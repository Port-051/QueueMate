package com.queuemate.platform.account.stats;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>PUBG 전적 · 티어 동기화</b>(2026-09-29 소유자 결정 "PUBG 티어는 API 로 채우고 LoL 처럼 동기로" — {@code contracts/platform-api.md} P-36)를
 * <b>가짜 PUBG API</b>({@link FakePubgApi})에 붙여서 본다. 진짜 PUBG 를 부르지 않는다. 응답의 모양은 2026-09-29 에 실제 키로 받은 응답에서 왔다.
 *
 * <p>{@code @DynamicPropertySource} 가 설정을 바꾸므로 <b>이 클래스만 스프링 컨텍스트를 따로 띄운다</b>. 키가 없을 때는 {@link GameStatsNotConfiguredTest} 다.
 * 사다리는 하나({@code RANKED})다 — 시즌 36 부터 티어가 모드에 걸쳐 통합됐다.
 */
class PubgStatsSyncTest extends ApiTestSupport {

    static final FakePubgApi FAKE = new FakePubgApi();

    @Autowired
    @Qualifier(GameStatsAsyncConfig.LOGIN_EXECUTOR)
    Executor gameStatsLoginExecutor;

    @DynamicPropertySource
    static void pubgProperties(DynamicPropertyRegistry registry)
    {
        registry.add("platform.pubg.api-key", () -> FakePubgApi.API_KEY);
        registry.add("platform.pubg.base-url", FAKE::baseUrl);
        registry.add("platform.pubg.connect-timeout", () -> "PT1S");
        registry.add("platform.pubg.read-timeout", () -> "PT1S");
    }

    @AfterAll
    static void stopFakePubg()
    {
        FAKE.stop();
    }

    /** 시즌 캐시를 비우고 시작한다 — 호출 수를 세는 테스트가 앞 테스트의 캐시에 흔들리지 않게. 사다리에는 이 테스트가 쓰는 이름을 seed 의 score 로 보탠다 */
    @BeforeEach
    void clearSeasonCacheAndSeedLadder()
    {
        redisTemplate.delete(List.of(PubgSeasonCache.key("steam"), PubgSeasonCache.key("kakao")));
        String ladder = "qm:gameconfig:PUBG:tier";
        redisTemplate.opsForZSet().add(ladder, "GOLD_2", 11);
        redisTemplate.opsForZSet().add(ladder, "PLATINUM_4", 13);
        redisTemplate.opsForZSet().add(ladder, "DIAMOND_3", 22);
        redisTemplate.opsForZSet().add(ladder, "MASTER", 25);
        redisTemplate.opsForZSet().add(ladder, "SURVIVOR", 26);
    }

