package com.queuemate.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** docs/11 결정 16번: access + refresh JWT를 발급/검증한다. */
@Service
public class JwtTokenService {

    private static final String CLAIM_TOKEN_TYPE = "typ";

    private final SecretKey key;
    private final AuthProperties properties;

    public JwtTokenService(AuthProperties properties) {
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("queuemate.auth.jwt-secret는 32 bytes 이상이어야 한다");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.properties = properties;
    }

    public String issueAccessToken(UUID userId) {
        return issue(userId, TokenType.ACCESS, properties.accessTokenTtl());
    }

    public String issueRefreshToken(UUID userId) {
        return issue(userId, TokenType.REFRESH, properties.refreshTokenTtl());
    }

    /**
     * 토큰을 검증하고 주체를 돌려준다.
     * 서명/만료/타입 중 하나라도 어긋나면 예외를 던진다. 실패를 삼키지 않는다.
     */
    public UUID parseSubject(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            if (!expectedType.name().equals(actualType)) {
                throw new InvalidTokenException("토큰 타입이 " + expectedType + "가 아니다");
            }
            return UUID.fromString(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 토큰", e);
        }
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }

    private String issue(UUID userId, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                // iat/exp는 초 단위라 같은 초에 발급하면 payload가 동일해진다.
                // jti가 없으면 rotation이 같은 토큰을 재발급해 재사용 탐지가 뚫린다.
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
