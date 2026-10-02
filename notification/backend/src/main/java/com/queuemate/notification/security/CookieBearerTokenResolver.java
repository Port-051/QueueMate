package com.queuemate.notification.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * access 토큰을 <b>쿠키 {@code qm_access} 에서</b> 꺼낸다. {@code Authorization} 헤더 · 쿼리 파라미터는 받지 않는다 —
 * {@code EventSource} 는 헤더를 못 붙이지만 같은 출처의 쿠키는 자동으로 싣는다({@code ../platform/CLAUDE.md} §5.1 (바)).
 *
 * <p><b>API 가 아닌 경로에서는 쿠키가 있어도 집지 않는다</b>({@code platform} 의 같은 이름 클래스와 같은 규칙). 집으면 만료된 토큰을 든
 * 브라우저가 인증이 필요 없는 경로에서도 401 을 받는다.
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