    /**
     * 로그인 때 다시 받기가 돌고 있으면 먼저 기다린다(하위 클래스의 {@code @AfterEach} 가 상위의 계정 삭제보다 먼저 돈다).
     * 그 다음 이 앱의 키만 지운다 — 시즌 캐시 · 자물쇠({@code FLUSHDB} 금지. 쿨타임 키는 2026-09-30 에 전적 갱신과 함께 없어졌다 — P-42)
     */
    @AfterEach
    void cleanKeys()
    {
        try
        {
            awaitLoginRefreshIdle();
        }
        catch(AssertionError ignored)
        {
            // 기다려 주는 것이 목적이다 — 여기서 테스트를 깨지 않는다
        }
        redisTemplate.delete(List.of(PubgSeasonCache.key("steam"), PubgSeasonCache.key("kakao")));
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

    // ---- 연결 ----

    @Test
    @DisplayName("PUBG 게임 계정을 연결하면 응답에 티어 · 전적이 바로 들어 있다 — 실제 응답 모양(duo · squad) · 랭크 모드 합산 · 비는 칸 · detail · external_id")
    void linkFillsFromPubg() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String accountId = stubSurvivor("steam", "fakeTaco");

        JsonNode linked = readBody(putPubg(cookie, "fakeTaco", "STEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("PUBG"))
                .andExpect(jsonPath("$.gameNickname").value("fakeTaco"))
                .andExpect(jsonPath("$.server").value("STEAM"))
                .andExpect(jsonPath("$.verified").value(false))
                // 사다리는 하나다 — 실제 응답의 Survivor + subTier "1" → SURVIVOR(단을 붙이지 않는다)
                .andExpect(jsonPath("$.tiers.length()").value(1))
                .andExpect(jsonPath("$.tiers.RANKED").value("SURVIVOR")));

        // duo 11판 + squad 169판 — 랭크 모드를 전부 합산한다
        JsonNode stats = linked.get("stats");
        assertThat(stats.get("games").asInt()).isEqualTo(180);
        // P-12 의 PUBG 칸 — 승/패 · 승률 · 연승 · 어시스트 · kda 는 비는 칸이다(응답의 kda 는 늘 0 이라 쓰지 않는다)
        for(String empty : new String[]{"wins", "losses", "winRate", "winStreak", "avgAssists", "kda"})
        {
            assertThat(stats.get(empty).isNull()).as(empty).isTrue();
        }
        // (32 + 475) / 180 = 2.81… · (11 + 133) / 180 = 0.8
        assertThat(number(stats, "avgKills")).isEqualTo(2.8);
        assertThat(number(stats, "avgDeaths")).isEqualTo(0.8);
        JsonNode detail = stats.get("detail");
        assertThat(detail.get("seasonMode").asString()).isEqualTo("RANKED");
        // (4373.2744 + 69228.2) / 180 = 408.89… · 507 / 144 = 3.520… · (1 + 58) × 100 / 180 = 32.77…
        assertThat(number(detail, "avgDamage")).isEqualTo(408.9);
        assertThat(number(detail, "kd")).isEqualTo(3.52);
        assertThat(number(detail, "top1Rate")).isEqualTo(32.8);
        // PUBG 는 시즌 합산이라 경기별 승 · 패 줄이 없다(P-43 — LoL 만이다)
        assertThat(detail.has("recentResults")).isFalse();

        Long gameAccountId = gameAccountId(userIdOf(nickname));
        assertThat(jdbcTemplate.queryForObject("select external_id from game_accounts where id = ?", String.class, gameAccountId))
                .isEqualTo(accountId);
        assertThat(jdbcTemplate.queryForObject("select tiers ->> 'RANKED' from game_accounts where id = ?", String.class, gameAccountId))
                .isEqualTo("SURVIVOR");
        assertThat(jdbcTemplate.queryForObject("select source from game_account_stats where game_account_id = ?", String.class,
                gameAccountId)).isEqualTo("API");
        assertThat(linked.has("externalId")).isFalse();

        // 플레이어 · 시즌 목록 · 랭크 — 셋이다(랭크 판이 있어 일반 전적은 부르지 않는다)
        assertThat(FAKE.calls()).isEqualTo(3);
        assertThat(FAKE.calls("season")).isZero();
        // filter[playerNames] 의 대괄호는 퍼센트 인코딩돼 간다
        assertThat(FAKE.rawQueries()).containsExactly("filter%5BplayerNames%5D=fakeTaco");
        // 현재 시즌이 Redis 에 30일짜리로 남는다
        assertThat(redisTemplate.opsForValue().get(PubgSeasonCache.key("steam"))).isEqualTo(FakePubgApi.CURRENT_SEASON);
        assertThat(redisTemplate.getExpire(PubgSeasonCache.key("steam"), TimeUnit.DAYS)).isBetween(29L, 30L);

        // users/me 도 같은 값이다
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts[0].tiers.RANKED").value("SURVIVOR"))
                .andExpect(jsonPath("$.gameAccounts[0].stats.games").value(180));
    }

    @Test
    @DisplayName("시즌 목록은 캐시가 있으면 부르지 않는다 — 두 번째 연결은 PUBG 호출이 둘(플레이어 · 랭크)이다")
    void seasonIsCached() throws Exception
    {
        stubSurvivor("steam", "firstTaco");
        stubSurvivor("steam", "secondTaco");
        putPubg(login(newNickname()), "firstTaco", "STEAM").andExpect(status().isOk());
        int before = FAKE.calls();

        putPubg(login(newNickname()), "secondTaco", "STEAM").andExpect(status().isOk());

        assertThat(FAKE.calls() - before).isEqualTo(2);
        assertThat(FAKE.calls("seasons")).isEqualTo(1);
    }

    @Test
    @DisplayName("이번 시즌 랭크 판이 없으면(rankedGameModeStats 가 {}) 일반 시즌 전적을 합산하고 티어는 null — 데스는 진 판(losses)이다. 호출은 넷이다")
    void noRankedFallsBackToNormalSeason() throws Exception
    {
        Cookie cookie = login(newNickname());
        String accountId = newAccountId();
        FAKE.stubPlayer("steam", "normalOnly", accountId);
        FAKE.stubRankedFixture("steam", accountId, "ranked-empty.json");
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("duo", FakePubgApi.normalMode(10, 1, 20, 9, 2000.0));
        modes.put("squad-fpp", FakePubgApi.normalMode(30, 3, 45, 27, 6000.0));
        modes.put("solo", FakePubgApi.normalMode(0, 0, 0, 0, 0));
        FAKE.stubSeasonStats("steam", accountId, modes);

        JsonNode stats = readBody(putPubg(cookie, "normalOnly", "STEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.RANKED").isEmpty())).get("stats");

        assertThat(stats.get("games").asInt()).isEqualTo(40);
        // 65 / 40 = 1.625 → 1.6 · 36 / 40 = 0.9
        assertThat(number(stats, "avgKills")).isEqualTo(1.6);
        assertThat(number(stats, "avgDeaths")).isEqualTo(0.9);
        JsonNode detail = stats.get("detail");
        assertThat(detail.get("seasonMode").asString()).isEqualTo("NORMAL");
        assertThat(number(detail, "avgDamage")).isEqualTo(200.0);
        assertThat(number(detail, "kd")).isEqualTo(1.81);
        assertThat(number(detail, "top1Rate")).isEqualTo(10.0);
        assertThat(FAKE.calls()).isEqualTo(4);
    }

