package com.queuemate.notification.security;

import com.queuemate.notification.sse.SseConnections;
import com.queuemate.notification.subscription.UserChannelSubscriber;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 연결의 인증 — 쿠키 {@code qm_access} 에서 꺼내는지, 무엇을 거절하는지. 거절은 전부 같은 401 {@code UNAUTHENTICATED} 이고
 * <b>거절되면 구독이 걸리지 않는다.</b> Redis 없이 돈다({@link UserChannelSubscriber} 를 mock 으로 바꾼다 — {@code EventStreamControllerTest} 와 같은 컨텍스트).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SseConnections connections;
    @MockitoBean
    private UserChannelSubscriber subscriber;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestTokens.register(registry);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void 연결을_비운다() {
        ((Map<String, ?>) ReflectionTestUtils.getField(connections, "connections")).clear();
    }

    @Test
    @DisplayName("쿠키가 없으면 401 UNAUTHENTICATED 이고 에러 본문은 platform 과 같은 모양이다")
    void 쿠키가_없으면_401() throws Exception {
        mockMvc.perform(events())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isEmpty());
        verify(subscriber, never()).subscribe(anyString());
    }

    @Test
    @DisplayName("옛 ?userId= 는 무시한다 — 쿠키가 없으면 401 이다")
    void 옛_userId_파라미터만으로는_401() throws Exception {
        expectUnauthenticated(events().param("userId", "42"));
    }

    @Test
    @DisplayName("쿠키가 있으면 ?userId= 가 달라도 토큰의 sub 로 연다")
    void userId_파라미터는_sub_를_이기지_못한다() throws Exception {
        mockMvc.perform(events().param("userId", "43").cookie(TestTokens.accessCookie("42")))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
        verify(subscriber).subscribe("42");
        verify(subscriber, never()).subscribe("43");
    }

    @Test
    @DisplayName("Authorization 헤더의 Bearer 토큰은 받지 않는다 — 쿠키로만 온다")
    void 헤더의_토큰은_받지_않는다() throws Exception {
        String valid = TestTokens.token(claims -> { });
        expectUnauthenticated(events().header(HttpHeaders.AUTHORIZATION, "Bearer " + valid));
    }

    @Test
    @DisplayName("JWT 가 아닌 쿠키는 401")
    void 깨진_쿠키는_401() throws Exception {
        expectUnauthenticated(events().cookie(new Cookie(TokenClaims.ACCESS_COOKIE, "not-a-jwt")));
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
    @DisplayName("token_use 가 social_signup 이면 401 — 같은 키로 서명된 소셜 가입 대기 토큰으로는 못 연다")
    void 소셜_가입_대기_토큰은_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.claim(TokenClaims.TOKEN_USE, "social_signup"))));
    }

    @Test
    @DisplayName("token_use 가 없으면 401")
    void token_use_가_없으면_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.claims(map -> map.remove(TokenClaims.TOKEN_USE)))));
    }

    @Test
    @DisplayName("sub 가 사용자 번호(숫자 문자열)가 아니면 401 — 채널 이름이 되는 값이다")
    void sub_가_숫자가_아니면_401() throws Exception {
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.subject("u1"))));
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.subject("42:x"))));
        expectUnauthenticated(withToken(TestTokens.token(claims -> claims.subject("12345678901234567890"))));
    }

    @Test
    @DisplayName("/health 는 인증을 요구하지 않는다 — 401 이 아니라 200 이다(경로 자체는 HealthEndpointTest 가 본다)")
    void health_는_인증_없이_열려_있다() throws Exception {
        mockMvc.perform(get("/health/live")).andExpect(status().isOk());
        // 만료된 쿠키를 들고 와도 API 가 아닌 경로에서는 집지 않는다
        Instant past = Instant.now().minusSeconds(3600);
        String expired = TestTokens.token(claims -> claims.issuedAt(past).expiresAt(past.plusSeconds(900)));
        mockMvc.perform(get("/health/live").cookie(new Cookie(TokenClaims.ACCESS_COOKIE, expired)))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder events() {
        return get("/api/v1/events").accept(MediaType.TEXT_EVENT_STREAM);
    }

    private static MockHttpServletRequestBuilder withToken(String token) {
        return events().cookie(new Cookie(TokenClaims.ACCESS_COOKIE, token));
    }

    private void expectUnauthenticated(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verify(subscriber, never()).subscribe(anyString());
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(connections, "connections")).isEmpty();
    }
}
