package com.queuemate.platform.account.stats;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게임 전적 동기화 — {@code contracts/platform-api.md} "게임 프로필" 의 "전적을 긁는 것"(2026-09-23 소유자 결정)을
 * <b>가짜 Riot API</b>({@link FakeRiotApi})에 붙여서 본다. 진짜 Riot 을 부르지 않는다.
 *
 * <p>{@code @DynamicPropertySource} 가 설정을 바꾸므로 <b>이 클래스만 스프링 컨텍스트를 따로 띄운다</b>. 키가 없을 때는 기본 컨텍스트에서 본다
 * ({@link GameStatsNotConfiguredTest}).
 *
 * <p><b>긁는 것은 비동기다</b> — {@code Thread.sleep} 으로 고정 시간을 기다리지 않고, 전용 풀({@code gameStatsExecutor})이 비는 것을
 * 조건으로 기다린다({@link #awaitSyncIdle()}). 풀에 일을 넣는 것은 요청 스레드가 하므로(커밋 뒤) 응답을 받은 시점에는 이미 큐에 들어가 있다 —
 * 그래서 "부르지 않는다" 도 이것으로 확인할 수 있다.
 */
class GameStatsSyncTest extends ApiTestSupport {

    static final FakeRiotApi FAKE = new FakeRiotApi();

    @DynamicPropertySource
    static void riotProperties(DynamicPropertyRegistry registry)
    {
        registry.add("platform.riot.api-key", () -> FakeRiotApi.API_KEY);
        // 대륙 주소와 플랫폼 주소를 한 가짜 서버로 돌린다 — 경로가 겹치지 않는다
        registry.add("platform.riot.regional-base-url", FAKE::baseUrl);
        registry.add("platform.riot.platform-base-url", FAKE::baseUrl);
        registry.add("platform.riot.match-count", () -> 20);
        registry.add("platform.riot.freshness", () -> "PT30M");
        // 로컬 가짜 서버라 짧게 둔다 — 타임아웃을 보는 테스트가 오래 기다리지 않게
        registry.add("platform.riot.connect-timeout", () -> "PT1S");
        registry.add("platform.riot.read-timeout", () -> "PT1S");
    }

    @AfterAll
    static void stopFakeRiot()
    {
        FAKE.stop();
    }

    @Autowired
    @Qualifier(GameStatsAsyncConfig.EXECUTOR)
    Executor gameStatsExecutor;

    /**
     * 돌고 있는 갱신이 지워진 계정을 건드리지 않게 먼저 기다린다(하위 클래스의 {@code @AfterEach} 가 상위의 계정 삭제보다 먼저 돈다).
     * 그 다음 이 앱의 락 키만 지운다 — <b>{@code FLUSHDB} 금지</b>(같은 Redis 를 {@code room} 이 쓸 수 있다).
     */
    @AfterEach
    void settleAndCleanLocks()
    {
        try
        {
            awaitSyncIdle();
        }
        catch(AssertionError ignored)
        {
            // 기다려 주는 것이 목적이다 — 여기서 테스트를 깨지 않는다
        }
        Set<String> locks = redisTemplate.keys(GameStatsSyncLock.SYNC_LOCK_PREFIX + "*");
        if(!locks.isEmpty())
        {
            redisTemplate.delete(locks);
        }
        FAKE.reset();
    }

    @Test
    @DisplayName("게임 계정을 연결하면 LoL 전적이 채워진다 — 평균 · 연승 · 모스트 챔피언 · 솔로랭크의 승/패, external_id 에 puuid")
    void syncOnLink() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        String puuid = stubLol("달콤한 인생#KR7", "sum-1", 18, 28, List.of(
                // 새 경기가 먼저다 — 연승은 2(이김 · 이김 · 짐)
                play("Samira", 10, 2, 5, true),
                play("Samira", 8, 4, 3, true),
                play("Samira", 4, 6, 2, false),
                play("Ahri", 6, 3, 9, true),
                play("Ahri", 2, 7, 4, false),
                play("Yasuo", 3, 5, 1, false),
                play("LeeSin", 5, 5, 5, true)));

        putGameAccount(cookie, "LOL", json("gameNickname", "달콤한 인생#KR7", "tier", "EMERALD_4", "mainPosition", "MID"))
                .andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(loginId), "LOL");
        awaitStats(gameAccountId);

        JsonNode stats = profile(cookie, "LOL").get("stats");
        assertThat(stats.get("games").asInt()).isEqualTo(7);
        // 승/패는 경기 7개가 아니라 솔로랭크 줄에서 온다(시즌 누적). 자유랭크의 99승 99패를 섞지 않는다
        assertThat(stats.get("wins").asInt()).isEqualTo(18);
        assertThat(stats.get("losses").asInt()).isEqualTo(28);
        assertThat(stats.get("winRate").asInt()).isEqualTo(39);
        assertThat(stats.get("winStreak").asInt()).isEqualTo(2);
        assertThat(number(stats, "avgKills")).isEqualTo(5.4);
        assertThat(number(stats, "avgDeaths")).isEqualTo(4.6);
        assertThat(number(stats, "avgAssists")).isEqualTo(4.1);

        // 판 수 많은 순 → 같으면 승률 높은 순(Yasuo 0% 보다 LeeSin 100% 가 먼저) → 셋까지
        JsonNode champions = stats.get("detail").get("mostChampions");
        assertThat(champions).hasSize(3);
        assertThat(champions.get(0).get("championId").asString()).isEqualTo("Samira");
        assertThat(champions.get(0).get("games").asInt()).isEqualTo(3);
        assertThat(champions.get(0).get("winRate").asInt()).isEqualTo(67);
        assertThat(champions.get(1).get("championId").asString()).isEqualTo("Ahri");
        assertThat(champions.get(1).get("winRate").asInt()).isEqualTo(50);
        assertThat(champions.get(2).get("championId").asString()).isEqualTo("LeeSin");
        assertThat(champions.get(2).get("winRate").asInt()).isEqualTo(100);

        Map<String, Object> row = statsRow(gameAccountId);
        assertThat(row.get("source")).isEqualTo("API");
        // puuid 는 external_id 에 적히고 밖으로 나가지 않는다. verified 는 그대로다 — 켜는 길은 아직 없다
        assertThat(gameAccountColumn(gameAccountId, "external_id")).isEqualTo(puuid);
        assertThat(gameAccountColumn(gameAccountId, "verified")).isEqualTo(false);
        assertThat(profile(cookie, "LOL").get("verified").asBoolean()).isFalse();
        // 끝나면 락을 푼다
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).isFalse();
    }

    @Test
    @DisplayName("솔로랭크 줄이 없으면 wins · losses · winRate 가 전부 null 이다 — 경기의 평균은 그대로 채운다")
    void noSoloRank() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        String puuid = "puuid-" + newTag();
        FAKE.stubAccount("언랭#KR1", puuid);
        FAKE.stubSummoner(puuid, "sum-unranked");
        FAKE.stubNoSoloRank("sum-unranked");
        FAKE.stubMatches(puuid, List.of(play("Ahri", 3, 3, 3, true)));

        putGameAccount(cookie, "LOL", json("gameNickname", "언랭#KR1")).andExpect(status().isOk());
        awaitStats(gameAccountId(userIdOf(loginId), "LOL"));

        JsonNode stats = profile(cookie, "LOL").get("stats");
        assertThat(stats.get("games").asInt()).isEqualTo(1);
        assertThat(stats.get("wins").isNull()).isTrue();
        assertThat(stats.get("losses").isNull()).isTrue();
        assertThat(stats.get("winRate").isNull()).isTrue();
        assertThat(number(stats, "avgKills")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("연승은 가장 최근 경기부터 센다 — 최근 경기가 패면 0 이다. 경기를 하나도 못 읽으면 games 0 · 평균과 연승이 null 이다")
    void winStreakAndNoMatches() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        String puuid = stubLol("연승#KR1", "sum-streak", 1, 1, List.of(
                play("Ahri", 1, 1, 1, false),
                play("Ahri", 1, 1, 1, true),
                play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "연승#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(loginId), "LOL");
        awaitStats(gameAccountId);
        assertThat(profile(cookie, "LOL").get("stats").get("winStreak").asInt()).isZero();

        // 경기가 하나도 없는 계정으로 바꿔 다시 긁는다(계정 연결은 신선도를 보지 않는다)
        FAKE.stubAccount("무경기#KR1", puuid);
        FAKE.stubMatches(puuid, List.of());
        putGameAccount(cookie, "LOL", json("gameNickname", "무경기#KR1")).andExpect(status().isOk());
        awaitSyncIdle();

        JsonNode stats = profile(cookie, "LOL").get("stats");
        assertThat(stats.get("games").asInt()).isZero();
        assertThat(stats.get("avgKills").isNull()).isTrue();
        assertThat(stats.get("avgDeaths").isNull()).isTrue();
        assertThat(stats.get("avgAssists").isNull()).isTrue();
        assertThat(stats.get("winStreak").isNull()).isTrue();
        assertThat(stats.get("kda").isNull()).isTrue();
        assertThat(stats.get("detail").get("mostChampions")).isEmpty();
    }

    @Test
    @DisplayName("글을 쓸 때 — 전적이 신선하면 Riot 을 아예 부르지 않고, 오래됐으면 부른다")
    void freshnessOnPostCreate() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        stubLol("글쓴이#KR1", "sum-post", 5, 5, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "글쓴이#KR1", "mainPosition", "MID")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(loginId), "LOL");
        awaitStats(gameAccountId);

        int callsBefore = FAKE.calls();
        Long postId = createdPostId(cookie);
        awaitSyncIdle();
        // 방금 긁은 전적이라 신선하다 — 요청이 한 번도 나가지 않는다
        assertThat(FAKE.calls()).isEqualTo(callsBefore);

        // synced_at 을 한 시간 전으로 돌리고 글을 다시 쓴다(모집 중인 글은 한 사람에 하나라 먼저 지운다)
        touchSyncedAt(gameAccountId, Instant.now().minus(Duration.ofHours(1)));
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isNoContent());
        createdPostId(cookie);
        awaitSyncIdle();

        assertThat(FAKE.calls()).isGreaterThan(callsBefore);
        assertThat(syncedAt(gameAccountId)).isAfter(Instant.now().minus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("Riot 이 500 · 429 를 주거나 응답이 없어도 본 요청은 성공하고 기존 전적이 지워지지 않는다")
    void riotFailuresKeepOldStats() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        stubLol("실패#KR1", "sum-fail", 3, 1, List.of(play("Ahri", 9, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "실패#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(loginId), "LOL");
        awaitStats(gameAccountId);
        Instant good = syncedAt(gameAccountId);

        for(int status : new int[]{500, 429})
        {
            FAKE.failWith(status);
            int before = FAKE.calls();
            putGameAccount(cookie, "LOL", json("gameNickname", "실패#KR1")).andExpect(status().isOk());
            awaitSyncIdle();
            assertThat(FAKE.calls()).as("status=" + status + " 일 때 Riot 을 부르기는 했다").isGreaterThan(before);
            assertThat(statsRow(gameAccountId)).as("status=" + status + " 일 때 전적 줄이 남아 있다").isNotNull();
            assertThat(syncedAt(gameAccountId)).as("status=" + status + " 일 때 옛 값 그대로다").isEqualTo(good);
            assertThat(profile(cookie, "LOL").get("stats").get("games").asInt()).isEqualTo(1);
        }

        // 응답이 없다(읽기 타임아웃 PT1S)
        FAKE.failWith(0);
        FAKE.respondAfter(Duration.ofSeconds(2));
        int before = FAKE.calls();
        putGameAccount(cookie, "LOL", json("gameNickname", "실패#KR1")).andExpect(status().isOk());
        awaitSyncIdle();
        FAKE.respondAfter(Duration.ZERO);
        assertThat(FAKE.calls()).isGreaterThan(before);
        assertThat(syncedAt(gameAccountId)).isEqualTo(good);
    }

    @Test
    @DisplayName("game_nickname 에 태그가 없으면 Riot 을 부르지 않는다 — 전적은 null 로 남는다")
    void nicknameWithoutTag() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        int before = FAKE.calls();

        putGameAccount(cookie, "LOL", json("gameNickname", "태그없음")).andExpect(status().isOk());
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(statsRow(gameAccountId(userIdOf(loginId), "LOL"))).isNull();
        assertThat(profile(cookie, "LOL").get("stats").isNull()).isTrue();
    }

    @Test
    @DisplayName("VALORANT · PUBG 는 긁지 않는다 — 구현이 없는 게임이라 조용히 끝낸다")
    void onlyLolIsFetched() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        int before = FAKE.calls();

        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#EU1", "mainPosition", "DUELIST"))
                .andExpect(status().isOk());
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken#KR", "server", "STEAM"))
                .andExpect(status().isOk());
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(statsRow(gameAccountId(userIdOf(loginId), "VALORANT"))).isNull();
        assertThat(statsRow(gameAccountId(userIdOf(loginId), "PUBG"))).isNull();
    }

    @Test
    @DisplayName("락 — 같은 게임 계정을 누가 이미 긁고 있으면 줄 서지 않고 건너뛴다")
    void lockSkipsConcurrentSync() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        stubLol("락#KR1", "sum-lock", 2, 2, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "락#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(loginId), "LOL");
        awaitStats(gameAccountId);
        Instant good = syncedAt(gameAccountId);

        // 다른 사이클이 잡고 있는 자물쇠 — room 이 아니라 이 앱의 키다(qm:riot:sync:*)
        redisTemplate.opsForValue().set(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId, "someone-else",
                Duration.ofSeconds(60));
        int before = FAKE.calls();

        putGameAccount(cookie, "LOL", json("gameNickname", "락#KR1")).andExpect(status().isOk());
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(syncedAt(gameAccountId)).isEqualTo(good);
        // 남의 자물쇠를 풀지 않는다
        assertThat(redisTemplate.opsForValue().get(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId))
                .isEqualTo("someone-else");
    }

    // ---- 바탕 ----

    /** 그 닉네임으로 계정 · 소환사 · 솔로랭크 · 경기를 한꺼번에 넣는다. 돌려주는 것은 {@code puuid} 다 */
    private String stubLol(String riotId, String summonerId, int wins, int losses, List<FakeRiotApi.Play> plays)
    {
        String puuid = "puuid-" + newTag();
        FAKE.stubAccount(riotId, puuid);
        FAKE.stubSummoner(puuid, summonerId);
        FAKE.stubSoloRank(summonerId, wins, losses);
        FAKE.stubMatches(puuid, plays);
        return puuid;
    }

    private static FakeRiotApi.Play play(String champion, int kills, int deaths, int assists, boolean win)
    {
        return new FakeRiotApi.Play(champion, kills, deaths, assists, win, "MIDDLE");
    }

    private static String newTag()
    {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions putGameAccount(Cookie cookie, String game, String body) throws Exception
    {
        return mockMvc.perform(put("/api/v1/users/me/game-accounts/" + game).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** LOL 글 하나를 쓰고 그 id 를 돌려준다 */
    private Long createdPostId(Cookie cookie) throws Exception
    {
        // mode 는 gameconfig 에 있는 이름이어야 한다(2026-09-24) — ApiTestSupport 가 심어 둔다
        String body = "{\"game\":\"LOL\",\"mode\":\"" + LOL_MODE + "\",\"title\":\"같이 하실 분\",\"description\":\"즐겁게\","
                + "\"voice\":\"REQUIRED\",\"purpose\":\"RANK_UP\",\"conditions\":{},\"wantedPositions\":[\"MID\"]}";
        ResultActions created = mockMvc.perform(post("/api/v1/posts").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        return readBody(created).get("postId").asLong();
    }

    /** {@code users/me} 의 그 게임 프로필. 없으면 {@code null} */
    private JsonNode profile(Cookie cookie, String game) throws Exception
    {
        JsonNode me = readBody(mockMvc.perform(get("/api/v1/users/me").cookie(cookie)).andExpect(status().isOk()));
        for(JsonNode account : me.get("gameAccounts"))
        {
            if(game.equals(account.get("game").asString()))
            {
                return account;
            }
        }
        return null;
    }

    private JsonNode readBody(ResultActions actions) throws Exception
    {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 소수 칸을 숫자로 — JSON 의 글자를 그대로 읽는다(5.4 가 5.4 인지 본다) */
    private static double number(JsonNode stats, String field)
    {
        return Double.parseDouble(stats.get(field).toString());
    }

    private Long gameAccountId(Long userId, String game)
    {
        return jdbcTemplate.queryForObject(
                "select id from account.game_accounts where user_id = ? and game = ?", Long.class, userId, game);
    }

    private Object gameAccountColumn(Long gameAccountId, String column)
    {
        return jdbcTemplate.queryForMap("select * from account.game_accounts where id = ?", gameAccountId).get(column);
    }

    /** 전적 줄. 없으면 {@code null} */
    private Map<String, Object> statsRow(Long gameAccountId)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from account.game_account_stats where game_account_id = ?", gameAccountId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Instant syncedAt(Long gameAccountId)
    {
        return ((Timestamp) statsRow(gameAccountId).get("synced_at")).toInstant();
    }

    private void touchSyncedAt(Long gameAccountId, Instant syncedAt)
    {
        jdbcTemplate.update("update account.game_account_stats set synced_at = ? where game_account_id = ?",
                Timestamp.from(syncedAt), gameAccountId);
    }

    /** 전적 줄이 생기기를 기다린다 — 긁는 것은 비동기다 */
    private void awaitStats(Long gameAccountId)
    {
        await(() -> statsRow(gameAccountId) != null, "전적 줄이 생기기를");
    }

    /**
     * 전용 풀이 비기를 기다린다 — 돌고 있는 것도 큐에 남은 것도 없을 때까지. 풀에 넣는 것은 요청 스레드가 하므로
     * 응답을 받은 뒤에 이것을 부르면 "부르지 않는다"도 확인할 수 있다.
     */
    private void awaitSyncIdle()
    {
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsExecutor;
        await(() -> pool.getActiveCount() == 0 && pool.getThreadPoolExecutor().getQueue().isEmpty(),
                "전적 동기화가 끝나기를");
    }

    private static void await(BooleanSupplier condition, String what)
    {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while(System.nanoTime() < deadline)
        {
            if(condition.getAsBoolean())
            {
                return;
            }
            try
            {
                Thread.sleep(50);
            }
            catch(InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(what + " 기다렸지만 끝나지 않았다");
    }
}
