package com.queuemate.common.security;

import com.queuemate.common.logging.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthenticationFilterTest {

    private final JwtTokenService tokenService = new JwtTokenService(
            new AuthProperties("test-secret-key-that-is-long-enough-32b", 900, 1209600));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokenService);

    @AfterEach
    void clear() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void 인증에_성공하면_userId를_로그_컨텍스트에_넣는다() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer " + tokenService.issueAccessToken(userId));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(userId.toString(), MDC.get(MdcKeys.USER_ID));
    }

    @Test
    void 토큰이_잘못되면_userId를_남기지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader("Authorization", "Bearer not-a-real-token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(MDC.get(MdcKeys.USER_ID));
    }
}
