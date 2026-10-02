package com.queuemate.common.security;

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
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.regex.Pattern;

/**
 * access 토큰의 검증. <b>공개 키로 검증만 한다</b> — 서명은 {@code platform} 만 한다 (docs/11 D-24 · RS256).
 * {@code platform} 의 {@code JwtConfig#jwtDecoder} 와 같은 모양이다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Pattern SUBJECT = Pattern.compile(TokenClaims.SUBJECT_PATTERN);

    /**
     * 서명(RS256) · {@code exp} · {@code iss} 에 더해 <b>{@code token_use == access}</b> 와 <b>{@code sub} 가 숫자 문자열</b>인지 본다.
     * 하나라도 어긋나면 컨트롤러에 닿기 전에 401 이다.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(JwtPublicKeys.load(properties))
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
