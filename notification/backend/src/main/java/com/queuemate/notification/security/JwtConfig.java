package com.queuemate.notification.security;

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
 * access 토큰의 검증 — {@code platform} 의 {@code JwtConfig#jwtDecoder} 와 같은 모양이다. <b>검증만 한다</b>(서명은 {@code platform} 만).
 *
 * <p>서명(RS256) · {@code exp} · {@code iss} 에 더해 <b>{@code token_use} 가 {@code access} 인지</b> 본다 — 소셜 가입 대기 토큰도 같은 키로
 * 서명되므로 이것을 빼면 그 토큰으로 연결이 열린다. <b>{@code sub} 가 사용자 번호(숫자 문자열)인지도 본다</b> — 그 값이 알림 채널 이름이 된다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Pattern SUBJECT = Pattern.compile(TokenClaims.SUBJECT_PATTERN);

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(JwtPublicKey.load(properties))
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
