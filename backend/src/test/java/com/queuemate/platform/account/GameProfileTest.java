package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.dto.UserGameProfile;
import com.queuemate.platform.account.service.GameProfileReader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게임 프로필 — {@code contracts/platform-api.md} "게임 프로필". 게임 계정 {@code PUT} 의 {@code server}, 읽기 전용 칸({@code verified} · {@code stats}),
 * 전적 스냅숏을 읽는 쪽, 그리고 {@code account} 밖에 내주는 창구({@link GameProfileReader}).
 *
 * <p><b>전적을 채우는 기능은 없다</b> — 그래서 테스트가 SQL 로 줄을 직접 넣어 읽는 쪽을 본다.
 *
 * <p>SQL 과 창구가 쓰는 것은 <b>사용자 번호</b>({@link #userIdOf})다 — 로그인 아이디는 가입 · 로그인에만 쓴다.
 */
class GameProfileTest extends ApiTestSupport {

    @Autowired
    GameProfileReader gameProfileReader;

    @Test
    @DisplayName("PUT 의 응답은 게임 프로필이다 — verified 는 false, stats 는 null. users/me 도 같은 모양을 준다")
    void putReturnsGameProfile() throws Exception
    {
        Cookie cookie = login(newNickname());

        putGameAccount(cookie, "LOL", json("gameNickname", "달콤한 인생#KR7", "tier", "EMERALD_4", "mainPosition", "MID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.gameNickname").value("달콤한 인생#KR7"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.tier").value("EMERALD_4"))
                .andExpect(jsonPath("$.mainPosition").value("MID"))
                .andExpect(jsonPath("$.server").isEmpty())
                .andExpect(jsonPath("$.stats").isEmpty())
                // 게임사 쪽 식별자는 밖에 내보내지 않는다
                .andExpect(jsonPath("$.externalId").doesNotExist());

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameAccounts[0].verified").value(false))
                .andExpect(jsonPath("$.gameAccounts[0].server").isEmpty())
                .andExpect(jsonPath("$.gameAccounts[0].stats").isEmpty());
    }

    @Test
    @DisplayName("server 는 PUBG 만 받는다(STEAM · KAKAO) — 다른 게임의 server 와 PUBG 의 모르는 server 는 400 이다")
    void server() throws Exception
    {
        Cookie cookie = login(newNickname());

        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "tier", null, "mainPosition", null, "server", "STEAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("STEAM"));
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "server", "KAKAO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("KAKAO"));
        // 비울 수 있다
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "server", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").isEmpty());

        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "server", "XBOX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("server"));
        putGameAccount(cookie, "LOL", json("gameNickname", "x", "server", "STEAM"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("server"));
        putGameAccount(cookie, "VALORANT", json("gameNickname", "x", "server", "KAKAO"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("server"));

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts.length()").value(1))
                .andExpect(jsonPath("$.gameAccounts[0].game").value("PUBG"));
    }

    @Test
    @DisplayName("verified · externalId · stats 는 요청으로 바꿀 수 없고, 다시 PUT 해도 덮어쓰이지 않는다")
    void readOnlyFieldsSurviveUpsert() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        Long userId = userIdOf(nickname);
        putGameAccount(cookie, "LOL", json("gameNickname", "before", "tier", "GOLD_1", "mainPosition", "TOP"))
                .andExpect(status().isOk());
        // 게임사 인증이 붙었다고 치고 DB 에서 직접 켠다 — 앱에는 켜는 길이 없다
        jdbcTemplate.update("update game_accounts set verified = true, external_id = 'puuid-123' "
                + "where user_id = ? and game = 'LOL'", userId);
        insertStats(gameAccountId(userId, "LOL"), 15, 10, 5, "3.0", "2.0", "4.0", 2, "{}");

        // 본문에 읽기 전용 칸을 실어 보내도 무시된다
        putGameAccount(cookie, "LOL", "{\"gameNickname\":\"after\",\"tier\":\"GOLD_2\",\"mainPosition\":\"MID\","
                + "\"verified\":false,\"externalId\":\"hacked\",\"stats\":{\"wins\":999}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameNickname").value("after"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.stats.wins").value(10));

        assertThat(jdbcTemplate.queryForObject(
                "select external_id from game_accounts where user_id = ? and game = 'LOL'",
                String.class, userId)).isEqualTo("puuid-123");
    }

    @Test
    @DisplayName("전적 줄이 있으면 stats 가 계약의 모양으로 온다 — winRate 는 정수 퍼센트, kda 는 계산값, detail 은 jsonb 그대로")
    void statsShape() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        putGameAccount(cookie, "LOL", json("gameNickname", "stats", "tier", "EMERALD_4", "mainPosition", "MID"))
                .andExpect(status().isOk());
        insertStats(gameAccountId(userIdOf(nickname), "LOL"), 364, 180, 184, "10.6", "5.7", "5.8", 3,
                "{\"mostChampions\":[{\"championId\":103,\"games\":40,\"winRate\":55}]}");

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameAccounts[0].stats.games").value(equalTo(364L), Long.class))
                .andExpect(jsonPath("$.gameAccounts[0].stats.wins").value(180))
                .andExpect(jsonPath("$.gameAccounts[0].stats.losses").value(184))
                // 180 / 364 = 49.45…
                .andExpect(jsonPath("$.gameAccounts[0].stats.winRate").value(49))
                .andExpect(jsonPath("$.gameAccounts[0].stats.winStreak").value(3))
                .andExpect(jsonPath("$.gameAccounts[0].stats.avgKills").value(10.6))
                .andExpect(jsonPath("$.gameAccounts[0].stats.avgDeaths").value(5.7))
                .andExpect(jsonPath("$.gameAccounts[0].stats.avgAssists").value(5.8))
                // (10.6 + 5.8) / 5.7 = 2.877…
                .andExpect(jsonPath("$.gameAccounts[0].stats.kda").value(2.88))
                .andExpect(jsonPath("$.gameAccounts[0].stats.detail.mostChampions[0].championId").value(103))
                .andExpect(jsonPath("$.gameAccounts[0].stats.syncedAt").isString())
                // 출처(SELF · API)는 계약의 모양에 없다
                .andExpect(jsonPath("$.gameAccounts[0].stats.source").doesNotExist());
    }

    @Test
    @DisplayName("데스가 0 이거나 평균값이 없으면 kda 는 null, 판 수가 0 이면 winRate 도 null 이다")
    void statsWithoutDenominator() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        Long userId = userIdOf(nickname);
        putGameAccount(cookie, "LOL", json("gameNickname", "a")).andExpect(status().isOk());
        putGameAccount(cookie, "VALORANT", json("gameNickname", "b")).andExpect(status().isOk());
        insertStats(gameAccountId(userId, "LOL"), 0, 0, 0, "3.0", "0.0", "1.0", 0, "{}");
        insertStats(gameAccountId(userId, "VALORANT"), 4, 3, 1, null, null, null, 0, "{}");

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameAccounts[0].game").value("LOL"))
                .andExpect(jsonPath("$.gameAccounts[0].stats.winRate").isEmpty())
                .andExpect(jsonPath("$.gameAccounts[0].stats.kda").isEmpty())
                .andExpect(jsonPath("$.gameAccounts[1].stats.winRate").value(75))
                .andExpect(jsonPath("$.gameAccounts[1].stats.avgKills").isEmpty())
                .andExpect(jsonPath("$.gameAccounts[1].stats.kda").isEmpty())
                .andExpect(jsonPath("$.gameAccounts[1].stats.detail").isMap());
    }

    @Test
    @DisplayName("PUBG 처럼 승/패 · 연승이 없는 전적도 stats 가 온다 — 그 칸들은 빠지지 않고 null 이다")
    void statsWithoutWinsAndLosses() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "server", "STEAM")).andExpect(status().isOk());
        // 승/패도 연승도 어시스트도 없다 — 치킨률 · 평균 데미지는 detail 에 담긴다
        insertStats(gameAccountId(userIdOf(nickname), "PUBG"), 120, null, null, "4.2", "3.1", null, null,
                "{\"chickenRate\":7,\"avgDamage\":312.5}");

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameAccounts[0].game").value("PUBG"))
                .andExpect(jsonPath("$.gameAccounts[0].stats.games").value(equalTo(120L), Long.class))
                // 칸 자체는 있고 값이 null 이다 — 화면이 "정보 없음"으로 그린다
                .andExpect(jsonPath("$.gameAccounts[0].stats.wins").value(nullValue()))
                .andExpect(jsonPath("$.gameAccounts[0].stats.losses").value(nullValue()))
                .andExpect(jsonPath("$.gameAccounts[0].stats.winRate").value(nullValue()))
                .andExpect(jsonPath("$.gameAccounts[0].stats.winStreak").value(nullValue()))
                .andExpect(jsonPath("$.gameAccounts[0].stats.avgAssists").value(nullValue()))
                // K/D 는 어시스트가 없어 kda 를 만들지 못한다 — 화면은 detail 과 평균값으로 그린다
                .andExpect(jsonPath("$.gameAccounts[0].stats.kda").value(nullValue()))
                .andExpect(jsonPath("$.gameAccounts[0].stats.avgKills").value(4.2))
                .andExpect(jsonPath("$.gameAccounts[0].stats.avgDeaths").value(3.1))
                .andExpect(jsonPath("$.gameAccounts[0].stats.detail.chickenRate").value(7))
                .andExpect(jsonPath("$.gameAccounts[0].stats.detail.avgDamage").value(312.5))
                .andExpect(jsonPath("$.gameAccounts[0].stats.syncedAt").isString());
    }

    @Test
    @DisplayName("창구 findProfiles — 여러 사용자의 닉네임과 그 게임의 프로필을 한 번에 준다. 계정이 없으면 profile 이 null, 없는 사용자는 결과에 없다")
    void findProfiles() throws Exception
    {
        String withStatsNickname = newNickname();
        String withoutStatsNickname = newNickname();
        String otherGameOnlyNickname = newNickname();
        putGameAccount(login(withStatsNickname), "LOL",
                json("gameNickname", "one", "tier", "GOLD_1", "mainPosition", "MID")).andExpect(status().isOk());
        putGameAccount(login(withoutStatsNickname), "LOL",
                json("gameNickname", "two", "tier", null, "mainPosition", "SUPPORT")).andExpect(status().isOk());
        putGameAccount(login(otherGameOnlyNickname), "PUBG",
                json("gameNickname", "three", "server", "STEAM")).andExpect(status().isOk());
        Long withStats = userIdOf(withStatsNickname);
        Long withoutStats = userIdOf(withoutStatsNickname);
        Long otherGameOnly = userIdOf(otherGameOnlyNickname);
        // 아무도 아닌 번호 — 멤버 SET 에 남은 유령처럼 있을 수 없는 사용자가 섞여 들어올 수 있다
        Long nobody = unknownUserId();
        insertStats(gameAccountId(withStats, "LOL"), 10, 6, 4, "5.0", "2.5", "5.0", 1, "{}");

        // 같은 번호가 두 번 들어와도 된다 — 멤버 SET 에서 온 값을 그대로 넘길 수 있어야 한다
        Map<Long, UserGameProfile> profiles = gameProfileReader.findProfiles(
                List.of(withStats, withoutStats, otherGameOnly, nobody, withStats), Game.LOL);

        assertThat(profiles).containsOnlyKeys(withStats, withoutStats, otherGameOnly);
        assertThat(profiles.get(withStats).nickname()).isEqualTo(withStatsNickname);
        assertThat(profiles.get(withStats).profile().mainPosition()).isEqualTo("MID");
        assertThat(profiles.get(withStats).profile().stats().games()).isEqualTo(10);
        assertThat(profiles.get(withStats).profile().stats().winRate()).isEqualTo(60);
        assertThat(profiles.get(withStats).profile().stats().kda()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(profiles.get(withoutStats).profile().gameNickname()).isEqualTo("two");
        assertThat(profiles.get(withoutStats).profile().stats()).isNull();
        // PUBG 계정만 있는 사람 — LOL 의 프로필은 없지만 닉네임은 온다
        assertThat(profiles.get(otherGameOnly).nickname()).isEqualTo(otherGameOnlyNickname);
        assertThat(profiles.get(otherGameOnly).profile()).isNull();

        assertThat(gameProfileReader.findProfiles(List.of(otherGameOnly), Game.PUBG)
                .get(otherGameOnly).profile().server()).isEqualTo("STEAM");
        assertThat(gameProfileReader.findProfiles(List.of(), Game.LOL)).isEmpty();
    }

    @Test
    @DisplayName("users/me — 소셜 연결이 없는 사용자는 socialProviders 가 빈 배열이다")
    void userWithoutSocialLinkHasNoSocialProviders() throws Exception
    {
        Cookie cookie = login(newNickname());

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.socialProviders").isArray())
                .andExpect(jsonPath("$.socialProviders").isEmpty());
    }

    private Long gameAccountId(Long userId, String game)
    {
        return jdbcTemplate.queryForObject(
                "select id from game_accounts where user_id = ? and game = ?", Long.class, userId, game);
    }

    /**
     * 게임사 API 가 채웠다고 치고 전적 줄을 직접 넣는다. 판 수({@code games})만 늘 있고 <b>승 · 패 · 연승 · 평균값은 {@code null} 일 수 있다</b>
     * — PUBG 는 승/패도 연승도 어시스트도 없다(2026-09-22 소유자 결정).
     */
    private void insertStats(Long gameAccountId, int games, Integer wins, Integer losses, String kills, String deaths,
                             String assists, Integer winStreak, String detailJson)
    {
        jdbcTemplate.update("insert into game_account_stats "
                        + "(game_account_id, games, wins, losses, avg_kills, avg_deaths, avg_assists, win_streak, detail, source, synced_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'API', now())",
                gameAccountId, games, wins, losses, decimal(kills), decimal(deaths), decimal(assists), winStreak, detailJson);
    }

    private static BigDecimal decimal(String value)
    {
        return value == null ? null : new BigDecimal(value);
    }

    private ResultActions putGameAccount(Cookie cookie, String game, String body) throws Exception
    {
        return mockMvc.perform(put("/api/v1/users/me/game-accounts/" + game).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
