package com.queuemate.chat.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.List;

/**
 * access 토큰을 <b>쿠키 {@code qm_access} 에서</b> 꺼낸다. {@code Authorization} 헤더 · 쿼리 파라미터는 받지 않는다 —
 * 브라우저의 {@code WebSocket} 은 헤더를 못 붙이지만 같은 출처의 쿠키는 핸드셰이크에 자동으로 싣는다({@code ../platform/CLAUDE.md} §5.1 (바)).
 *
 * <p><b>API · 소켓이 아닌 경로에서는 쿠키가 있어도 집지 않는다</b>({@code platform} · {@code notification} 의 같은 이름 클래스와 같은 규칙).
 * 집으면 만료된 토큰을 든 브라우저가 인증이 필요 없는 경로({@code /health/**})에서도 401 을 받는다.
 * <b>{@code notification} 과 다른 것 하나</b> — 소켓 경로 {@code /ws/} 도 집는다. 핸드셰이크가 {@code /ws/chat/…} 로 오기 때문이다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    static final String API_PREFIX = "/api/v1/";
    static final String WS_PREFIX = "/ws/";
    private static final List<String> TOKEN_PATH_PREFIXES = List.of(API_PREFIX, WS_PREFIX);

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (TOKEN_PATH_PREFIXES.stream().noneMatch(path::startsWith)) {
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
