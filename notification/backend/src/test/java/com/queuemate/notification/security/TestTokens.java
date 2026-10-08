package com.queuemate.notification.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.servlet.http.Cookie;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 테스트용 토큰. <b>테스트가 도는 동안만 있는 키 쌍</b>을 만들어 공개 키는 앱에 넣고({@link #register}), 개인 키로 {@code platform} 과
 * 같은 모양의 access 토큰을 찍는다. 개인 키는 이 클래스 밖으로 나가지 않고 저장소에도 없다 — 앱 코드는 여전히 공개 키만 받는다.
 */
public final class TestTokens {

    private static final KeyPair KEYS = newKeyPair();
    private static final JwtEncoder ENCODER = encoder(KEYS);

    private TestTokens() {
    }

    /** {@code @SpringBootTest} 마다 {@code @DynamicPropertySource} 에서 부른다 — 이 공개 키로 검증하게 한다 */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("queuemate.jwt.public-key", () -> toPem((RSAPublicKey) KEYS.getPublic()));
    }

    /** 사용자 번호 {@code userId} 의 멀쩡한 access 토큰을 담은 쿠키 {@code qm_access} */
    public static Cookie accessCookie(String userId) {
        return new Cookie(TokenClaims.ACCESS_COOKIE, token(ENCODER, claims -> claims.subject(userId)));
    }

    /** 멀쩡한 access 토큰에서 {@code customizer} 가 바꾼 것만 다른 토큰(이 테스트의 키로 서명) */
    public static String token(Consumer<JwtClaimsSet.Builder> customizer) {
        return token(ENCODER, customizer);
    }

    /** 클레임은 멀쩡하지만 <b>다른 키</b>로 서명한 토큰 */
    public static String forgedToken(String userId) {
        return token(encoder(newKeyPair()), claims -> claims.subject(userId));
    }

    private static String token(JwtEncoder encoder, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .subject("42")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ACCESS);
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-1").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static JwtEncoder encoder(KeyPair pair) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey(pair.getPrivate())
                .keyID("test-1")
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String toPem(RSAPublicKey key) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }
}
