package com.queuemate.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * access 토큰을 <b>쿠키 {@code qm_access} 에서</b> 꺼낸다. {@code Authorization} 헤더는 받지 않는다 (docs/11 D-14 · D-24).
 *
 * <p><b>{@code /api/v1/} 밖의 경로(actuator 의 {@code /health/**} · {@code /info} · {@code /metrics/**})에서는 쿠키가 있어도 집지 않는다.</b>
 * 집으면 보안 필터가 그 토큰을 검증하려 들고, 만료된 쿠키를 든 브라우저가 {@code permitAll} 경로에서도 401 을 받는다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    static final String API_PREFIX = "/api/v1/";

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith(API_PREFIX)) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (TokenClaims.ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
