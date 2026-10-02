package com.queuemate.platform.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * access 토큰을 <b>쿠키 {@code qm_access} 에서</b> 꺼낸다. {@code Authorization} 헤더는 받지 않는다 (CLAUDE.md §5 · §5.1).
 *
 * <p><b>인증이 필요 없는 경로에서는 쿠키가 있어도 집지 않는다.</b> 집으면 보안 필터가 그 토큰을 검증하려 들고,
 * 만료되거나 깨진 토큰이면 {@code permitAll} 경로인데도 401 이 난다 — 토큰이 만료된 사용자가 다시 로그인하지 못하게 된다.
 * 그래서 {@code /api/v1/auth/**}(가입 · 로그인 · 로그아웃 · 소셜 로그인)와 API 가 아닌 경로({@code /health/**} · {@code /info})에서는 {@code null} 을 돌려준다.
 *
 * <p><b>예외 하나 — 회원 탈퇴 {@link #ACCOUNT_PATH}</b>(2026-10-02). refresh 쿠키가 실려 오게 {@code /api/v1/auth} 아래에 뒀지만 "나" 를 알아야 하는 요청이라
 * 쿠키를 집는다. 보안 설정({@code SecurityConfig})이 같은 경로를 {@code authenticated()} 로 잡는다 — 둘이 같은 금이어야 한다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    static final String API_PREFIX = "/api/v1/";
    static final String AUTH_PREFIX = "/api/v1/auth/";

    /** 회원 탈퇴 — {@code /api/v1/auth/**} 가운데 access 토큰이 있어야 하는 하나(2026-10-02 · P-48). 보안 설정도 이 상수를 쓴다 */
    static final String ACCOUNT_PATH = "/api/v1/auth/account";

    @Override
    public String resolve(HttpServletRequest request)
    {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if(!path.startsWith(API_PREFIX) || (path.startsWith(AUTH_PREFIX) && !path.equals(ACCOUNT_PATH)))
        {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if(cookies == null)
        {
            return null;
        }
        for(Cookie cookie : cookies)
        {
            if(TokenClaims.ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank())
            {
                return cookie.getValue();
            }
        }
        return null;
    }
}
