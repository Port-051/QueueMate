package com.queuemate.platform.account.stats;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게임 전적 동기화 — {@code contracts/platform-api.md} "게임 프로필" 의 "전적을 긁는 것"(2026-09-23 소유자 결정)을
 * <b>가짜 Riot API</b>({@link FakeRiotApi})에 붙여서 본다. 진짜 Riot 을 부르지 않는다.
 *
 * <p><b>긁는 시점은 둘이다</b>(2026-09-24 소유자 결정) — ① 게임 계정을 저장할 때(비동기) ② <b>사용자가 전적 갱신을 누를 때</b>
 * ({@code POST …/game-accounts/{game}/refresh} — 동기이고 쿨타임 2분이 있다. 아래 "전적 갱신" 묶음).
 * <b>모집 글을 쓸 때 긁던 것</b>과 신선도 장치는 같은 날 없어졌다 — 되살아나지 않게 {@link #noSyncOnPostCreate()} 가 지킨다.
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
     * 그 다음 이 앱의 락 키와 <b>전적 갱신 쿨타임 키</b>만 지운다(쿨타임은 2분이라 그냥 두면 다음 테스트가 429 를 받는다) —
     * <b>{@code FLUSHDB} 금지</b>(같은 Redis 를 {@code room} 이 쓸 수 있다).
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
        deleteKeys(GameStatsSyncLock.SYNC_LOCK_PREFIX + "*");
        deleteKeys(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + "*");
        FAKE.reset();
    }

    private void deleteKeys(String pattern)
    {
        Set<String> keys = redisTemplate.keys(pattern);
        if(!keys.isEmpty())
        {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("게임 계정을 연결하면 LoL 전적이 채워진다 — 평균 · 연승 · 모스트 챔피언 · 솔로랭크의 승/패, external_id 에 puuid")
    void syncOnLink() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
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
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
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
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String puuid = "puuid-" + newTag();
        FAKE.stubAccount("언랭#KR1", puuid);
        FAKE.stubSummoner(puuid, "sum-unranked");
        FAKE.stubNoSoloRank("sum-unranked");
        FAKE.stubMatches(puuid, List.of(play("Ahri", 3, 3, 3, true)));

        putGameAccount(cookie, "LOL", json("gameNickname", "언랭#KR1")).andExpect(status().isOk());
        awaitStats(gameAccountId(userIdOf(nickname), "LOL"));

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
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String puuid = stubLol("연승#KR1", "sum-streak", 1, 1, List.of(
                play("Ahri", 1, 1, 1, false),
                play("Ahri", 1, 1, 1, true),
                play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "연승#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        awaitStats(gameAccountId);
        assertThat(profile(cookie, "LOL").get("stats").get("winStreak").asInt()).isZero();

        // 경기가 하나도 없는 계정으로 바꿔 다시 긁는다(계정 연결은 무조건 긁는다)
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
    @DisplayName("글을 써도 전적을 긁지 않는다 — 전적이 아무리 오래됐어도 Riot 을 한 번도 부르지 않는다 (2026-09-24 소유자 결정)")
    void noSyncOnPostCreate() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("글쓴이#KR1", "sum-post", 5, 5, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "글쓴이#KR1", "mainPosition", "MID")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        awaitStats(gameAccountId);

        // 전적을 한 시간 전으로 돌린다 — "신선해서 건너뛴 것"이 아니라 그 길 자체가 없다는 것을 보려는 것이다
        touchSyncedAt(gameAccountId, Instant.now().minus(Duration.ofHours(1)));
        int callsBefore = FAKE.calls();

        createdPostId(cookie);
        awaitSyncIdle();

        assertThat(FAKE.calls()).as("글 쓰기가 Riot 을 부르지 않는다").isEqualTo(callsBefore);
        assertThat(syncedAt(gameAccountId)).as("전적을 다시 적지도 않는다")
                .isBefore(Instant.now().minus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("Riot 이 500 · 429 를 주거나 응답이 없어도 본 요청은 성공하고 기존 전적이 지워지지 않는다")
    void riotFailuresKeepOldStats() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("실패#KR1", "sum-fail", 3, 1, List.of(play("Ahri", 9, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "실패#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
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
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        int before = FAKE.calls();

        putGameAccount(cookie, "LOL", json("gameNickname", "태그없음")).andExpect(status().isOk());
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "LOL"))).isNull();
        assertThat(profile(cookie, "LOL").get("stats").isNull()).isTrue();
    }

    @Test
    @DisplayName("VALORANT · PUBG 는 긁지 않는다 — 구현이 없는 게임이라 조용히 끝낸다")
    void onlyLolIsFetched() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        int before = FAKE.calls();

        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#EU1", "mainPosition", "DUELIST"))
                .andExpect(status().isOk());
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken#KR", "server", "STEAM"))
                .andExpect(status().isOk());
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "VALORANT"))).isNull();
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "PUBG"))).isNull();
    }

    @Test
    @DisplayName("락 — 같은 게임 계정을 누가 이미 긁고 있으면 줄 서지 않고 건너뛴다")
    void lockSkipsConcurrentSync() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("락#KR1", "sum-lock", 2, 2, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "락#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
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

    // ---- 전적 갱신 (POST …/game-accounts/{game}/refresh — 2026-09-24 소유자 결정) ----

    @Test
    @DisplayName("전적 갱신을 누르면 200 이고 그 자리에서 최신 전적을 준다 — 응답이 PUT 과 같은 게임 프로필이다")
    void refreshReturnsFreshStats() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String puuid = stubLol("갱신#KR1", "sum-refresh", 10, 10, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "갱신#KR1", "mainPosition", "MID")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        awaitStats(gameAccountId);
        Instant before = syncedAt(gameAccountId);
        int callsBefore = FAKE.calls();

        // 그 사이에 두 판 더 이겼다 — 갱신이 진짜로 다시 긁는지 보려는 것이다(games 1 → 3, 연승 1 → 3)
        FAKE.stubMatches(puuid, List.of(
                play("Samira", 9, 1, 3, true),
                play("Samira", 7, 2, 4, true),
                play("Ahri", 1, 1, 1, true)));

        // 동기다 — 응답이 오면 이미 긁혀 있다(awaitStats 로 기다리지 않는다)
        refresh(cookie, "LOL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.gameNickname").value("갱신#KR1"))
                .andExpect(jsonPath("$.stats.games").value(3))
                .andExpect(jsonPath("$.stats.winStreak").value(3));

        assertThat(FAKE.calls()).as("Riot 을 다시 불렀다").isGreaterThan(callsBefore);
        assertThat(syncedAt(gameAccountId)).as("synced_at 이 새로워졌다").isAfterOrEqualTo(before);
        assertThat(profile(cookie, "LOL").get("stats").get("games").asInt()).isEqualTo(3);
        // 끝나면 자물쇠를 푼다. 쿨타임 키는 남는다 — 그것이 2분 동안 다음 갱신을 막는다
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).isFalse();
        assertThat(redisTemplate.hasKey(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + gameAccountId)).isTrue();
    }

    @Test
    @DisplayName("곧바로 또 갱신하면 429 TOO_MANY_STATS_REFRESHES + Retry-After 이고 Riot 을 부르지 않는다")
    void refreshTwiceHitsCooldown() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("쿨타임#KR1", "sum-cool", 3, 3, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "쿨타임#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        awaitStats(gameAccountId);

        refresh(cookie, "LOL").andExpect(status().isOk());
        Instant refreshed = syncedAt(gameAccountId);
        int callsBefore = FAKE.calls();

        MvcResult rejected = refresh(cookie, "LOL")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_STATS_REFRESHES"))
                .andReturn();

        long retryAfter = Long.parseLong(rejected.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isBetween(1L, 120L);
        assertThat(FAKE.calls()).as("거절은 Riot 을 부르지 않는다").isEqualTo(callsBefore);
        assertThat(syncedAt(gameAccountId)).isEqualTo(refreshed);
    }

    @Test
    @DisplayName("로그인하지 않으면 401 이다 — 경로가 me 라서 남의 게임 계정을 가리킬 길이 없다")
    void refreshRequiresLogin() throws Exception
    {
        int callsBefore = FAKE.calls();

        mockMvc.perform(post("/api/v1/users/me/game-accounts/LOL/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertThat(FAKE.calls()).isEqualTo(callsBefore);
    }

    @Test
    @DisplayName("연결한 게임 계정이 없으면 404 GAME_ACCOUNT_NOT_FOUND 다 — 쿨타임을 소모하지 않는다")
    void refreshWithoutGameAccount() throws Exception
    {
        Cookie cookie = login(newNickname());
        int callsBefore = FAKE.calls();

        refresh(cookie, "LOL")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_ACCOUNT_NOT_FOUND"));

        assertThat(FAKE.calls()).isEqualTo(callsBefore);
        assertThat(redisTemplate.keys(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + "*")).isEmpty();
    }

    @Test
    @DisplayName("긁는 구현이 없는 게임(VALORANT · PUBG)은 409 GAME_STATS_NOT_SUPPORTED 다 — 200 을 주면 거짓말이고, 쿨타임도 소모하지 않는다")
    void refreshUnsupportedGame() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#EU1", "mainPosition", "DUELIST"))
                .andExpect(status().isOk());
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken#KR", "server", "STEAM")).andExpect(status().isOk());
        awaitSyncIdle();
        int callsBefore = FAKE.calls();

        for(String game : new String[]{"VALORANT", "PUBG"})
        {
            refresh(cookie, game)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GAME_STATS_NOT_SUPPORTED"));
        }

        assertThat(FAKE.calls()).isEqualTo(callsBefore);
        assertThat(redisTemplate.keys(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + "*")).isEmpty();
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "VALORANT"))).isNull();
    }

    @Test
    @DisplayName("Riot 이 실패하면 503 GAME_STATS_UNAVAILABLE 이고 전적 줄은 그대로다 — 쿨타임은 소모된다")
    void refreshRiotFailureKeepsOldStats() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("갱신실패#KR1", "sum-refresh-fail", 4, 6, List.of(play("Ahri", 2, 2, 2, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "갱신실패#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        awaitStats(gameAccountId);
        Instant good = syncedAt(gameAccountId);

        FAKE.failWith(500);
        refresh(cookie, "LOL")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
        FAKE.failWith(0);

        assertThat(statsRow(gameAccountId)).as("전적 줄이 남아 있다").isNotNull();
        assertThat(syncedAt(gameAccountId)).as("옛 값 그대로다").isEqualTo(good);
        assertThat(profile(cookie, "LOL").get("stats").get("games").asInt()).isEqualTo(1);
        // 실패해도 쿨타임은 소모된다 — 실패만 무제한으로 다시 할 수 있으면 Riot 한도를 그대로 태운다
        assertThat(redisTemplate.hasKey(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + gameAccountId)).isTrue();
    }

    @Test
    @DisplayName("누가 이미 같은 계정을 긁고 있으면 갱신도 429 다 — 자물쇠를 그대로 쓴다")
    void refreshWhileAlreadySyncing() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("자물쇠#KR1", "sum-refresh-lock", 1, 2, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "자물쇠#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        awaitStats(gameAccountId);
        Instant good = syncedAt(gameAccountId);

        // 다른 사이클이 잡고 있는 자물쇠
        redisTemplate.opsForValue().set(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId, "someone-else",
                Duration.ofSeconds(60));
        int callsBefore = FAKE.calls();

        refresh(cookie, "LOL")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_STATS_REFRESHES"));

        assertThat(FAKE.calls()).isEqualTo(callsBefore);
        assertThat(syncedAt(gameAccountId)).isEqualTo(good);
        // 남의 자물쇠를 풀지 않는다
        assertThat(redisTemplate.opsForValue().get(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId))
                .isEqualTo("someone-else");
    }

    // ---- 바탕 ----

    private ResultActions refresh(Cookie cookie, String game) throws Exception
    {
        return mockMvc.perform(post("/api/v1/users/me/game-accounts/" + game + "/refresh").cookie(cookie));
    }

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
                "select id from game_accounts where user_id = ? and game = ?", Long.class, userId, game);
    }

    private Object gameAccountColumn(Long gameAccountId, String column)
    {
        return jdbcTemplate.queryForMap("select * from game_accounts where id = ?", gameAccountId).get(column);
    }

    /** 전적 줄. 없으면 {@code null} */
    private Map<String, Object> statsRow(Long gameAccountId)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from game_account_stats where game_account_id = ?", gameAccountId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Instant syncedAt(Long gameAccountId)
    {
        return ((Timestamp) statsRow(gameAccountId).get("synced_at")).toInstant();
    }

    private void touchSyncedAt(Long gameAccountId, Instant syncedAt)
    {
        jdbcTemplate.update("update game_account_stats set synced_at = ? where game_account_id = ?",
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
