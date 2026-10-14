package com.queuemate.chat.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 — 쿠키 {@code qm_access} 에서 꺼내는지, 무엇을 거절하는지({@code notification} 의 같은 이름 테스트를 옮겼다). 거절은 전부 같은 401
 * {@code UNAUTHENTICATED} 다. 인증 경로는 테스트용 발판 {@code GET /api/v1/chat/ping}({@code web/PingController} — 토큰의 {@code sub} 를 돌려준다)과
 * 아직 핸들러가 없는 소켓 경로 {@code /ws/chat/dm} 로 본다.
 *
 * <p><b>Redis 없이 돈다</b> — 이 테스트는 Redis 를 건드리지 않고 Lettuce 는 연결을 늦게 연다(2026-10-09 Docker 를 내린 채 확인).
 * 소켓 · Streams 코드가 들어오면 그쪽 테스트는 {@code REDIS_PORT} 로 6380 을 넘긴다(6379 는 다른 프로젝트 것이다).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestTokens.register(registry);
    }

    @Test
    @DisplayName("쿠키가 없으면 401 UNAUTHENTICATED 이고 에러 본문은 platform 과 같은 모양이다")
    void 쿠키가_없으면_401() throws Exception {
        mockMvc.perform(ping())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    @DisplayName("쿠키가 있으면 토큰의 sub 가 \"나\" 다 — ?userId= 는 없고 줘도 보지 않는다")
    void 쿠키의_sub_가_나다() throws Exception {
        mockMvc.perform(ping().param("userId", "43").cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("42"));
    }

    @Test
    @DisplayName("옛 ?userId= 만으로는 401 이다")
    void userId_파라미터만으로는_401() throws Exception {
        expectUnauthenticated(ping().param("userId", "42"));
    }

    @Test
    @DisplayName("Authorization 헤더의 Bearer 토큰은 받지 않는다 — 쿠키로만 온다")
    void 헤더의_토큰은_받지_않는다() throws Exception {
        String valid = TestTokens.token(claims -> { });
        expectUnauthenticated(ping().header(HttpHeaders.AUTHORIZATION, "Bearer " + valid));
    }

    @Test
    @DisplayName("JWT 가 아닌 쿠키는 401")
    void 깨진_쿠키는_401() throws Exception {
        expectUnauthenticated(ping().cookie(new Cookie(TokenClaims.ACCESS_COOKIE, "not-a-jwt")));
    }

    @Test
    @DisplayName("만료된 토큰은 401")
    void 만료된_토큰은_401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.issuedAt(past).expiresAt(past.plusSeconds(900)))));
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 401")
    void 서명이_틀리면_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.forgedToken("42")));
    }

    @Test
    @DisplayName("iss 가 queuemate-platform 이 아니면 401")
    void iss_가_다르면_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.issuer("someone-else"))));
    }

    @Test
    @DisplayName("token_use 가 social_signup 이면 401 — 같은 키로 서명된 소셜 가입 대기 토큰으로는 못 들어온다")
    void 소셜_가입_대기_토큰은_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.claim(TokenClaims.TOKEN_USE, "social_signup"))));
    }

    @Test
    @DisplayName("token_use 가 없으면 401")
    void token_use_가_없으면_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.claims(map -> map.remove(TokenClaims.TOKEN_USE)))));
    }

    @Test
    @DisplayName("sub 가 사용자 번호(숫자 문자열)가 아니면 401 — DM 키에 들어가는 값이다")
    void sub_가_숫자가_아니면_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.subject("u1"))));
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.subject("42:x"))));
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.subject("12345678901234567890"))));
    }

    @Test
    @DisplayName("소켓 경로 /ws/** 도 인증이다 — 쿠키가 없으면 401, 쿠키가 있으면 401 이 아니다(핸들러는 아직 없어 404)")
    void 소켓_경로도_쿠키를_본다() throws Exception {
        mockMvc.perform(get("/ws/chat/dm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/ws/chat/dm").cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("/health 는 인증을 요구하지 않는다 — 만료된 쿠키를 들고 와도 API 가 아닌 경로에서는 집지 않는다")
    void health_는_인증_없이_열려_있다() throws Exception {
        mockMvc.perform(get("/health/live")).andExpect(status().isOk());
        Instant past = Instant.now().minusSeconds(3600);
        String expired = TestTokens.token(claims -> claims.issuedAt(past).expiresAt(past.plusSeconds(900)));
        mockMvc.perform(get("/health/live").cookie(new Cookie(TokenClaims.ACCESS_COOKIE, expired)))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder ping() {
        return get("/api/v1/chat/ping").accept(MediaType.APPLICATION_JSON);
    }

    private static MockHttpServletRequestBuilder withToken(String token) {
        return ping().cookie(new Cookie(TokenClaims.ACCESS_COOKIE, token));
    }

    private void expectUnauthenticated(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
