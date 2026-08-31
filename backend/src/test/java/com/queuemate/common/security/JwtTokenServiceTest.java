package com.queuemate.common.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-32b";

    private final JwtTokenService tokenService =
            new JwtTokenService(new AuthProperties(SECRET, 900, 1209600));

    @Test
    void issuesAndParsesAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenService.issueAccessToken(userId);

        assertEquals(userId, tokenService.parseSubject(token, TokenType.ACCESS));
    }

    @Test
    void rejectsAccessTokenUsedAsRefreshToken() {
        String access = tokenService.issueAccessToken(UUID.randomUUID());

        assertThrows(InvalidTokenException.class,
                () -> tokenService.parseSubject(access, TokenType.REFRESH));
    }

    @Test
    void rejectsRefreshTokenUsedAsAccessToken() {
        String refresh = tokenService.issueRefreshToken(UUID.randomUUID());

        assertThrows(InvalidTokenException.class,
                () -> tokenService.parseSubject(refresh, TokenType.ACCESS));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtTokenService other = new JwtTokenService(
                new AuthProperties("another-secret-key-that-is-long-enough", 900, 1209600));
        String foreign = other.issueAccessToken(UUID.randomUUID());

        assertThrows(InvalidTokenException.class,
                () -> tokenService.parseSubject(foreign, TokenType.ACCESS));
    }

    @Test
    void rejectsTamperedToken() {
        String token = tokenService.issueAccessToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(InvalidTokenException.class,
                () -> tokenService.parseSubject(tampered, TokenType.ACCESS));
    }

    @Test
    void rejectsExpiredToken() {
        JwtTokenService expiring = new JwtTokenService(new AuthProperties(SECRET, -1, -1));
        String token = expiring.issueAccessToken(UUID.randomUUID());

        assertThrows(InvalidTokenException.class,
                () -> expiring.parseSubject(token, TokenType.ACCESS));
    }

    @Test
    void rejectsGarbageToken() {
        assertThrows(InvalidTokenException.class,
                () -> tokenService.parseSubject("not-a-jwt", TokenType.ACCESS));
    }

    @Test
    void rejectsSecretShorterThanHmacRequirement() {
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(new AuthProperties("too-short", 900, 1209600)));
    }

    @Test
    void issuesDistinctAccessAndRefreshTokens() {
        UUID userId = UUID.randomUUID();

        assertNotEquals(tokenService.issueAccessToken(userId), tokenService.issueRefreshToken(userId));
    }
}
