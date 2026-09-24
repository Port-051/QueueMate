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
 * <b>{@code RIOT_API_KEY} 가 없을 때</b> — 기동은 정상이고 <b>긁는 일 자체를 하지 않는다</b>({@code stats} 가 {@code null} 로 남는다).
 * 기본 컨텍스트에서 본다(설정을 바꾸지 않는다 — {@code application.yaml} 의 기본값이 빈 키다). 키가 있을 때는 {@link GameStatsSyncTest} 다.
 *
 * <p>가짜 Riot 서버조차 띄우지 않는다 — 부르러 나갔다면 진짜 {@code riotgames.com} 으로 나갔을 것이고, 그러면 {@code stats} 가 채워지지도 않는다.
 * 여기서 보는 것은 <b>전용 풀에 일이 들어가지도 않는다</b>는 것이다.
 *
 * <p><b>사용자가 누르는 전적 갱신</b>({@code POST …/game-accounts/{game}/refresh} — 2026-09-24)은 키가 없을 때 <b>503</b> 이다.
 * 아무것도 갱신되지 않았으니 200 을 줄 수 없고, 그 사람이 할 수 있는 일도 아니라 쿨타임을 소모하지 않는다.
 */
class GameStatsNotConfiguredTest extends ApiTestSupport {

    @Autowired
    @Qualifier(GameStatsAsyncConfig.EXECUTOR)
    Executor gameStatsExecutor;

    @Test
    @DisplayName("키가 없으면 전적을 긁지 않는다 — stats 와 external_id 가 그대로 비어 있다")
    void doesNothingWithoutApiKey() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);

        mockMvc.perform(put("/api/v1/users/me/game-accounts/LOL").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gameNickname", "달콤한 인생#KR7", "tier", "EMERALD_4", "mainPosition", "MID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats").isEmpty());

        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsExecutor;
        assertThat(pool.getThreadPoolExecutor().getTaskCount()).as("전용 풀에 일이 들어가지 않았다").isZero();

        Long gameAccountId = jdbcTemplate.queryForObject(
                "select id from account.game_accounts where user_id = ? and game = 'LOL'", Long.class, userIdOf(loginId));
        List<?> stats = jdbcTemplate.queryForList(
                "select 1 from account.game_account_stats where game_account_id = ?", gameAccountId);
        assertThat(stats).isEmpty();
        assertThat(jdbcTemplate.queryForMap("select * from account.game_accounts where id = ?", gameAccountId))
                .containsEntry("external_id", null)
                .containsEntry("verified", false);
    }

    @Test
    @DisplayName("키가 없으면 전적 갱신 요청은 503 이다 — 200 을 주면 거짓말이고, 쿨타임도 소모하지 않는다")
    void refreshFailsWithoutApiKey() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);
        mockMvc.perform(put("/api/v1/users/me/game-accounts/LOL").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("gameNickname", "달콤한 인생#KR7")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/me/game-accounts/LOL/refresh").cookie(cookie))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GAME_STATS_UNAVAILABLE"));

        Long gameAccountId = jdbcTemplate.queryForObject(
                "select id from account.game_accounts where user_id = ? and game = 'LOL'", Long.class, userIdOf(loginId));
        assertThat(redisTemplate.hasKey(GameStatsRefreshCooldown.REFRESH_KEY_PREFIX + gameAccountId)).isFalse();
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) gameStatsExecutor;
        assertThat(pool.getThreadPoolExecutor().getTaskCount()).as("전용 풀에 일이 들어가지 않았다").isZero();
    }
}