    @Test
    @DisplayName("랭크 · 일반 전적이 다 없으면(404) 판 수 0 · 평균과 detail 의 숫자가 null · 티어 null 로 연결된다")
    void noStatsAtAll() throws Exception
    {
        Cookie cookie = login(newNickname());
        FAKE.stubPlayer("steam", "freshTaco", newAccountId());

        JsonNode stats = readBody(putPubg(cookie, "freshTaco", "STEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.RANKED").isEmpty())).get("stats");

        assertThat(stats.get("games").asInt()).isZero();
        assertThat(stats.get("avgKills").isNull()).isTrue();
        assertThat(stats.get("detail").get("seasonMode").asString()).isEqualTo("NORMAL");
        assertThat(stats.get("detail").get("avgDamage").isNull()).isTrue();
        assertThat(stats.get("detail").get("kd").isNull()).isTrue();
        assertThat(stats.get("detail").get("top1Rate").isNull()).isTrue();
    }

    @Test
    @DisplayName("카카오 서버는 kakao shard 로 부른다 — 스팀에만 있는 닉네임은 카카오로 연결하면 404 다")
    void kakaoShard() throws Exception
    {
        Cookie cookie = login(newNickname());
        stubSurvivor("steam", "steamOnly");
        String kakaoAccount = newAccountId();
        FAKE.stubPlayer("kakao", "카카오닉", kakaoAccount);
        FAKE.stubRankedFixture("kakao", kakaoAccount, "ranked-diamond-squad-only.json");

        putPubg(cookie, "steamOnly", "KAKAO")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBG_PLAYER_NOT_FOUND"));
        putPubg(cookie, "카카오닉", "KAKAO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("KAKAO"))
                // 실제 응답 — squad 하나 · Diamond + "3"
                .andExpect(jsonPath("$.tiers.RANKED").value("DIAMOND_3"))
                .andExpect(jsonPath("$.stats.games").value(184));
    }

    // ---- 거절 ----

    @Test
    @DisplayName("그 서버에 그 닉네임이 없으면 404 PUBG_PLAYER_NOT_FOUND 이고 저장하지 않는다 — 이름은 대소문자까지 같아야 한다")
    void playerNotFound() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubSurvivor("steam", "fakeTaco");

        for(String wrong : new String[]{"nobody", "FAKETACO", "faketaco"})
        {
            putPubg(cookie, wrong, "STEAM")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PUBG_PLAYER_NOT_FOUND"));
        }
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
        // 없는 닉네임도 PUBG 를 한 번씩 불렀다 — 404 도 한도를 쓴다
        assertThat(FAKE.calls("players")).isEqualTo(3);
    }

    @Test
    @DisplayName("PUBG 는 tier 를 받지 않고 server 가 필수다 — PUBG 를 부르기 전에 400 이고 아무것도 저장하지 않는다")
    void validatesBeforeCallingPubg() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);

        putGameAccount(cookie, "PUBG", json("gameNickname", "fakeTaco", "server", "STEAM", "tier", "GOLD_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("tier"));
        putGameAccount(cookie, "PUBG", json("gameNickname", "fakeTaco"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("server"));
        putGameAccount(cookie, "PUBG", json("gameNickname", "fakeTaco", "server", "XBOX"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("server"));
        putGameAccount(cookie, "PUBG", json("gameNickname", "fakeTaco", "server", "STEAM", "mainPosition", "SQUAD"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("mainPosition"));

        assertThat(FAKE.calls()).isZero();
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    @Test
    @DisplayName("PUBG 가 500 을 주거나 응답이 없으면 503 GAME_STATS_UNAVAILABLE 이고 저장하지 않는다")
    void pubgFailuresSaveNothing() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubSurvivor("steam", "fakeTaco");

        FAKE.failWith(500);
        putPubg(cookie, "fakeTaco", "STEAM")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
        FAKE.failWith(0);
        // 읽기 타임아웃(PT1S)
        FAKE.respondAfter(Duration.ofSeconds(2));
        putPubg(cookie, "fakeTaco", "STEAM")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
        FAKE.respondAfter(Duration.ZERO);

        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    @Test
    @DisplayName("PUBG 의 429 는 재시도하지 않고 503 + Retry-After 다 — X-RateLimit-Reset 까지 남은 초, 없으면 60")
    void rateLimitedIs503WithRetryAfter() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        stubSurvivor("steam", "fakeTaco");
        FAKE.failWith(429);

        FAKE.rateLimitReset(Instant.now().getEpochSecond() + 42);
        MvcResult withReset = putPubg(cookie, "fakeTaco", "STEAM")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"))
                .andReturn();
        assertThat(Long.parseLong(withReset.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(38L, 43L);
        assertThat(FAKE.calls()).as("재시도하지 않는다").isEqualTo(1);

        FAKE.rateLimitReset(null);
        MvcResult withoutReset = putPubg(cookie, "fakeTaco", "STEAM")
                .andExpect(status().isServiceUnavailable())
                .andReturn();
        assertThat(withoutReset.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(gameAccountCount(userIdOf(nickname))).isZero();
    }

    // ---- 티어 ----

    @Test
    @DisplayName("PUBG 의 티어 이름을 사다리 이름으로 — Gold+2→GOLD_2, Master · Survivor 는 subTier \"1\" 이 와도 단을 붙이지 않는다")
    void ladderNames()
    {
        assertThat(PubgStatsProvider.ladderName("Gold", "2")).isEqualTo("GOLD_2");
        assertThat(PubgStatsProvider.ladderName("Diamond", "3")).isEqualTo("DIAMOND_3");
        assertThat(PubgStatsProvider.ladderName("Crystal", "4")).isEqualTo("CRYSTAL_4");
        assertThat(PubgStatsProvider.ladderName("Bronze", "1")).isEqualTo("BRONZE_1");
        assertThat(PubgStatsProvider.ladderName("Master", "1")).isEqualTo("MASTER");
        assertThat(PubgStatsProvider.ladderName("Survivor", "1")).isEqualTo("SURVIVOR");
        // 방어 — 로마 숫자 · 언랭 · 비었다
        assertThat(PubgStatsProvider.ladderName("Gold", "II")).isEqualTo("GOLD_2");
        assertThat(PubgStatsProvider.ladderName("Unranked", "")).isNull();
        assertThat(PubgStatsProvider.ladderName("Gold", null)).isNull();
        assertThat(PubgStatsProvider.ladderName("Gold", "x")).isNull();
        assertThat(PubgStatsProvider.ladderName(null, "1")).isNull();
        assertThat(PubgStatsProvider.shardOf("STEAM")).isEqualTo("steam");
        assertThat(PubgStatsProvider.shardOf("KAKAO")).isEqualTo("kakao");
        assertThat(PubgStatsProvider.shardOf("XBOX")).isNull();
        assertThat(PubgStatsProvider.shardOf(null)).isNull();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("모드마다 티어가 다르면(통합이라 정상이면 없다) RP 가 가장 큰 모드의 것을 쓰고 WARN — 판이 0 인 모드는 보지 않는다")
    void differingTiersUseHighestRankPoint(CapturedOutput output) throws Exception
    {
        Cookie cookie = login(newNickname());
        String accountId = newAccountId();
        FAKE.stubPlayer("steam", "mixedTaco", accountId);
        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("duo", FakePubgApi.rankedMode("Gold", "2", 1500, 5, 0, 5, 5, 1, 500.0));
        modes.put("squad-fpp", FakePubgApi.rankedMode("Platinum", "4", 1800, 10, 1, 10, 9, 2, 1500.0));
        // 판이 0 인 모드의 티어는 보지 않는다 — RP 가 가장 커도
        modes.put("squad", FakePubgApi.rankedMode("Master", "1", 9999, 0, 0, 0, 0, 0, 0));
        FAKE.stubRanked("steam", accountId, modes);

        putPubg(cookie, "mixedTaco", "STEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.RANKED").value("PLATINUM_4"))
                .andExpect(jsonPath("$.stats.games").value(15));
        assertThat(output.getAll()).contains("모드마다 티어가 다르다");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("만든 이름이 gameconfig 사다리에 없으면 티어는 null 이고 WARN 에 원문 값을 남긴다 — 전적은 그대로 채운다")
    void tierNotOnLadder(CapturedOutput output) throws Exception
    {
        Cookie cookie = login(newNickname());
        String accountId = newAccountId();
        FAKE.stubPlayer("steam", "oddTaco", accountId);
        FAKE.stubRanked("steam", accountId, Map.of("squad", FakePubgApi.rankedMode("Gold", "5", 1200, 4, 0, 2, 4, 0, 400.0)));

        putPubg(cookie, "oddTaco", "STEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.RANKED").isEmpty())
                .andExpect(jsonPath("$.stats.games").value(4));
        assertThat(output.getAll()).contains("사다리에 없다 tier=GOLD_5").contains("원문 tier=Gold subTier=5");
    }

    // ---- 로그인 때 다시 받기 (2026-09-30 소유자 결정 · P-42 — 사용자가 누르던 전적 갱신을 바꿨다) ----

    @Test
    @DisplayName("PUBG 도 재발급 때 1시간 넘게 지난 전적 · 티어를 뒤에서 다시 받는다 — 게임 닉네임 · 서버는 그대로다")
    void reissueRefetchesStalePubg() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        String accountId = stubSurvivor("steam", "fakeTaco");
        putPubg(cookie, "fakeTaco", "STEAM").andExpect(status().isOk());
        Long gameAccountId = gameAccountId(userIdOf(nickname));
        Instant old = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS);
        touchSyncedAt(gameAccountId, old);

        // 그 사이에 다이아 3 으로 떨어졌고 스쿼드만 했다
        FAKE.stubRankedFixture("steam", accountId, "ranked-diamond-squad-only.json");
        int before = FAKE.calls();
        reissue(nickname);
        awaitLoginRefreshIdle();

        assertThat(FAKE.calls()).isGreaterThan(before);
        assertThat(syncedAt(gameAccountId)).isAfter(old);
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts[0].gameNickname").value("fakeTaco"))
                .andExpect(jsonPath("$.gameAccounts[0].server").value("STEAM"))
                .andExpect(jsonPath("$.gameAccounts[0].tiers.RANKED").value("DIAMOND_3"))
                .andExpect(jsonPath("$.gameAccounts[0].stats.games").value(184));
    }

    @Test
    @DisplayName("서버 없이 저장된 옛 PUBG 계정(자기신고이던 때)은 로그인 때 다시 받지 않는다 — 물어볼 shard 가 없어 PUBG 를 부르지 않고 재발급은 200 이다")
    void reissueSkipsPubgWithoutServer() throws Exception
    {
        String nickname = newNickname();
        login(nickname);
        Long gameAccountId = insertGameAccount(userIdOf(nickname), "PUBG", "oldTaco", null);

        reissue(nickname);
        awaitLoginRefreshIdle();

        assertThat(FAKE.calls()).isZero();
        assertThat(jdbcTemplate.queryForList("select 1 from game_account_stats where game_account_id = ?", gameAccountId)).isEmpty();
    }

    // ---- 바탕 ----

    /** 실제 응답(Survivor · duo 11판 + squad 169판)을 그 닉네임에 넣는다. 돌려주는 것은 계정 id 다 */
    private String stubSurvivor(String shard, String name)
    {
        String accountId = newAccountId();
        FAKE.stubPlayer(shard, name, accountId);
        FAKE.stubRankedFixture(shard, accountId, "ranked-survivor-duo-squad.json");
        return accountId;
    }

    private static String newAccountId()
    {
        return "account." + UUID.randomUUID().toString().replace("-", "");
    }

    private ResultActions putPubg(Cookie cookie, String gameNickname, String server) throws Exception
    {
        return putGameAccount(cookie, "PUBG", json("gameNickname", gameNickname, "server", server));
    }

    private ResultActions putGameAccount(Cookie cookie, String game, String body) throws Exception
    {
        return mockMvc.perform(put("/api/v1/users/me/game-accounts/" + game).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 재발급 — 그 사람의 refresh 쿠키로 {@code POST /api/v1/auth/refresh}. 새로 받은 refresh 는 끝나고 지워지게 적어 둔다 */
    private void reissue(String nickname) throws Exception
    {
        refreshCookieOf(mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookieFor(nickname)))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** 로그인 때 다시 받기의 풀이 비기를 기다린다 — 요청 스레드가 응답 전에 던지므로 응답을 받은 뒤에 부르면 "받지 않는다"도 확인할 수 있다 */
    private void awaitLoginRefreshIdle()
    {
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsLoginExecutor;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while(System.nanoTime() < deadline)
        {
            if(pool.getActiveCount() == 0 && pool.getThreadPoolExecutor().getQueue().isEmpty())
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
        throw new AssertionError("로그인 뒤 전적 다시 받기가 끝나기를 기다렸지만 끝나지 않았다");
    }

    private Instant syncedAt(Long gameAccountId)
    {
        return ((Timestamp) jdbcTemplate.queryForMap("select synced_at from game_account_stats where game_account_id = ?", gameAccountId)
                .get("synced_at")).toInstant();
    }

    private void touchSyncedAt(Long gameAccountId, Instant syncedAt)
    {
        jdbcTemplate.update("update game_account_stats set synced_at = ? where game_account_id = ?",
                Timestamp.from(syncedAt), gameAccountId);
    }

    private JsonNode readBody(ResultActions actions) throws Exception
    {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 소수 칸을 숫자로 — JSON 의 글자를 그대로 읽는다 */
    private static double number(JsonNode node, String field)
    {
        return Double.parseDouble(node.get(field).toString());
    }

    private Long gameAccountId(Long userId)
    {
        return jdbcTemplate.queryForObject("select id from game_accounts where user_id = ? and game = 'PUBG'", Long.class, userId);
    }

    private int gameAccountCount(Long userId)
    {
        return jdbcTemplate.queryForObject("select count(*) from game_accounts where user_id = ?", Integer.class, userId);
    }
}
