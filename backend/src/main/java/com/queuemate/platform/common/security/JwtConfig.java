package com.queuemate.platform.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.regex.Pattern;

/**
 * 서명(개인 키)과 검증(공개 키). RS256 이다 — 개인 키는 이 앱만 갖고 옆 서비스는 공개 키로 검증만 한다 (CLAUDE.md §5.1 (가)).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    public JwtKeys jwtKeys(JwtProperties properties)
    {
        return JwtKeys.load(properties);
    }

    /** 서명. 헤더의 {@code kid} 와 같은 id 의 키를 고른다 — 그래서 JWK 에도 같은 {@code kid} 를 단다 */
    @Bean
    public JwtEncoder jwtEncoder(JwtKeys keys, JwtProperties properties)
    {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .keyID(properties.keyId())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    private static final Pattern SUBJECT = Pattern.compile(TokenClaims.SUBJECT_PATTERN);

    /**
     * access 토큰의 검증 — 서명 · {@code exp} · {@code iss} 에 더해 <b>{@code token_use} 가 {@code access} 인지</b> 본다.
     * 소셜 가입 대기 토큰도 같은 키로 서명하므로 이것을 빼면 그 토큰이 access 토큰으로 통한다 ({@code contracts/platform-api.md}).
     * <b>{@code sub} 가 사용자 번호(숫자 문자열)인지도 본다</b> — 아니면 컨트롤러에 닿기 전에 401 이다. {@code @CurrentUserId} 는 그 값을 {@code Long} 으로 판다.
     *
     * <p>옆 서비스가 붙일 검증도 이 모양이다 — {@code NimbusJwtDecoder.withPublicKey()} + 같은 검증기.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeys keys)
    {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> tokenUseIsAccess = new JwtClaimValidator<String>(
                TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ACCESS::equals);
        OAuth2TokenValidator<Jwt> subjectIsUserNumber = new JwtClaimValidator<String>(
                JwtClaimNames.SUB, sub -> sub != null && SUBJECT.matcher(sub).matches());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TokenClaims.ISSUER), tokenUseIsAccess, subjectIsUserNumber));
        return decoder;
    }
}
