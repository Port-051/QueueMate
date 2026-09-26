package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.account.service.AuthService;
import com.queuemate.platform.common.security.AccessTokenIssuer;
import com.queuemate.platform.common.security.JwtProperties;
import com.queuemate.platform.common.security.RefreshTokens;
import com.queuemate.platform.common.security.SessionCookies;
import com.queuemate.platform.common.web.WebSecurityProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * refresh 토큰 — 재발급 · rotation · 로그아웃 ({@code contracts/platform-api.md} "refresh 토큰". 2026-09-23 소유자 결정).
 *
 * <p>access 는 15분이고 refresh 는 7일이다. <b>서버가 무효화할 수 있는 것은 refresh 쪽 하나다</b> — access 는 denylist 가 없어
 * 만료까지 서명이 유효하다(CLAUDE.md §5.1 (라)).
 *
 * <p>여기서 받은 refresh 값은 {@link ApiTestSupport#refreshCookieOf} 가 적어 뒀다가 테스트가 끝날 때 그 키만 지운다 —
 * {@code KEYS} · {@code FLUSHDB} 를 쓰지 않는다.
 */
class RefreshTokenApiTest extends ApiTestSupport {

    @Autowired
    JwtProperties jwtProperties;

    @Autowired
    AccessTokenIssuer accessTokenIssuer;

    @Autowired
    RefreshTokens refreshTokens;

    @Autowired
    WebSecurityProperties webSecurityProperties;

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("수명은 설정대로다 — access 15분 · refresh 7일(P7D 가 Duration 으로 바인딩된다)")
    void ttlsAreBound()
    {
        assertThat(jwtProperties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(jwtProperties.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
        // 비밀이 아니라 로그에 찍어도 된다 — 키만 빠져 있다
        assertThat(jwtProperties.toString()).contains("refreshTokenTtl=PT168H").doesNotContain("BEGIN");
    }

    @Test
    @DisplayName("로그인이 준 refresh 로 재발급하면 200 + 새 access · 새 refresh 이고, 그 access 로 users/me 를 읽는다")
    void refreshRotatesBothCookies() throws Exception
    {
        String nickname = newNickname();
        Cookie oldAccess = login(nickname);
        Cookie oldRefresh = refreshCookieFor(nickname);
        Long userId = userIdOf(nickname);
        // 값은 불투명한 UUID 다 — 사용자 정보가 들어 있지 않다(사용자 번호는 Redis 의 값에만 있다 — 아래)
        assertThat(UUID.fromString(oldRefresh.getValue())).isNotNull();
        assertThat(oldRefresh.getValue()).doesNotContain(nickname);
        // Redis 의 줄은 사용자 번호이고 수명이 7일이다
        assertThat(redisTemplate.opsForValue().get("qm:auth:refresh:" + oldRefresh.getValue()))
                .isEqualTo(String.valueOf(userId));
        assertThat(redisTemplate.getExpire("qm:auth:refresh:" + oldRefresh.getValue(), TimeUnit.SECONDS))
                .isBetween(Duration.ofDays(7).toSeconds() - 60, Duration.ofDays(7).toSeconds());

        // access 쿠키 없이 refresh 쿠키만으로 부른다 — 인증이 필요 없는 요청이다
        MvcResult refreshed = refresh(oldRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.equalTo(userId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nickname))
                // 토큰은 본문에 싣지 않는다 — 소셜 가입과 같은 본문이다
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        Cookie newAccess = refreshed.getResponse().getCookie("qm_access");
        Cookie newRefresh = refreshCookieOf(refreshed);
        assertThat(newAccess).isNotNull();
        assertThat(newRefresh).isNotNull();
        assertThat(newRefresh.getValue()).isNotEqualTo(oldRefresh.getValue());
        assertThat(newAccess.getValue()).isNotEqualTo(oldAccess.getValue());
        assertThat(newAccess.getMaxAge()).isEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(newRefresh.getMaxAge()).isEqualTo(Duration.ofDays(7).toSeconds());
        assertThat(newRefresh.getPath()).isEqualTo("/api/v1/auth/refresh");
        assertThat(newRefresh.isHttpOnly()).isTrue();
        assertThat(newRefresh.getAttribute("SameSite")).isEqualTo("Lax");

        // 새 access 는 진짜로 통한다
        mockMvc.perform(get("/api/v1/users/me").cookie(newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.equalTo(userId), Long.class));
        // 옛 refresh 는 Redis 에서 사라졌다(GETDEL) — 새것만 남는다
        assertThat(refreshTokenStored(oldRefresh.getValue())).isFalse();
        assertThat(refreshTokenStored(newRefresh.getValue())).isTrue();
    }

    @Test
    @DisplayName("같은 refresh 를 두 번 쓰면 두 번째는 401 이다 — rotation")
    void reuseIsRejected() throws Exception
    {
        Cookie refreshCookie = refreshCookieFor(newNickname());

        MvcResult first = refresh(refreshCookie).andExpect(status().isOk()).andReturn();
        refreshCookieOf(first);

        expectInvalidRefresh(refresh(refreshCookie));
    }

    @Test
    @DisplayName("쿠키가 없든 · 아무 문자열이든 · 이미 쓴 값이든 · 사용자가 사라졌든 글자까지 같은 401 INVALID_REFRESH_TOKEN 이고, 그때도 refresh 쿠키를 지운다")
    void everyFailureLooksTheSame() throws Exception
    {
        String nickname = newNickname();
        Cookie used = refreshCookieFor(nickname);
        refreshCookieOf(refresh(used).andExpect(status().isOk()).andReturn());

        // 계정이 사라진 사람의 refresh — 값은 Redis 에 멀쩡히 있다
        String deletedNickname = newNickname();
        Cookie orphaned = refreshCookieFor(deletedNickname);
        jdbcTemplate.update("delete from users where id = ?", userIdOf(deletedNickname));
        assertThat(refreshTokenStored(orphaned.getValue())).isTrue();

        List<MvcResult> failures = List.of(
                // 쿠키가 아예 없다
                mockMvc.perform(post("/api/v1/auth/refresh")).andReturn(),
                // UUID 꼴이 아닌 아무 문자열 — Redis 에 묻지도 않는다
                refresh(new Cookie("qm_refresh", "not-a-token")).andReturn(),
                // 꼴은 맞지만 아무도 받지 않은 값
                refresh(new Cookie("qm_refresh", UUID.randomUUID().toString())).andReturn(),
                // 이미 쓴 값
                refresh(used).andReturn(),
                // 사용자가 사라졌다
                refresh(orphaned).andReturn());

        String body = null;
        for(MvcResult failure : failures)
        {
            assertThat(failure.getResponse().getStatus()).isEqualTo(401);
            String content = failure.getResponse().getContentAsString();
            if(body == null)
            {
                body = content;
                assertThat(body).contains("\"code\":\"INVALID_REFRESH_TOKEN\"");
            }
            // 무엇이 틀렸는지 알려 주지 않는다 — 다섯 갈래가 글자까지 같다
            assertThat(content).isEqualTo(body);
            // 못 쓰는 값을 계속 들고 있게 두지 않는다. access 쿠키는 건드리지 않는다(아직 살아 있을 수 있다)
            String setCookie = failure.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).startsWith("qm_refresh=;")
                    .contains("Max-Age=0").contains("Path=/api/v1/auth/refresh")
                    .contains("HttpOnly").contains("SameSite=Lax");
            assertThat(failure.getResponse().getCookie("qm_access")).isNull();
        }
        // 값을 확인하려면 Redis 에서 읽어 지우는 수밖에 없다 — 사용자가 없어도 그 값은 버려진다(다시 쓸 수 없다)
        assertThat(refreshTokenStored(orphaned.getValue())).isFalse();
    }

    @Test
    @DisplayName("로그아웃하면 Redis 의 그 키가 없어지고 그 refresh 로는 재발급이 401 이다")
    void logoutRevokesRefresh() throws Exception
    {
        String nickname = newNickname();
        Cookie refreshCookie = refreshCookieFor(nickname);
        assertThat(refreshTokenStored(refreshCookie.getValue())).isTrue();

        mockMvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie)).andExpect(status().isNoContent());

        assertThat(refreshTokenStored(refreshCookie.getValue())).isFalse();
        expectInvalidRefresh(refresh(refreshCookie));
        // 다시 로그인하면 새 값이 나온다 — 계정이 잠긴 것이 아니다
        assertThat(refreshCookieFor(nickname).getValue()).isNotEqualTo(refreshCookie.getValue());
    }

    @Test
    @DisplayName("로그아웃은 남의 refresh 를 지우지 않는다 — 자기 쿠키에 담긴 값만 버린다")
    void logoutOnlyRevokesItsOwn() throws Exception
    {
        Cookie mine = refreshCookieFor(newNickname());
        Cookie other = refreshCookieFor(newNickname());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(mine)).andExpect(status().isNoContent());

        assertThat(refreshTokenStored(mine.getValue())).isFalse();
        assertThat(refreshTokenStored(other.getValue())).isTrue();
        refresh(other).andExpect(status().isOk()).andReturn();
    }

    @Test
    @DisplayName("Redis 를 못 쓰면 로그인은 성공하고(access 하나만) 재발급은 401 이다 — fail-closed")
    void redisDown() throws Exception
    {
        String nickname = newNickname();
        Cookie realRefresh = refreshCookieFor(nickname);
        Long userId = userIdOf(nickname);

        RefreshTokens broken = new RefreshTokens(brokenRedis(), jwtProperties, webSecurityProperties);

        // 로그인시키는 쿠키가 access 하나다 — 로그인 자체는 성공한다(예외가 밖으로 나오지 않는다)
        List<String> cookies = new SessionCookies(accessTokenIssuer, broken).login(userId);
        assertThat(cookies).hasSize(1);
        assertThat(cookies.get(0)).startsWith("qm_access=");
        assertThat(broken.issue(userId)).isEmpty();

        // 확인할 방법이 없는 값을 통과시키지 않는다. 멀쩡한 값이어도 거절한다
        AuthService service = new AuthService(userRepository, broken);
        assertThat(service.refresh(realRefresh.getValue())).isEmpty();
        assertThat(broken.consume(realRefresh.getValue())).isEmpty();
        // 못 읽었을 뿐이므로 값은 그대로 살아 있다 — Redis 가 돌아오면 그 refresh 로 다시 재발급할 수 있다
        assertThat(refreshTokenStored(realRefresh.getValue())).isTrue();
        Optional<AuthResponse> healthy = new AuthService(userRepository, refreshTokens).refresh(realRefresh.getValue());
        assertThat(healthy).isPresent();

        // 로그아웃은 Redis 가 죽어도 조용히 지나간다
        broken.revoke(UUID.randomUUID().toString());
    }

    /** {@code opsForValue} · {@code delete} 가 전부 터지는 {@link StringRedisTemplate} — {@code PostServiceRedisDownTest} 와 같은 방식이다 */
    private static StringRedisTemplate brokenRedis()
    {
        return new StringRedisTemplate() {
            @Override
            public ValueOperations<String, String> opsForValue()
            {
                throw new RedisConnectionFailureException("테스트 — Redis 가 죽었다");
            }

            @Override
            public Boolean delete(String key)
            {
                throw new RedisConnectionFailureException("테스트 — Redis 가 죽었다");
            }
        };
    }

    private ResultActions refresh(Cookie refreshCookie) throws Exception
    {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie));
    }

    private static void expectInvalidRefresh(ResultActions actions) throws Exception
    {
        actions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.details").isArray());
    }
}
