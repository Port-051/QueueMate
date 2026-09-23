package com.queuemate.platform.common.security;

import com.queuemate.platform.common.web.WebSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * access 토큰을 찍고 쿠키에 담는다. 클레임과 쿠키 속성은 {@code contracts/platform-api.md} "access 토큰" 표 그대로다.
 *
 * <p>클레임은 {@code iss · sub · iat · exp · jti · token_use} 여섯 개<b>뿐</b>이다. 닉네임처럼 바뀌는 값을 싣지 않는다 —
 * 토큰이 살아 있는 동안 낡은 값이 돌아다닌다 (CLAUDE.md §5.1 (가)).
 */
@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final WebSecurityProperties webSecurityProperties;

    /** @param userId 사용자 번호({@code account.users.id}). {@code sub} 에는 숫자를 문자열로 찍는다 — JWT 의 {@code sub} 는 문자열이다 */
    public String issue(Long userId)
    {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                // denylist 를 두게 될 때 쓸 자리다. 지금은 아무도 읽지 않는다 (CLAUDE.md §5.1 (라))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ACCESS)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** 로그인 응답에 싣는 쿠키. 수명은 토큰 수명과 같다 */
    public ResponseCookie cookie(String token)
    {
        return baseCookie(token, jwtProperties.accessTokenTtl());
    }

    /** 로그아웃 응답에 싣는 쿠키 — {@code Max-Age=0} 이면 브라우저가 지운다. 속성이 발급 때와 같아야 같은 쿠키로 친다 */
    public ResponseCookie expiredCookie()
    {
        return baseCookie("", Duration.ZERO);
    }

    private ResponseCookie baseCookie(String value, Duration maxAge)
    {
        // Domain 은 지정하지 않는다(host-only) — 네 서비스가 한 도메인 아래에 있다 (CLAUDE.md §5.1 (나))
        return ResponseCookie.from(TokenClaims.ACCESS_COOKIE, value)
                .httpOnly(true)
                .secure(webSecurityProperties.cookieSecure())
                // Strict 면 외부 링크로 들어온 첫 화면이 로그아웃 상태로 보인다
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
