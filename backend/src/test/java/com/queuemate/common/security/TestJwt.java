package com.queuemate.common.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.servlet.http.Cookie;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 테스트용 키 쌍과 access 토큰. <b>테스트 JVM 이 뜰 때마다 새로 만든다</b> — 개인 키를 저장소에 두지 않는다.
 *
 * <p>공개 키는 {@link TestJwtKeyInitializer} 가 모든 {@code @SpringBootTest} 컨텍스트의 {@code queuemate.jwt.public-key} 로 넣는다
 * ({@code META-INF/spring.factories}). 토큰은 개인 키로 찍는다 — 운영에서 {@code platform} 이 찍는 것과 같은 모양이다
 * ({@code iss} · {@code sub} · {@code token_use} · {@code jti} · {@code exp}).
 */
public final class TestJwt {

    private static final KeyPair KEYS = generate();

    private TestJwt() {
    }

    public static String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(KEYS.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    /** 정상 access 토큰 — {@code sub} = userId */
    public static String accessToken(String userId) {
        return token(KEYS, claims -> claims.subject(userId));
    }

    /** 정상 access 토큰을 실은 쿠키 {@code qm_access} */
    public static Cookie cookie(String userId) {
        return new Cookie(TokenClaims.ACCESS_COOKIE, accessToken(userId));
    }

    /** 클레임을 바꾼 토큰 — 거절돼야 하는 토큰을 만들 때 쓴다 */
    public static String token(Consumer<JwtClaimsSet.Builder> customizer) {
        return token(KEYS, customizer);
    }

    /** 다른 키로 서명한 토큰 — 서명 검증이 거절해야 한다 */
    public static String tokenSignedByOtherKey(String userId) {
        return token(generate(), claims -> claims.subject(userId));
    }

    private static String token(KeyPair keys, Consumer<JwtClaimsSet.Builder> customizer) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
                .keyID("test-1")
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ACCESS);
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-1").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
