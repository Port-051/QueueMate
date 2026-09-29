package com.queuemate.platform.account.stats;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
 * <p><b>긁는 시점은 둘이고 둘 다 동기다</b> — ① <b>LoL 게임 계정 연결</b>({@code PUT …/game-accounts/LOL} — 2026-09-27 소유자 결정.
 * 본문은 이름#태그 하나이고 저장하기 전에 긁어 티어 · 전적을 채운다. 실패하면 저장하지 않는다. 주 포지션은 없다 — 2026-09-29, P-35) ② <b>사용자가 전적 갱신을 누를 때</b>
 * ({@code POST …/game-accounts/{game}/refresh} — 쿨타임 2분이 있다. 아래 "전적 갱신" 묶음).
 * <b>모집 글을 쓸 때 긁던 것</b>과 신선도 장치는 2026-09-24 에 없어졌다 — 되살아나지 않게 {@link #noSyncOnPostCreate()} 가 지킨다.
 *
 * <p>{@code @DynamicPropertySource} 가 설정을 바꾸므로 <b>이 클래스만 스프링 컨텍스트를 따로 띄운다</b>. 키가 없을 때는 기본 컨텍스트에서 본다
 * ({@link GameStatsNotConfiguredTest}).
 *
 * <p>응답이 오면 이미 적혀 있다 — 기다리지 않는다. 다만 상한(30초 — 여기서는 3초)을 넘긴 긁기는 뒤에서 계속 돌므로
 * "늦게 끝난 것을 저장하지 않는다"는 전용 풀이 비기를 기다려서 본다({@link #awaitSyncIdle()}).
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
        // 기다리는 상한(기본 30초)도 줄인다 — 상한을 넘긴 연결이 저장하지 않는지 보는 테스트가 오래 걸리지 않게
        registry.add("platform.riot.refresh-timeout", () -> "PT3S");
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
    @DisplayName("LoL 게임 계정을 연결하면 응답에 티어 · 전적이 바로 들어 있다 — 평균 · 연승 · 모스트 챔피언 · 솔로랭크의 승/패, external_id 에 puuid")
    void linkFillsFromRiot() throws Exception
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
        // 숙련도 — LeeSin 은 넣지 않는다(숙련도 목록에 없는 챔피언은 null)
        FAKE.stubMastery(puuid, Map.of("Samira", new int[]{7, 123_456}, "Ahri", new int[]{45, 1_234_567}));
        int callsBefore = FAKE.calls();

        // 동기다 — 응답이 오면 이미 긁혀 적혀 있다
        JsonNode linked = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "달콤한 인생#KR7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.gameNickname").value("달콤한 인생#KR7"))
                // 사다리가 둘이다(2026-09-29 — P-36) — 솔로랭크 줄의 EMERALD + IV → SOLO, 자유랭크 줄의 GOLD + II → FLEX. tier 칸은 없다
                .andExpect(jsonPath("$.tiers.SOLO").value("EMERALD_4"))
                .andExpect(jsonPath("$.tiers.FLEX").value("GOLD_2"))
                .andExpect(jsonPath("$.tier").doesNotExist())
                // 주 포지션 칸은 없다 — 경기는 전부 미드(teamPosition MIDDLE)였지만 Riot 에서 뽑지 않고, 게임 계정에 그 칸이 없다(2026-09-29 — P-35)
                .andExpect(jsonPath("$.mainPosition").doesNotExist())
                .andExpect(jsonPath("$.server").isEmpty())
                .andExpect(jsonPath("$.verified").value(false)));
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");

        JsonNode stats = linked.get("stats");
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
        // 숙련도는 챔피언 번호로 맞춘다(엉뚱한 Teemo 줄을 고르지 않는다). 목록에 없는 LeeSin 은 둘 다 null
        assertThat(champions.get(0).get("masteryLevel").asInt()).isEqualTo(7);
        assertThat(champions.get(0).get("masteryPoints").asInt()).isEqualTo(123_456);
        assertThat(champions.get(1).get("masteryLevel").asInt()).isEqualTo(45);
        assertThat(champions.get(1).get("masteryPoints").asInt()).isEqualTo(1_234_567);
        assertThat(champions.get(2).get("masteryLevel").isNull()).isTrue();
        assertThat(champions.get(2).get("masteryPoints").isNull()).isTrue();
        // 계정 · 소환사 · 리그 · 경기 id · 경기 7 · 숙련도 1 — 숙련도는 챔피언마다 부르지 않는다
        assertThat(FAKE.calls() - callsBefore).isEqualTo(12);

        // users/me 도 같은 값이다
        JsonNode profile = profile(cookie, "LOL");
        assertThat(profile.get("tiers").get("SOLO").asString()).isEqualTo("EMERALD_4");
        assertThat(profile.get("tiers").get("FLEX").asString()).isEqualTo("GOLD_2");
        assertThat(profile.has("mainPosition")).isFalse();
        // DB 에는 jsonb 한 칸에 사다리 둘이다
        assertThat(jdbcTemplate.queryForObject("select tiers ->> 'FLEX' from game_accounts where id = ?", String.class,
                gameAccountId)).isEqualTo("GOLD_2");
        assertThat(profile.get("stats").get("games").asInt()).isEqualTo(7);

        Map<String, Object> row = statsRow(gameAccountId);
        assertThat(row.get("source")).isEqualTo("API");
        // puuid 는 external_id 에 적히고 밖으로 나가지 않는다. verified 는 그대로다 — 켜는 길은 아직 없다
        assertThat(gameAccountColumn(gameAccountId, "external_id")).isEqualTo(puuid);
        assertThat(gameAccountColumn(gameAccountId, "verified")).isEqualTo(false);
        assertThat(linked.has("externalId")).isFalse();
        // 연결은 쿨타임을 찍지 않는다(쿨타임은 전적 갱신 요청의 것이다). 락은 새 계정이라 잡지 않았다
        assertThat(redisTemplate.hasKey(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + gameAccountId)).isFalse();
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).isFalse();
    }

    @Test
    @DisplayName("다시 연결하면 이름 · 티어 · 전적이 새 Riot ID 의 것으로 바뀐다 — 한 줄이고, 쿨타임 없이 곧바로 또 할 수 있다")
    void relinkReplaces() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("처음#KR1", "sum-first", 1, 1, List.of(play("Ahri", 1, 1, 1, true, "TOP")));
        String second = "puuid-" + newTag();
        FAKE.stubAccount("다음#KR2", second);
        FAKE.stubSummoner(second, "sum-second");
        FAKE.stubSoloRank("sum-second", "GOLD", "II", 5, 5);
        FAKE.stubMatches(second, List.of(play("Lulu", 1, 1, 1, true, "UTILITY")));

        putGameAccount(cookie, "LOL", json("gameNickname", "처음#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.SOLO").value("EMERALD_4"));
        putGameAccount(cookie, "LOL", json("gameNickname", "다음#KR2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameNickname").value("다음#KR2"))
                .andExpect(jsonPath("$.tiers.SOLO").value("GOLD_2"))
                .andExpect(jsonPath("$.stats.wins").value(5));

        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        assertThat(gameAccountColumn(gameAccountId, "external_id")).isEqualTo(second);
        assertThat(jdbcTemplate.queryForObject("select count(*) from game_accounts where user_id = ?",
                Integer.class, userIdOf(nickname))).isEqualTo(1);
        // 다시 연결할 때는 락을 잡았다가 푼다
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).isFalse();
    }

    @Test
    @DisplayName("LoL 도 주 포지션을 받지 않는다 — mainPosition 을 보내면 400 이고, Riot 을 부르지 않으며 아무것도 저장 · 변경하지 않는다 (2026-09-29 — P-35)")
    void lolRejectsMainPosition() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("포지션#KR1", "sum-position", 3, 3, List.of(
                play("Lulu", 1, 1, 1, true, "UTILITY"),
                play("Nami", 1, 1, 1, true, "UTILITY")));
        int before = FAKE.calls();

        // 처음 연결 — Riot 을 부르기 전에 거르고 저장하지 않는다
        putGameAccount(cookie, "LOL", json("gameNickname", "포지션#KR1", "mainPosition", "MID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("mainPosition"));
        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();

        // 연결된 뒤 — 다시 연결도 통째로 거절되고 이름이 그대로다
        putGameAccount(cookie, "LOL", json("gameNickname", "포지션#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mainPosition").doesNotExist());
        before = FAKE.calls();
        putGameAccount(cookie, "LOL", json("gameNickname", "딴이름#KR9", "mainPosition", "SUPPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("mainPosition"));
        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(profile(cookie, "LOL").get("gameNickname").asString()).isEqualTo("포지션#KR1");
    }

    @Test
    @DisplayName("Riot 의 티어 이름을 gameconfig 사다리 이름으로 옮긴다 — GOLD+II→GOLD_2, MASTER+I→MASTER")
    void ladderNames()
    {
        assertThat(LolStatsProvider.ladderName("GOLD", "II")).isEqualTo("GOLD_2");
        assertThat(LolStatsProvider.ladderName("IRON", "IV")).isEqualTo("IRON_4");
        assertThat(LolStatsProvider.ladderName("DIAMOND", "I")).isEqualTo("DIAMOND_1");
        assertThat(LolStatsProvider.ladderName("EMERALD", "III")).isEqualTo("EMERALD_3");
        // 단이 없는 셋 — Riot 은 rank "I" 를 주지만 사다리 이름에는 붙지 않는다
        assertThat(LolStatsProvider.ladderName("MASTER", "I")).isEqualTo("MASTER");
        assertThat(LolStatsProvider.ladderName("GRANDMASTER", "I")).isEqualTo("GRANDMASTER");
        assertThat(LolStatsProvider.ladderName("CHALLENGER", "I")).isEqualTo("CHALLENGER");
        // 모르는 단 · 티어 없음
        assertThat(LolStatsProvider.ladderName("GOLD", "V")).isNull();
        assertThat(LolStatsProvider.ladderName("GOLD", null)).isNull();
        assertThat(LolStatsProvider.ladderName(null, "I")).isNull();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("Riot 이 준 티어가 gameconfig 사다리에 없으면 그 사다리(SOLO)는 null 이고 WARN 한 줄을 남긴다 — 다른 사다리 · 전적은 그대로 채운다")
    void tierNotOnLadder(CapturedOutput output) throws Exception
    {
        Cookie cookie = login(newNickname());
        String puuid = "puuid-" + newTag();
        FAKE.stubAccount("새티어#KR1", puuid);
        FAKE.stubSummoner(puuid, "sum-obsidian");
        FAKE.stubSoloRank("sum-obsidian", "OBSIDIAN", "II", 7, 3);
        FAKE.stubMatches(puuid, List.of(play("Ahri", 1, 1, 1, true)));

        putGameAccount(cookie, "LOL", json("gameNickname", "새티어#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.SOLO").isEmpty())
                .andExpect(jsonPath("$.tiers.FLEX").value("GOLD_2"))
                .andExpect(jsonPath("$.stats.wins").value(7));
        assertThat(output.getAll()).contains("사다리에 없다 tier=OBSIDIAN_2");
    }

    @Test
    @DisplayName("솔로랭크 줄이 없으면(언랭) SOLO 와 wins · losses · winRate 가 전부 null 이다 — 자유랭크 줄의 FLEX 와 경기의 평균은 그대로 채운다")
    void noSoloRank() throws Exception
    {
        Cookie cookie = login(newNickname());
        String puuid = "puuid-" + newTag();
        FAKE.stubAccount("언랭#KR1", puuid);
        FAKE.stubSummoner(puuid, "sum-unranked");
        FAKE.stubNoSoloRank("sum-unranked");
        FAKE.stubMatches(puuid, List.of(play("Ahri", 3, 3, 3, true)));

        JsonNode linked = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "언랭#KR1"))
                .andExpect(status().isOk())
                // 자유랭크 줄(GOLD II)의 티어는 FLEX 사다리다 — 솔로랭크 사다리로 옮기지 않는다(2026-09-29 — P-36)
                .andExpect(jsonPath("$.tiers.SOLO").isEmpty())
                .andExpect(jsonPath("$.tiers.FLEX").value("GOLD_2")));

        JsonNode stats = linked.get("stats");
        assertThat(stats.get("games").asInt()).isEqualTo(1);
        assertThat(stats.get("wins").isNull()).isTrue();
        assertThat(stats.get("losses").isNull()).isTrue();
        assertThat(stats.get("winRate").isNull()).isTrue();
        assertThat(number(stats, "avgKills")).isEqualTo(3.0);
    }

    @Test
    @DisplayName("연승은 가장 최근 경기부터 센다 — 최근 경기가 패면 0 이다. 경기를 하나도 못 읽으면 games 0 · 평균 · 연승이 null 이다")
    void winStreakAndNoMatches() throws Exception
    {
        Cookie cookie = login(newNickname());
        String puuid = stubLol("연승#KR1", "sum-streak", 1, 1, List.of(
                play("Ahri", 1, 1, 1, false),
                play("Ahri", 1, 1, 1, true),
                play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "연승#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.winStreak").value(0));

        // 경기가 하나도 없는 계정으로 바꿔 다시 연결한다(연결은 무조건 긁는다)
        FAKE.stubAccount("무경기#KR1", puuid);
        FAKE.stubMatches(puuid, List.of());
        JsonNode stats = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "무경기#KR1"))
                .andExpect(status().isOk())).get("stats");

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
        putGameAccount(cookie, "LOL", json("gameNickname", "글쓴이#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");

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
    @DisplayName("이름#태그가 Riot 에 없으면 404 RIOT_ID_NOT_FOUND 이고 저장하지 않는다")
    void unknownRiotId() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        int before = FAKE.calls();

        putGameAccount(cookie, "LOL", json("gameNickname", "없는사람#KR1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RIOT_ID_NOT_FOUND"));

        // account-v1 한 번으로 끝난다
        assertThat(FAKE.calls()).isEqualTo(before + 1);
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    @Test
    @DisplayName("숙련도 호출만 500 이면 연결은 200 이고 나머지 전적은 정상 · 숙련도만 null 이다 — 경기 20판이면 Riot 호출은 25번")
    @ExtendWith(OutputCaptureExtension.class)
    void masteryFailureKeepsStats(CapturedOutput output) throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        List<FakeRiotApi.Play> twenty = new java.util.ArrayList<>();
        for(int i = 0; i < 20; i++)
        {
            twenty.add(play(i < 12 ? "Ahri" : "Yasuo", 4, 2, 6, i % 2 == 0));
        }
        String puuid = stubLol("숙련도#KR1", "sum-mastery", 30, 20, twenty);
        FAKE.stubMastery(puuid, Map.of("Ahri", new int[]{12, 99_999}));
        FAKE.failMasteryWith(500);
        int callsBefore = FAKE.calls();

        JsonNode stats = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "숙련도#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.SOLO").value("EMERALD_4"))).get("stats");

        // 계정 · 소환사 · 리그 · 경기 id · 경기 20 · 숙련도 1
        assertThat(FAKE.calls() - callsBefore).isEqualTo(25);
        assertThat(stats.get("games").asInt()).isEqualTo(20);
        assertThat(stats.get("wins").asInt()).isEqualTo(30);
        assertThat(stats.get("losses").asInt()).isEqualTo(20);
        JsonNode champions = stats.get("detail").get("mostChampions");
        assertThat(champions).hasSize(2);
        assertThat(champions.get(0).get("championId").asString()).isEqualTo("Ahri");
        assertThat(champions.get(0).get("games").asInt()).isEqualTo(12);
        for(JsonNode champion : champions)
        {
            assertThat(champion.get("masteryLevel").isNull()).isTrue();
            assertThat(champion.get("masteryPoints").isNull()).isTrue();
        }
        assertThat(output).contains("챔피언 숙련도를 받지 못했다");
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "LOL"))).isNotNull();
    }

    @Test
    @DisplayName("Riot 이 500 · 429 를 주거나 응답이 없으면 503 GAME_STATS_UNAVAILABLE 이고 game_accounts 에 줄이 생기지 않는다")
    void riotFailuresSaveNothing() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("실패#KR1", "sum-fail", 3, 1, List.of(play("Ahri", 9, 1, 1, true)));

        for(int status : new int[]{500, 429})
        {
            FAKE.failWith(status);
            int before = FAKE.calls();
            putGameAccount(cookie, "LOL", json("gameNickname", "실패#KR1"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
            assertThat(FAKE.calls()).as("status=" + status + " 일 때 Riot 을 부르기는 했다").isGreaterThan(before);
            assertThat(gameAccountCount(userIdOf(nickname))).as("status=" + status + " 일 때 저장하지 않았다").isZero();
        }

        // 응답이 없다(읽기 타임아웃 PT1S)
        FAKE.failWith(0);
        FAKE.respondAfter(Duration.ofSeconds(2));
        putGameAccount(cookie, "LOL", json("gameNickname", "실패#KR1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
        FAKE.respondAfter(Duration.ZERO);
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    @Test
    @DisplayName("이미 연결한 계정을 다시 연결하다 Riot 이 실패하면 503 이고 옛 이름 · 티어 · 전적이 그대로다")
    void riotFailureKeepsExistingAccount() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("그대로#KR1", "sum-keep", 3, 1, List.of(play("Ahri", 9, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "그대로#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant good = syncedAt(gameAccountId);

        FAKE.failWith(500);
        putGameAccount(cookie, "LOL", json("gameNickname", "딴이름#KR9"))
                .andExpect(status().isServiceUnavailable());
        FAKE.failWith(0);

        JsonNode profile = profile(cookie, "LOL");
        assertThat(profile.get("gameNickname").asString()).isEqualTo("그대로#KR1");
        assertThat(profile.get("tiers").get("SOLO").asString()).isEqualTo("EMERALD_4");
        assertThat(syncedAt(gameAccountId)).isEqualTo(good);
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).as("락을 풀었다").isFalse();
    }

    @Test
    @DisplayName("상한(여기서는 3초)을 넘기면 503 이고, 뒤에서 늦게 끝난 긁기는 저장하지 않는다")
    void timeoutDiscardsLateResult() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        // 부르는 것이 8번(계정 · 소환사 · 리그 · 경기 id · 경기 3 · 숙련도) — 한 번에 0.7초면 상한 3초를 넘긴다(읽기 타임아웃 1초는 안 넘는다)
        stubLol("느림#KR1", "sum-slow", 1, 1, List.of(
                play("Ahri", 1, 1, 1, true), play("Ahri", 1, 1, 1, true), play("Ahri", 1, 1, 1, true)));
        FAKE.respondAfter(Duration.ofMillis(700));
        int before = FAKE.calls();

        putGameAccount(cookie, "LOL", json("gameNickname", "느림#KR1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));

        // 뒤에서 돌던 긁기가 끝나기를 기다린다 — 끝나도 아무것도 적히지 않는다
        awaitSyncIdle();
        FAKE.respondAfter(Duration.ZERO);
        assertThat(FAKE.calls()).as("뒤에서 끝까지 긁기는 했다").isEqualTo(before + 8);
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    @Test
    @DisplayName("VALORANT · PUBG 는 긁지 않는다 — 자기신고를 그대로 적고 곧바로 답한다")
    void onlyLolIsFetched() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        int before = FAKE.calls();

        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#EU1", "tier", "DIAMOND_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.COMPETITIVE").value("DIAMOND_2"))
                .andExpect(jsonPath("$.stats").isEmpty());
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken#KR", "server", "STEAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("STEAM"));
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "VALORANT"))).isNull();
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "PUBG"))).isNull();
    }

    @Test
    @DisplayName("락 — 같은 게임 계정을 누가 이미 긁고 있으면 다시 연결은 429 TOO_MANY_STATS_REFRESHES 이고 아무것도 바꾸지 않는다")
    void lockRejectsConcurrentLink() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("락#KR1", "sum-lock", 2, 2, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "락#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant good = syncedAt(gameAccountId);

        // 다른 사이클이 잡고 있는 자물쇠 — room 이 아니라 이 앱의 키다(qm:riot:sync:*)
        redisTemplate.opsForValue().set(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId, "someone-else",
                Duration.ofSeconds(60));
        int before = FAKE.calls();

        MvcResult rejected = putGameAccount(cookie, "LOL", json("gameNickname", "딴이름#KR1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_STATS_REFRESHES"))
                .andReturn();

        assertThat(rejected.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotNull();
        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(syncedAt(gameAccountId)).isEqualTo(good);
        assertThat(profile(cookie, "LOL").get("gameNickname").asString()).isEqualTo("락#KR1");
        // 남의 자물쇠를 풀지 않는다
        assertThat(redisTemplate.opsForValue().get(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId))
                .isEqualTo("someone-else");
    }

    // ---- 전적 갱신 (POST …/game-accounts/{game}/refresh — 2026-09-24 소유자 결정) ----

    @Test
    @DisplayName("전적 갱신을 누르면 200 이고 그 자리에서 최신 전적 · 티어를 준다 — 게임 닉네임은 바꾸지 않는다. 응답이 PUT 과 같은 게임 프로필이다")
    void refreshReturnsFreshStats() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String puuid = stubLol("갱신#KR1", "sum-refresh", 10, 10, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "갱신#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.SOLO").value("EMERALD_4"))
                .andExpect(jsonPath("$.tiers.FLEX").value("GOLD_2"));
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant before = syncedAt(gameAccountId);
        int callsBefore = FAKE.calls();

        // 그 사이에 두 판 더 이겼다(서폿으로) · 솔로랭크는 골드 II 로 떨어졌고 자유랭크는 사라졌다 — 갱신이 진짜로 다시 긁는지 보려는 것이다
        // (games 1 → 3, 연승 1 → 3). 사다리 둘을 통째로 갈아 끼운다 — 자유랭크 줄이 없어졌으니 FLEX 는 null 이 된다
        FAKE.stubRanks("sum-refresh", "GOLD", "II", null, null, 11, 12);
        FAKE.stubMatches(puuid, List.of(
                play("Lulu", 9, 1, 3, true, "UTILITY"),
                play("Nami", 7, 2, 4, true, "UTILITY"),
                play("Ahri", 1, 1, 1, true)));

        // 동기다 — 응답이 오면 이미 긁혀 있다. 티어도 같이 갱신된다(2026-09-27). 게임 닉네임은 사용자가 적은 그대로다
        // (최근 경기가 서폿이어도 포지션 칸은 생기지 않는다 — 게임 계정에 주 포지션이 없다, 2026-09-29 — P-35)
        refresh(cookie, "LOL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.gameNickname").value("갱신#KR1"))
                .andExpect(jsonPath("$.tiers.SOLO").value("GOLD_2"))
                .andExpect(jsonPath("$.tiers.FLEX").isEmpty())
                .andExpect(jsonPath("$.mainPosition").doesNotExist())
                .andExpect(jsonPath("$.stats.wins").value(11))
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
        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#EU1"))
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

    /** 미드로 한 판 */
    private static FakeRiotApi.Play play(String champion, int kills, int deaths, int assists, boolean win)
    {
        return play(champion, kills, deaths, assists, win, "MIDDLE");
    }

    /** 포지션은 Riot 의 {@code teamPosition} 그대로({@code UTILITY} · {@code BOTTOM} …) — 앱이 읽지 않는 것을 보이려고 넣는다(게임 계정에 주 포지션이 없다 — P-35) */
    private static FakeRiotApi.Play play(String champion, int kills, int deaths, int assists, boolean win, String position)
    {
        return new FakeRiotApi.Play(champion, kills, deaths, assists, win, position);
    }

    private int gameAccountCount(Long userId)
    {
        return jdbcTemplate.queryForObject("select count(*) from game_accounts where user_id = ?", Integer.class, userId);
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
                + "\"voice\":\"REQUIRED\",\"conditions\":{},\"wantedPositions\":[\"MID\"]}";
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
