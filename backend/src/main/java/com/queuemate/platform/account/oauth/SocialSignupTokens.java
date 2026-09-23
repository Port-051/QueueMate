package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import com.queuemate.platform.common.security.JwtKeys;
import com.queuemate.platform.common.security.JwtProperties;
import com.queuemate.platform.common.security.TokenClaims;
import com.queuemate.platform.common.web.WebSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 소셜로 <b>처음</b> 온 사람이 아이디 · 닉네임을 정할 때까지 들고 있는 토큰 — 쿠키 {@code qm_social_signup}
 * ({@code contracts/platform-api.md} "소셜 로그인"). "제공자가 이 회원 번호를 확인해 줬다"를 서버가 기억하는 대신 서명해서 브라우저에 맡긴다 —
 * stateless 그대로다.
 *
 * <p>access 토큰과 <b>같은 키로 서명한다.</b> 그래서 {@code token_use} 로 가른다 — 이 검증기는 {@code social_signup} 만 받고,
 * access 토큰의 검증기({@code JwtConfig#jwtDecoder})는 {@code access} 만 받는다. 어느 쪽도 다른 쪽의 토큰으로 통하지 않는다.
 *
 * <p>검증기를 빈으로 내놓지 않는다 — {@code JwtDecoder} 빈이 둘이면 보안 설정이 어느 것을 쓸지 헷갈린다. 이 클래스 안에서만 쓴다.
 */
@Slf4j
@Component
public class SocialSignupTokens {

    public static final String COOKIE = "qm_social_signup";
    /** {@code pending} · {@code signup} 둘 다 이 아래다. 다른 요청에는 실려 가지 않는다 */
    static final String COOKIE_PATH = "/api/v1/auth/social";
    static final Duration TTL = Duration.ofMinutes(10);

    static final String CLAIM_PROVIDER = "provider";
    static final String CLAIM_PROVIDER_USER_ID = "provider_user_id";
    static final String CLAIM_SUGGESTED_NICKNAME = "suggested_nickname";
    /** 닉네임의 길이 제한과 같다({@code SignupRequest}) — 미리 채운 값이 그대로 검증에 걸리지 않게 자른다 */
    static final int NICKNAME_MAX_LENGTH = 16;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder decoder;
    private final JwtProperties jwtProperties;
    private final WebSecurityProperties webSecurityProperties;

    public SocialSignupTokens(JwtEncoder jwtEncoder, JwtKeys keys, JwtProperties jwtProperties,
                              WebSecurityProperties webSecurityProperties)
    {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.webSecurityProperties = webSecurityProperties;

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TokenClaims.ISSUER),
                new JwtClaimValidator<String>(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_SOCIAL_SIGNUP::equals)));
        this.decoder = nimbusDecoder;
    }

    /** 가입을 기다리는 소셜 계정. {@code suggestedNickname} 은 없을 수 있다({@code null}) */
    public record Pending(SocialProvider provider, String providerUserId, String suggestedNickname) {
    }

    public String issue(SocialProvider provider, OAuthUser user)
    {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .build();
        // sub 가 없다 — 아직 사용자가 아니다
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_SOCIAL_SIGNUP)
                .claim(CLAIM_PROVIDER, provider.name())
                .claim(CLAIM_PROVIDER_USER_ID, user.providerUserId());
        String suggested = suggestedNickname(user.nickname());
        if(suggested != null)
        {
            claims.claim(CLAIM_SUGGESTED_NICKNAME, suggested);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /**
     * 쿠키의 값을 검증한다 — 서명 · {@code iss} · {@code exp} · <b>{@code token_use}</b>. 없거나 · 깨졌거나 · 만료됐거나 · 다른 쓰임새의 토큰이면
     * 전부 빈 값이다(왜 실패했는지는 로그에만 남긴다).
     */
    public Optional<Pending> verify(String token)
    {
        if(token == null || token.isBlank())
        {
            return Optional.empty();
        }
        try
        {
            Jwt jwt = decoder.decode(token);
            Optional<SocialProvider> provider = SocialProvider.fromName(jwt.getClaimAsString(CLAIM_PROVIDER));
            String providerUserId = jwt.getClaimAsString(CLAIM_PROVIDER_USER_ID);
            if(provider.isEmpty() || providerUserId == null || providerUserId.isBlank())
            {
                return Optional.empty();
            }
            return Optional.of(new Pending(provider.get(), providerUserId,
                    jwt.getClaimAsString(CLAIM_SUGGESTED_NICKNAME)));
        }
        catch(JwtException e)
        {
            log.debug("qm_social_signup 검증 실패 reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    public ResponseCookie cookie(String token)
    {
        return baseCookie(token, TTL);
    }

    /** 가입이 끝나면 지운다 — 같은 토큰으로 다시 가입하려 들어도 DB 의 PK 가 막지만, 쓸모없는 쿠키를 남기지 않는다 */
    public ResponseCookie expiredCookie()
    {
        return baseCookie("", Duration.ZERO);
    }

    /**
     * 제공자의 닉네임을 가입 화면이 미리 채울 값으로 다듬는다 — 앞뒤 공백을 걷고 16자로 자른다. 남는 것이 없으면 {@code null} 이다(클레임을 뺀다).
     * 제안일 뿐이다 — 검증은 가입 요청에서 한다(2자 미만이거나 남이 쓰는 닉네임일 수 있다).
     */
    static String suggestedNickname(String nickname)
    {
        if(nickname == null)
        {
            return null;
        }
        String trimmed = nickname.strip();
        if(trimmed.length() > NICKNAME_MAX_LENGTH)
        {
            int end = NICKNAME_MAX_LENGTH;
            // 이모지처럼 두 칸짜리 글자의 가운데를 자르지 않는다 — 반쪽이 남으면 JSON 으로 나갈 때 깨진 글자가 된다
            if(Character.isHighSurrogate(trimmed.charAt(end - 1)))
            {
                end--;
            }
            trimmed = trimmed.substring(0, end).strip();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseCookie baseCookie(String value, Duration maxAge)
    {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(webSecurityProperties.cookieSecure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
