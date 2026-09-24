package com.queuemate.platform.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>"로그인시킨다 = 이 쿠키들"</b>을 한 곳에서 정한다 — 로그인 · 소셜 로그인 · 소셜 가입 · 재발급이 모두 이것을 부른다.
 * 쿠키를 만드는 일 자체는 {@link AccessTokenIssuer} 와 {@link RefreshTokens} 가 한다.
 *
 * <p>여기 있는 이유는 하나다 — <b>refresh 는 없을 수 있다</b>(Redis 가 죽으면 저장하지 못한다). 그때도 로그인은 성공하고
 * access 쿠키만 나간다. 그 판단을 네 자리에 되풀이하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SessionCookies {

    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokens refreshTokens;

    /**
     * 이 사용자를 로그인시키는 {@code Set-Cookie} 값들 — access 하나와 refresh 하나. refresh 를 저장하지 못했으면 <b>access 하나만</b>이다
     * (그 사용자는 access 가 만료되면 다시 로그인해야 한다).
     */
    public List<String> login(long userId)
    {
        List<String> cookies = new ArrayList<>(2);
        cookies.add(accessTokenIssuer.cookie(accessTokenIssuer.issue(userId)).toString());
        refreshTokens.issue(userId).ifPresent(token -> cookies.add(refreshTokens.cookie(token).toString()));
        return List.copyOf(cookies);
    }

    /**
     * 로그아웃에 싣는, <b>두 쿠키를 지우는</b> {@code Set-Cookie} 값들. Redis 의 줄을 지우는 것은 {@link RefreshTokens#revoke} 다 —
     * 쿠키만 지우면 그 값이 여전히 재발급에 통한다.
     */
    public List<String> logout()
    {
        return List.of(accessTokenIssuer.expiredCookie().toString(), refreshTokens.expiredCookie().toString());
    }

    /** {@code Set-Cookie} 헤더에 그대로 넘길 배열 — {@code ResponseEntity} 의 {@code header(name, values…)} 가 가변인자다 */
    public static String[] array(List<String> cookies)
    {
        return cookies.toArray(String[]::new);
    }
}
