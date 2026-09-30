package com.queuemate.platform.common.web;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code Origin} 검사 — {@code contracts/platform-api.md} "공통". 허용 목록은 기본값({@code http://localhost:5173,http://localhost:3000})이다.
 */
class OriginCheckTest extends ApiTestSupport {

    @Test
    @DisplayName("다른 출처의 Origin 을 단 POST 는 403 ORIGIN_NOT_ALLOWED 이고 refresh 가 버려지지 않는다")
    void foreignOriginIsRejected() throws Exception
    {
        Cookie refresh = refreshCookieFor(newNickname());

        for(String origin : new String[]{"https://evil.example", "http://localhost:9999", "null"})
        {
            // 로그아웃은 인증 없이 부르는 POST 다 — 통과했다면 그 refresh 를 Redis 에서 지웠을 것이다
            mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, origin).cookie(refresh))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.details").isArray());
        }

        assertThat(refreshTokenStored(refresh.getValue())).isTrue();
    }

    @Test
    @DisplayName("허용된 Origin 과 Origin 이 없는 요청은 통과한다")
    void allowedOriginAndNoOriginPass() throws Exception
    {
        Cookie withOrigin = refreshCookieFor(newNickname());
        Cookie withoutOrigin = refreshCookieFor(newNickname());

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, "http://localhost:5173").cookie(withOrigin))
                .andExpect(status().isNoContent());
        assertThat(refreshTokenStored(withOrigin.getValue())).isFalse();
        // curl · 서버 사이의 요청에는 Origin 이 없다
        mockMvc.perform(post("/api/v1/auth/logout").cookie(withoutOrigin))
                .andExpect(status().isNoContent());
        assertThat(refreshTokenStored(withoutOrigin.getValue())).isFalse();
    }

    @Test
    @DisplayName("로그인한 사용자의 요청이어도 다른 출처면 403 이다. GET 은 보지 않는다")
    void checkedBeforeAuthenticationAndOnlyForStateChangingMethods() throws Exception
    {
        Cookie cookie = login(newNickname());

        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL").cookie(cookie)
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        // 쿠키가 없어도 401 이 아니라 403 이 먼저다 — 보안 필터보다 앞에서 돈다
        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL")
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden());
        // 상태를 바꾸는 GET 은 만들지 않는다 — 그래서 GET 은 검사하지 않는다
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie).header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isOk());
    }
}
