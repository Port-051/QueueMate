package com.queuemate.platform.account.stats;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
 * <p><b>긁는 시점은 둘이다</b> — ① <b>LoL 게임 계정 연결</b>({@code PUT …/game-accounts/LOL} — 2026-09-27 소유자 결정. <b>동기다</b> —
 * 본문은 이름#태그 하나이고 저장하기 전에 긁어 티어 · 전적을 채운다. 실패하면 저장하지 않는다. 주 포지션은 없다 — 2026-09-29, P-35) ② <b>로그인 · 재발급 때 뒤에서</b>
 * (2026-09-30 소유자 결정 · P-42 — 마지막으로 받은 뒤 1시간이 지난 계정만 · 응답은 기다리지 않는다. 아래 "로그인 때 다시 받기" 묶음).
 * 사용자가 누르던 <b>전적 갱신</b>({@code POST …/game-accounts/{game}/refresh})과 그 쿨타임은 ② 로 바꾸며 없어졌다 — {@link #removedRefreshEndpoint()} 가 지킨다.
 * <b>모집 글을 쓸 때 긁던 것</b>과 신선도 장치는 2026-09-24 에 없어졌다 — 되살아나지 않게 {@link #noSyncOnPostCreate()} 가 지킨다.
 *
 * <p>{@code @DynamicPropertySource} 가 설정을 바꾸므로 <b>이 클래스만 스프링 컨텍스트를 따로 띄운다</b>. 키가 없을 때는 기본 컨텍스트에서 본다
 * ({@link GameStatsNotConfiguredTest}).
 *
 * <p>연결은 응답이 오면 이미 적혀 있다 — 기다리지 않는다. 다만 상한(30초 — 여기서는 3초)을 넘긴 긁기는 뒤에서 계속 돌므로
 * "늦게 끝난 것을 저장하지 않는다"는 전용 풀이 비기를 기다려서 본다({@link #awaitSyncIdle()}). 로그인 때 다시 받기는 <b>다른 풀</b>이라
 * 그 풀이 비기를 기다려서 본다({@link #awaitLoginRefreshIdle()}).
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
        // match-count 는 덮지 않는다 — 기본값(10판 — 2026-09-29 소유자 결정)으로 돈다. 그것을 masteryFailureKeepsStats 가 본다
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

    @Autowired
    @Qualifier(GameStatsAsyncConfig.LOGIN_EXECUTOR)
    Executor gameStatsLoginExecutor;

    @Autowired
    LolChampionNames championNames;

    @Autowired
    GameStatsSyncWorker worker;

    @Autowired
    GameStatsStore store;

    /**
     * 돌고 있는 갱신이 지워진 계정을 건드리지 않게 먼저 기다린다(하위 클래스의 {@code @AfterEach} 가 상위의 계정 삭제보다 먼저 돈다).
     * 로그인 때 다시 받기의 풀도 기다린다. 그 다음 이 앱의 락 키만 지운다 — <b>{@code FLUSHDB} 금지</b>(같은 Redis 를 {@code room} 이 쓸 수 있다).
     * (전적 갱신의 쿨타임 키 {@code qm:riot:refresh:*} 는 2026-09-30 에 요청과 함께 없어졌다 — P-42)
     */
    @AfterEach
    void settleAndCleanLocks()
    {
        try
        {
            awaitSyncIdle();
            awaitLoginRefreshIdle();
        }
        catch(AssertionError ignored)
        {
            // 기다려 주는 것이 목적이다 — 여기서 테스트를 깨지 않는다
        }
        deleteKeys(GameStatsSyncLock.SYNC_LOCK_PREFIX + "*");
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
    @DisplayName("LoL 게임 계정을 연결하면 응답에 티어 · 전적이 바로 들어 있다 — 평균 · 연승 · 솔로랭크의 승/패 · 숙련도 상위 셋의 모스트 챔피언, external_id 에 puuid")
    void linkFillsFromRiot() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String puuid = stubLol("달콤한 인생#KR7", 18, 28, List.of(
                // 새 경기가 먼저다 — 연승은 2(이김 · 이김 · 짐)
                play("Samira", 10, 2, 5, true),
                play("Samira", 8, 4, 3, true),
                play("Samira", 4, 6, 2, false),
                play("Ahri", 6, 3, 9, true),
                play("Ahri", 2, 7, 4, false),
                play("Yasuo", 3, 5, 1, false),
                play("LeeSin", 5, 5, 5, true)));
        // 숙련도 — 가짜 서버가 Teemo(3 · 12,345)를 같이 넣는다. 가장 많이 한 Samira 가 아니라 점수가 가장 높은 Ahri 가 먼저이고,
        // 한 판도 안 한 Teemo 도 셋에 든다(경기와 무관하다). 넷째(Yasuo 1,000점)는 top?count=3 이 잘라 받지 않는다. LeeSin 은 숙련도가 없어 들지 않는다
        FAKE.stubMastery(puuid, Map.of("Samira", new int[]{7, 123_456}, "Ahri", new int[]{45, 1_234_567}, "Yasuo", new int[]{5, 1_000}));
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

        // 모스트 챔피언 = 숙련도 점수 상위 셋(2026-09-30 소유자 결정 — P-39). 칸은 셋뿐이다 — 최근 경기의 판 수 · 승률(games · winRate)은 없다.
        // 이름은 숙련도의 숫자 번호를 이름표(Data Dragon)로 옮긴 것이다 — 103 → Ahri · 360 → Samira · 17 → Teemo
        JsonNode champions = stats.get("detail").get("mostChampions");
        assertThat(champions).hasSize(3);
        assertMostChampion(champions.get(0), "Ahri", 45, 1_234_567);
        assertMostChampion(champions.get(1), "Samira", 7, 123_456);
        assertMostChampion(champions.get(2), "Teemo", 3, 12_345);
        assertThat(FAKE.lastMasteryRequest()).isEqualTo("/top?count=3");
        // 최근 경기의 승 · 패(2026-09-30 — P-43) — games · 평균 · 연승과 같은 7판, 새 경기가 먼저. detail 의 칸은 이 둘뿐이다
        assertThat(stats.get("detail").propertyNames()).containsExactly("mostChampions", "recentResults");
        assertThat(recentResults(stats)).containsExactly("W", "W", "L", "W", "L", "L", "W")
                .hasSize(stats.get("games").asInt());
        // 계정 · 리그 · 경기 id · 경기 7 · 숙련도 1 — 승 · 패 줄을 위해 더 부르지 않는다 — 숙련도는 챔피언마다 부르지 않고 상위 셋을 한 번에 받는다. 소환사(summoner-v4)는 부르지 않는다 —
        // 실제 응답에 id 가 없어 리그를 puuid 로 부른다(2026-09-29)
        assertThat(FAKE.calls() - callsBefore).isEqualTo(11);
        assertThat(FAKE.summonerCalls()).isZero();

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
        // 락은 새 계정이라 잡지 않았다
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).isFalse();
    }

    @Test
    @DisplayName("다시 연결하면 이름 · 티어 · 전적이 새 Riot ID 의 것으로 바뀐다 — 한 줄이고, 쿨타임 없이 곧바로 또 할 수 있다")
    void relinkReplaces() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("처음#KR1", 1, 1, List.of(play("Ahri", 1, 1, 1, true, "TOP")));
        String second = "puuid-" + newTag();
        FAKE.stubAccount("다음#KR2", second);
        FAKE.stubSoloRank(second, "GOLD", "II", 5, 5);
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
        stubLol("포지션#KR1", 3, 3, List.of(
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
        FAKE.stubSoloRank(puuid, "OBSIDIAN", "II", 7, 3);
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
        FAKE.stubNoSoloRank(puuid);
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
    @DisplayName("연승은 가장 최근 경기부터 센다 — 최근 경기가 패면 0 이다. 경기를 하나도 못 읽으면 games 0 · 평균 · 연승이 null 이고 recentResults 는 빈 배열이다")
    void winStreakAndNoMatches() throws Exception
    {
        Cookie cookie = login(newNickname());
        String puuid = stubLol("연승#KR1", 1, 1, List.of(
                play("Ahri", 1, 1, 1, false),
                play("Ahri", 1, 1, 1, true),
                play("Ahri", 1, 1, 1, true)));
        JsonNode first = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "연승#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.winStreak").value(0))).get("stats");
        // 최근 경기가 패라 연승 0 — 승 · 패 줄의 첫 칸도 L 이다(같은 순서)
        assertThat(recentResults(first)).containsExactly("L", "W", "W");

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
        // 경기가 없으면 칸을 빼지 않고 빈 배열이다(P-43 — mostChampions 와 같은 규칙)
        assertThat(stats.get("detail").has("recentResults")).isTrue();
        assertThat(stats.get("detail").get("recentResults").isArray()).isTrue();
        assertThat(stats.get("detail").get("recentResults")).isEmpty();
    }

    @Test
    @DisplayName("참가자에서 그 사람을 못 찾은 경기는 games · 평균 · 연승과 함께 recentResults 에서도 빠진다 — 나머지 경기의 순서는 그대로다 (P-43)")
    void unreadableMatchIsSkippedInRecentResults() throws Exception
    {
        Cookie cookie = login(newNickname());
        // 둘째 경기(null)는 그 사람이 참가자에 없다 — 앱이 읽지 못해 건너뛴다. Riot 은 그 경기도 불렀다(경기 4번)
        stubLol("빠진경기#KR1", 5, 5, java.util.Arrays.asList(
                play("Ahri", 3, 1, 3, true),
                null,
                play("Ahri", 1, 3, 1, false),
                play("Ahri", 2, 2, 2, true)));
        int callsBefore = FAKE.calls();

        JsonNode stats = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "빠진경기#KR1"))
                .andExpect(status().isOk())).get("stats");

        assertThat(stats.get("games").asInt()).isEqualTo(3);
        assertThat(recentResults(stats)).containsExactly("W", "L", "W");
        assertThat(stats.get("winStreak").asInt()).isEqualTo(1);
        assertThat(number(stats, "avgKills")).isEqualTo(2.0);
        // 계정 · 리그 · 경기 id · 경기 4 · 숙련도 1 — 못 읽은 경기도 부르기는 했다. 승 · 패 줄이 호출을 늘리지 않는다
        assertThat(FAKE.calls() - callsBefore).isEqualTo(8);
    }

    @Test
    @DisplayName("글을 써도 전적을 긁지 않는다 — 전적이 아무리 오래됐어도 Riot 을 한 번도 부르지 않는다 (2026-09-24 소유자 결정)")
    void noSyncOnPostCreate() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("글쓴이#KR1", 5, 5, List.of(play("Ahri", 1, 1, 1, true)));
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
    @DisplayName("숙련도 호출만 500 이면 연결은 200 이고 나머지 전적은 정상 · 모스트 챔피언만 빈 배열이다 — 경기가 20판 있어도 기본값대로 최근 10판만 읽어 Riot 호출은 14번")
    @ExtendWith(OutputCaptureExtension.class)
    void masteryFailureKeepsStats(CapturedOutput output) throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        // 가짜 서버는 count 를 보지 않고 20판을 다 준다 — 앱이 match-count(기본 10)만큼만 읽는지 본다(games 10 · 평균이 그 10판의 것)
        List<FakeRiotApi.Play> twenty = new java.util.ArrayList<>();
        for(int i = 0; i < 20; i++)
        {
            // 최근 10판은 킬 4, 나머지 10판은 킬 8 — 평균 킬이 4.0 이면 최근 10판만 읽은 것이다
            twenty.add(play(i < 6 ? "Ahri" : "Yasuo", i < 10 ? 4 : 8, 2, 6, i % 2 == 0));
        }
        String puuid = stubLol("숙련도#KR1", 30, 20, twenty);
        FAKE.stubMastery(puuid, Map.of("Ahri", new int[]{12, 99_999}));
        FAKE.failMasteryWith(500);
        int callsBefore = FAKE.calls();

        JsonNode stats = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "숙련도#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.SOLO").value("EMERALD_4"))).get("stats");

        // 계정 · 리그 · 경기 id · 경기 10 · 숙련도 1 — 경기 20판이던 때는 24번이었다. 모스트 챔피언을 숙련도 상위 셋으로 바꾼 뒤(2026-09-30)에도 14번 그대로다
        assertThat(FAKE.calls() - callsBefore).isEqualTo(14);
        assertThat(stats.get("games").asInt()).isEqualTo(10);
        assertThat(number(stats, "avgKills")).isEqualTo(4.0);
        // 승 · 패 줄도 읽은 10판만이다(P-43) — 짝수 번째가 이긴 판
        assertThat(recentResults(stats)).containsExactly("W", "L", "W", "L", "W", "L", "W", "L", "W", "L");
        // 승/패는 읽은 경기 수와 무관한 솔로랭크 시즌 누적이다
        assertThat(stats.get("wins").asInt()).isEqualTo(30);
        assertThat(stats.get("losses").asInt()).isEqualTo(20);
        // 모스트 챔피언은 숙련도에서만 온다 — 최근 경기로 채우지 않는다
        assertThat(stats.get("detail").get("mostChampions")).isEmpty();
        assertThat(output).contains("챔피언 숙련도를 받지 못했다");
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "LOL"))).isNotNull();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("모스트 챔피언의 순서와 이름 — 점수 같으면 레벨 높은 순 → 이름순. 이름표에 없는 챔피언은 최근 경기의 이름, 거기에도 없으면 번호의 글자 (P-39)")
    void mostChampionsOrderAndNames(CapturedOutput output) throws Exception
    {
        Cookie cookie = login(newNickname());
        // Lulu · Neverplayed 는 가짜 서버가 실제가 아닌 번호(10000 이상)를 준다 — 이름표에 없는 "새 챔피언" 이다.
        // Lulu 는 최근 경기에 있어 그 championName 으로 메우고, Neverplayed 는 어디에도 없어 번호를 글자로 쓴다
        String puuid = stubLol("순서#KR1", 5, 5, List.of(play("Lulu", 1, 1, 1, true)));
        Map<String, int[]> masteries = new LinkedHashMap<>();
        // 셋 다 500,000점 — 가짜 서버는 같은 점수를 넣은 순서(Lulu · Neverplayed · Ahri)로 준다. 앱이 다시 줄 세운다
        masteries.put("Lulu", new int[]{8, 500_000});
        masteries.put("Neverplayed", new int[]{8, 500_000});
        masteries.put("Ahri", new int[]{10, 500_000});
        FAKE.stubMastery(puuid, masteries);
        String neverplayed = String.valueOf(FakeRiotApi.championKey("Neverplayed"));

        JsonNode champions = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "순서#KR1"))
                .andExpect(status().isOk())).get("stats").get("detail").get("mostChampions");

        // 레벨 10 인 Ahri 가 먼저 → 레벨 8 둘은 이름(글자)순 — 숫자가 글자보다 앞이다(String 비교). 넷째 Teemo(12,345점)는 받지 않는다
        assertThat(champions).hasSize(3);
        assertMostChampion(champions.get(0), "Ahri", 10, 500_000);
        assertMostChampion(champions.get(1), neverplayed, 8, 500_000);
        assertMostChampion(champions.get(2), "Lulu", 8, 500_000);
        assertThat(output).contains("이름을 모르는 챔피언이다 championId=" + neverplayed);
        assertThat(output).doesNotContain("이름을 모르는 챔피언이다 championId=" + FakeRiotApi.championKey("Lulu"));
    }

    @Test
    @DisplayName("경기가 하나도 없어도 모스트 챔피언은 숙련도에서 채운다 — Riot 호출은 계정 · 리그 · 경기 id · 숙련도 4번")
    void mostChampionsWithoutMatches() throws Exception
    {
        Cookie cookie = login(newNickname());
        String puuid = stubLol("무경기숙련#KR1", 5, 5, List.of());
        FAKE.stubMastery(puuid, Map.of("Samira", new int[]{20, 300_000}));
        int callsBefore = FAKE.calls();

        JsonNode stats = readBody(putGameAccount(cookie, "LOL", json("gameNickname", "무경기숙련#KR1"))
                .andExpect(status().isOk())).get("stats");

        assertThat(stats.get("games").asInt()).isZero();
        JsonNode champions = stats.get("detail").get("mostChampions");
        assertThat(champions).hasSize(2);
        assertMostChampion(champions.get(0), "Samira", 20, 300_000);
        assertMostChampion(champions.get(1), "Teemo", 3, 12_345);
        assertThat(FAKE.calls() - callsBefore).isEqualTo(4);
    }

    @Test
    @DisplayName("챔피언 이름표 — Data Dragon 16.18.1 의 173개. 숫자 번호를 프런트 목록과 같은 이름(Data Dragon ID)으로 옮기고, 모르는 번호는 null")
    void championNameTable()
    {
        assertThat(championNames.size()).isEqualTo(173);
        assertThat(championNames.name(7)).isEqualTo("Leblanc");
        assertThat(championNames.name(145)).isEqualTo("Kaisa");
        assertThat(championNames.name(268)).isEqualTo("Azir");
        assertThat(championNames.name(517)).isEqualTo("Sylas");
        assertThat(championNames.name(64)).isEqualTo("LeeSin");
        assertThat(championNames.name(9)).isEqualTo("Fiddlesticks");
        assertThat(championNames.name(950)).isEqualTo("Naafiri");
        assertThat(championNames.name(0)).isNull();
        assertThat(championNames.name(FakeRiotApi.championKey("Lulu"))).isNull();
    }

    @Test
    @DisplayName("Riot 이 500 · 429 를 주거나 응답이 없으면 503 GAME_STATS_UNAVAILABLE 이고 game_accounts 에 줄이 생기지 않는다")
    void riotFailuresSaveNothing() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("실패#KR1", 3, 1, List.of(play("Ahri", 9, 1, 1, true)));

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
        stubLol("그대로#KR1", 3, 1, List.of(play("Ahri", 9, 1, 1, true)));
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
        // 부르는 것이 7번(계정 · 리그 · 경기 id · 경기 3 · 숙련도) — 한 번에 0.7초면 상한 3초를 넘긴다(읽기 타임아웃 1초는 안 넘는다)
        stubLol("느림#KR1", 1, 1, List.of(
                play("Ahri", 1, 1, 1, true), play("Ahri", 1, 1, 1, true), play("Ahri", 1, 1, 1, true)));
        FAKE.respondAfter(Duration.ofMillis(700));
        int before = FAKE.calls();

        putGameAccount(cookie, "LOL", json("gameNickname", "느림#KR1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));

        // 뒤에서 돌던 긁기가 끝나기를 기다린다 — 끝나도 아무것도 적히지 않는다
        awaitSyncIdle();
        FAKE.respondAfter(Duration.ZERO);
        assertThat(FAKE.calls()).as("뒤에서 끝까지 긁기는 했다").isEqualTo(before + 7);
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    @Test
    @DisplayName("VALORANT 는 긁지 않는다 — 자기신고를 그대로 적고 곧바로 답한다. Riot 을 부르지 않는다(PUBG 는 PUBG API 다 — PubgStatsSyncTest)")
    void valorantIsNotFetched() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        int before = FAKE.calls();

        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#EU1", "tier", "DIAMOND_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.COMPETITIVE").value("DIAMOND_2"))
                .andExpect(jsonPath("$.stats").isEmpty());
        awaitSyncIdle();

        assertThat(FAKE.calls()).isEqualTo(before);
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "VALORANT"))).isNull();
    }

    @Test
    @DisplayName("락 — 같은 게임 계정을 누가 이미 긁고 있으면 다시 연결은 429 TOO_MANY_STATS_REFRESHES 이고 아무것도 바꾸지 않는다")
    void lockRejectsConcurrentLink() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("락#KR1", 2, 2, List.of(play("Ahri", 1, 1, 1, true)));
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

    // ---- 로그인 때 다시 받기 (2026-09-30 소유자 결정 · P-42 — 사용자가 누르던 전적 갱신을 바꿨다) ----

    @Test
    @DisplayName("재발급이 성공하면 1시간 넘게 지난 전적을 뒤에서 다시 받는다 — 응답은 기다리지 않고, 끝나면 티어 · 전적이 새것이다(게임 닉네임은 그대로)")
    void reissueRefetchesStaleStatsInBackground() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String puuid = stubLol("다시받기#KR1", 10, 10, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "다시받기#KR1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.FLEX").value("GOLD_2"));
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        // 61분 전에 받은 것이다 — 기준(1시간)을 막 넘겼다
        Instant old = Instant.now().minus(Duration.ofMinutes(61)).truncatedTo(ChronoUnit.MILLIS);
        touchSyncedAt(gameAccountId, old);

        // 그 사이에 두 판 더 이겼고(서폿으로) · 솔로랭크는 골드 II 로 떨어졌고 자유랭크는 사라졌다 — 사다리 둘을 통째로 갈아 끼운다
        FAKE.stubRanks(puuid, "GOLD", "II", null, null, 11, 12);
        FAKE.stubMatches(puuid, List.of(
                play("Lulu", 9, 1, 3, true, "UTILITY"),
                play("Nami", 7, 2, 4, true, "UTILITY"),
                play("Ahri", 1, 1, 1, true)));
        // Riot 을 느리게 — 한 번에 0.3초 · 모두 7번(계정 · 리그 · 경기 id · 경기 3 · 숙련도)이면 2초가 넘는다
        FAKE.respondAfter(Duration.ofMillis(300));
        int callsBefore = FAKE.calls();

        MvcResult reissued = reissue(nickname);

        // 응답은 기다리지 않았다 — 첫 호출만도 0.3초인데 벌써 새 쿠키를 받았고 전적은 아직 옛것이다
        assertThat(reissued.getResponse().getCookie("qm_access")).isNotNull();
        assertThat(syncedAt(gameAccountId)).isEqualTo(old);

        awaitLoginRefreshIdle();
        FAKE.respondAfter(Duration.ZERO);
        assertThat(FAKE.calls() - callsBefore).isEqualTo(7);
        assertThat(syncedAt(gameAccountId)).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
        JsonNode profile = profile(cookie, "LOL");
        assertThat(profile.get("gameNickname").asString()).isEqualTo("다시받기#KR1");
        assertThat(profile.get("tiers").get("SOLO").asString()).isEqualTo("GOLD_2");
        assertThat(profile.get("tiers").get("FLEX").isNull()).isTrue();
        JsonNode stats = profile.get("stats");
        assertThat(stats.get("games").asInt()).isEqualTo(3);
        assertThat(stats.get("wins").asInt()).isEqualTo(11);
        assertThat(stats.get("winStreak").asInt()).isEqualTo(3);
        // 끝나면 자물쇠를 푼다. 쿨타임 키는 없다(전적 갱신과 함께 없앴다)
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).isFalse();
        assertThat(redisTemplate.hasKey("qm:riot:refresh:" + gameAccountId)).isFalse();
    }

    @Test
    @DisplayName("진짜 로그인(개발용 로그인 — 소셜 로그인과 같은 길)도 같다 — 1시간 넘게 지난 전적을 뒤에서 다시 받는다")
    void loginRefetchesStaleStats() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("로그인#KR1", 4, 4, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "로그인#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant old = Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MILLIS);
        touchSyncedAt(gameAccountId, old);
        int callsBefore = FAKE.calls();

        MvcResult loggedIn = mockMvc.perform(post("/api/v1/auth/dev-login")
                        .contentType(MediaType.APPLICATION_JSON).content(json("nickname", nickname)))
                .andExpect(status().isOk())
                .andReturn();
        refreshCookieOf(loggedIn);
        assertThat(objectMapper.readTree(loggedIn.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("userId").asLong())
                .isEqualTo(userIdOf(nickname));
        awaitLoginRefreshIdle();

        assertThat(FAKE.calls()).isGreaterThan(callsBefore);
        assertThat(syncedAt(gameAccountId)).isAfter(old);
    }

    @Test
    @DisplayName("1시간이 안 지난 전적(59분)과 긁는 구현이 없는 VALORANT 는 다시 받지 않는다 — Riot 을 한 번도 부르지 않는다")
    void freshAndValorantAreNotRefetched() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("신선#KR1", 4, 4, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "신선#KR1")).andExpect(status().isOk());
        putGameAccount(cookie, "VALORANT", json("gameNickname", "제트#KR1", "tier", "DIAMOND_2")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant recent = Instant.now().minus(Duration.ofMinutes(59)).truncatedTo(ChronoUnit.MILLIS);
        touchSyncedAt(gameAccountId, recent);
        int callsBefore = FAKE.calls();

        reissue(nickname);
        awaitLoginRefreshIdle();

        assertThat(FAKE.calls()).isEqualTo(callsBefore);
        assertThat(syncedAt(gameAccountId)).isEqualTo(recent);
        assertThat(statsRow(gameAccountId(userIdOf(nickname), "VALORANT"))).isNull();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("뒤에서 다시 받다 Riot 이 실패해도 재발급은 200 이고 옛 전적이 그대로다 — 재시도하지 않고(한 번), 다음 재발급 때 다시 받는다")
    void backgroundFailureKeepsOldStats(CapturedOutput output) throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("실패뒤#KR1", 4, 6, List.of(play("Ahri", 2, 2, 2, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "실패뒤#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant old = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS);
        touchSyncedAt(gameAccountId, old);

        FAKE.failWith(500);
        int callsBefore = FAKE.calls();
        MvcResult reissued = reissue(nickname);
        assertThat(reissued.getResponse().getCookie("qm_access")).isNotNull();
        awaitLoginRefreshIdle();

        // 계정 주소 한 번에 실패했고 되풀이하지 않았다
        assertThat(FAKE.calls() - callsBefore).isEqualTo(1);
        assertThat(syncedAt(gameAccountId)).as("옛 값 그대로다").isEqualTo(old);
        assertThat(profile(cookie, "LOL").get("stats").get("games").asInt()).isEqualTo(1);
        assertThat(output.getAll()).contains("로그인 뒤 전적 다시 받기에 실패했다");
        assertThat(redisTemplate.hasKey(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId)).as("락을 풀었다").isFalse();

        // 실패는 기다림을 늘리지 않는다 — 다음 재발급이 곧바로 다시 받는다
        FAKE.failWith(0);
        reissue(nickname);
        awaitLoginRefreshIdle();
        assertThat(syncedAt(gameAccountId)).isAfter(old);
    }

    @Test
    @DisplayName("누가 이미 같은 계정을 긁고 있으면(연결 · 다른 탭) 뒤에서 다시 받기는 건너뛴다 — 줄 서지 않고 남의 자물쇠를 풀지 않는다")
    void lockHeldSkipsBackgroundRefetch() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("자물쇠뒤#KR1", 1, 2, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "자물쇠뒤#KR1")).andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname), "LOL");
        Instant old = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS);
        touchSyncedAt(gameAccountId, old);
        redisTemplate.opsForValue().set(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId, "someone-else",
                Duration.ofSeconds(60));
        int callsBefore = FAKE.calls();

        reissue(nickname);
        awaitLoginRefreshIdle();

        assertThat(FAKE.calls()).isEqualTo(callsBefore);
        assertThat(syncedAt(gameAccountId)).isEqualTo(old);
        assertThat(redisTemplate.opsForValue().get(GameStatsSyncLock.SYNC_LOCK_PREFIX + gameAccountId))
                .isEqualTo("someone-else");
    }

    @Test
    @DisplayName("자물쇠를 잡은 뒤 다시 읽어 그 사이에 누가 받았으면 긁지 않는다 — 두 탭이 같은 낡은 줄을 보고 거의 같이 재발급해도 한 번만 긁는다")
    void rereadUnderLockSkipsWhenJustFetched() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("두탭#KR1", 4, 4, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "두탭#KR1")).andExpect(status().isOk());
        Long userId = userIdOf(nickname);
        touchSyncedAt(gameAccountId(userId, "LOL"), Instant.now().minus(Duration.ofHours(2)));
        // 두 탭이 같은 낡은 줄을 읽었다
        GameAccountWithStats seenByBoth = store.find(userId, Game.LOL).orElseThrow();
        Instant staleBefore = Instant.now().minus(Duration.ofHours(1));
        int callsBefore = FAKE.calls();

        assertThat(worker.syncIfStale(seenByBoth.account(), staleBefore)).isEqualTo(GameStatsSyncWorker.SyncOutcome.SAVED);
        int afterFirst = FAKE.calls();
        // 둘째 탭 — 자물쇠를 잡고 다시 읽으니 방금 받은 것이다
        assertThat(worker.syncIfStale(seenByBoth.account(), staleBefore)).isEqualTo(GameStatsSyncWorker.SyncOutcome.FRESH);

        assertThat(afterFirst).isGreaterThan(callsBefore);
        assertThat(FAKE.calls()).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("전적 갱신 요청(POST …/game-accounts/{game}/refresh)은 없어졌다 — 없는 경로와 같은 404 NOT_FOUND 이고 Riot 을 부르지 않는다. 로그인하지 않으면 401 (P-42)")
    void removedRefreshEndpoint() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubLol("없앤요청#KR1", 4, 4, List.of(play("Ahri", 1, 1, 1, true)));
        putGameAccount(cookie, "LOL", json("gameNickname", "없앤요청#KR1")).andExpect(status().isOk());
        int callsBefore = FAKE.calls();

        for(String game : new String[]{"LOL", "PUBG", "VALORANT"})
        {
            mockMvc.perform(post("/api/v1/users/me/game-accounts/" + game + "/refresh").cookie(cookie))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        mockMvc.perform(post("/api/v1/users/me/game-accounts/LOL/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(FAKE.calls()).isEqualTo(callsBefore);
    }

    // ---- 바탕 ----

    /** 재발급 — 그 사람의 refresh 쿠키로 {@code POST /api/v1/auth/refresh}. 새로 받은 refresh 는 끝나고 지워지게 적어 둔다 */
    private MvcResult reissue(String nickname) throws Exception
    {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookieFor(nickname)))
                .andExpect(status().isOk())
                .andReturn();
        refreshCookieOf(result);
        return result;
    }

    /** 그 닉네임으로 계정 · 리그(솔로 에메랄드 IV · 자유 골드 II) · 경기를 한꺼번에 넣는다. 돌려주는 것은 {@code puuid} 다 */
    private String stubLol(String riotId, int wins, int losses, List<FakeRiotApi.Play> plays)
    {
        String puuid = "puuid-" + newTag();
        FAKE.stubAccount(riotId, puuid);
        FAKE.stubSoloRank(puuid, wins, losses);
        FAKE.stubMatches(puuid, plays);
        return puuid;
    }

    /** 모스트 챔피언 한 줄 — 칸이 정확히 셋(championId · masteryLevel · masteryPoints)이고 최근 경기의 칸(games · winRate)이 없다 */
    private static void assertMostChampion(JsonNode champion, String championId, int level, long points)
    {
        assertThat(champion.propertyNames()).containsExactly("championId", "masteryLevel", "masteryPoints");
        assertThat(champion.get("championId").asString()).isEqualTo(championId);
        assertThat(champion.get("masteryLevel").asInt()).isEqualTo(level);
        assertThat(champion.get("masteryPoints").asLong()).isEqualTo(points);
    }

    /** {@code stats.detail.recentResults} 를 글자 목록으로(P-43). 칸이 없거나 배열이 아니면 테스트를 깬다 */
    private static List<String> recentResults(JsonNode stats)
    {
        JsonNode results = stats.get("detail").get("recentResults");
        assertThat(results).as("detail.recentResults").isNotNull();
        assertThat(results.isArray()).as("detail.recentResults 는 배열이다").isTrue();
        List<String> values = new java.util.ArrayList<>();
        results.forEach(one -> values.add(one.asString()));
        return values;
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
        // mode 는 gameconfig 에 있는 이름이어야 한다(2026-09-24) — ApiTestSupport 가 심어 둔다. 포지션이 있는 모드라 방장 포지션이 필수다(2026-09-30 — P-38)
        String body = "{\"game\":\"LOL\",\"mode\":\"" + LOL_MODE + "\",\"title\":\"같이 하실 분\",\"description\":\"즐겁게\","
                + "\"voice\":\"REQUIRED\",\"conditions\":{},\"wantedPositions\":[\"MID\"],\"hostPosition\":\"JUNGLE\"}";
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

    /** 로그인 때 다시 받기의 풀이 비기를 기다린다 — 요청 스레드가 응답 전에 던지므로 응답을 받은 뒤에 부르면 "받지 않는다"도 확인할 수 있다 */
    private void awaitLoginRefreshIdle()
    {
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsLoginExecutor;
        await(() -> pool.getActiveCount() == 0 && pool.getThreadPoolExecutor().getQueue().isEmpty(),
                "로그인 뒤 전적 다시 받기가 끝나기를");
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
