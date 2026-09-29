package com.queuemate.platform.account.stats;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>{@code RIOT_API_KEY} 가 없을 때</b> — 기동은 정상이고 <b>긁는 일 자체를 하지 않는다.</b>
 * 기본 컨텍스트에서 본다(설정을 바꾸지 않는다 — {@code application.yaml} 의 기본값이 빈 키다). 키가 있을 때는 {@link GameStatsSyncTest} 다.
 *
 * <p>가짜 Riot 서버조차 띄우지 않는다 — 부르러 나갔다면 진짜 {@code riotgames.com} 으로 나갔을 것이다.
 * 여기서 보는 것은 <b>전용 풀에 일이 들어가지도 않는다</b>는 것이다.
 *
 * <p><b>LoL 게임 계정 연결은 503 이고 아무것도 저장하지 않는다</b>(2026-09-27 소유자 결정 — LoL 은 Riot 을 긁어야 연결된다).
 * <b>PUBG 도 같다</b>({@code PUBG_API_KEY} — 2026-09-29 소유자 결정 · P-36). VALORANT 는 자기신고라 키와 상관없이 된다.
 * <b>사용자가 누르는 전적 갱신</b>({@code POST …/game-accounts/{game}/refresh} — 2026-09-24)도 <b>503</b> 이고 쿨타임을 소모하지 않는다.
 */
class GameStatsNotConfiguredTest extends ApiTestSupport {

    @Autowired
    @Qualifier(GameStatsAsyncConfig.EXECUTOR)
    Executor gameStatsExecutor;

    @Test
    @DisplayName("키가 없으면 LoL 게임 계정 연결은 503 GAME_STATS_UNAVAILABLE 이고 저장하지 않는다 — VALORANT 는 그대로 된다")
    void lolLinkFailsWithoutApiKey() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);

        mockMvc.perform(put("/api/v1/users/me/game-accounts/LOL").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gameNickname", "달콤한 인생#KR7")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));

        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsExecutor;
        assertThat(pool.getThreadPoolExecutor().getTaskCount()).as("전용 풀에 일이 들어가지 않았다").isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_accounts where user_id = ?", Integer.class, userIdOf(nickname))).isZero();

        mockMvc.perform(put("/api/v1/users/me/game-accounts/VALORANT").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gameNickname", "제트#KR1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats").isEmpty());
        assertThat(pool.getThreadPoolExecutor().getTaskCount()).isZero();
    }

    @Test
    @DisplayName("PUBG_API_KEY 가 없으면 PUBG 게임 계정 연결 · 전적 갱신이 503 GAME_STATS_UNAVAILABLE 이고 저장 · 쿨타임이 없다")
    void pubgFailsWithoutApiKey() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);

        mockMvc.perform(put("/api/v1/users/me/game-accounts/PUBG").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gameNickname", "chicken", "server", "STEAM")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_accounts where user_id = ?", Integer.class, userIdOf(nickname))).isZero();

        // 키가 있던 때 연결해 둔 계정이라고 친다
        Long gameAccountId = insertGameAccount(userIdOf(nickname), "PUBG", "chicken", "GOLD_1");
        mockMvc.perform(post("/api/v1/users/me/game-accounts/PUBG/refresh").cookie(cookie))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));
        assertThat(redisTemplate.hasKey(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + gameAccountId)).isFalse();
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsExecutor;
        assertThat(pool.getThreadPoolExecutor().getTaskCount()).as("전용 풀에 일이 들어가지 않았다").isZero();
    }

    @Test
    @DisplayName("키가 없으면 전적 갱신 요청은 503 이다 — 200 을 주면 거짓말이고, 쿨타임도 소모하지 않는다")
    void refreshFailsWithoutApiKey() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        // 키가 있던 때 연결해 둔 계정이라고 친다 — 지금은 API 로 LoL 계정을 만들 수 없다
        Long gameAccountId = insertGameAccount(userIdOf(nickname), "LOL", "달콤한 인생#KR7", null);

        mockMvc.perform(post("/api/v1/users/me/game-accounts/LOL/refresh").cookie(cookie))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));

        assertThat(redisTemplate.hasKey(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + gameAccountId)).isFalse();
        List<?> stats = jdbcTemplate.queryForList(
                "select 1 from game_account_stats where game_account_id = ?", gameAccountId);
        assertThat(stats).isEmpty();
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsExecutor;
        assertThat(pool.getThreadPoolExecutor().getTaskCount()).as("전용 풀에 일이 들어가지 않았다").isZero();
    }
}
