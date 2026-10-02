package com.queuemate.platform.account.oauth;

import com.queuemate.platform.common.web.WebSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 인가 요청의 {@code state} — 콜백이 "내가 시작한 흐름의 끝"인지 가리는 값이다. 콜백은 GET 이라 {@code Origin} 검사가 닿지 않는다 —
 * <b>이 값의 대조가 그 자리를 지킨다</b> ({@code contracts/platform-api.md} "소셜 로그인").
 *
 * <p>서버에 기억하지 않고 쿠키 {@code qm_oauth_state} 에 둔다(stateless). {@code SameSite=Lax} 라서 제공자에서 돌아오는 최상위 이동(GET)에는 실려 온다.
 */
@Component
@RequiredArgsConstructor
public class OAuthStateCookie {

    public static final String NAME = "qm_oauth_state";
    /** start · callback 둘 다 이 아래다. 다른 요청에는 실려 가지 않는다 */
    static final String PATH = "/api/v1/auth/oauth";
    static final Duration TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final WebSecurityProperties webSecurityProperties;

    /** 추측할 수 없는 값 — 32바이트를 base64url 로(패딩 없이). 쿠키와 쿼리 어디에 실어도 글자가 깨지지 않는다 */
    public String newState()
    {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public ResponseCookie cookie(String state)
    {
        return baseCookie(state, TTL);
    }

    /** 콜백에서 쓴 뒤 지운다 — 한 번 쓴 {@code state} 가 브라우저에 남아 있을 이유가 없다 */
    public ResponseCookie expiredCookie()
    {
        return baseCookie("", Duration.ZERO);
    }

    /** <b>상수 시간 비교다</b> — 앞에서부터 몇 글자가 맞았는지가 응답 시간으로 새지 않는다. 어느 쪽이든 비어 있으면 다르다 */
    public boolean matches(String fromCookie, String fromQuery)
    {
        if(fromCookie == null || fromCookie.isBlank() || fromQuery == null || fromQuery.isBlank())
        {
            return false;
        }
        return MessageDigest.isEqual(fromCookie.getBytes(StandardCharsets.UTF_8),
                fromQuery.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseCookie baseCookie(String value, Duration maxAge)
    {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(webSecurityProperties.cookieSecure())
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
