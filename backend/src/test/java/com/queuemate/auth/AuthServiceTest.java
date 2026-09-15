package com.queuemate.auth;

import com.queuemate.auth.api.AuthDtos.LoginRequest;
import com.queuemate.auth.api.AuthDtos.SignupRequest;
import com.queuemate.auth.api.AuthDtos.TokenResponse;
import com.queuemate.auth.service.AuthService;
import com.queuemate.auth.service.RefreshTokenStore;
import com.queuemate.common.error.ConflictException;
import com.queuemate.common.security.AuthProperties;
import com.queuemate.common.security.InvalidTokenException;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.common.security.TokenType;
import com.queuemate.user.domain.User;
import com.queuemate.user.domain.UserStatus;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String PASSWORD = "password12";

    private UserRepository users;
    private InMemoryRefreshTokenStore refreshTokens;
    private JwtTokenService tokenService;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        refreshTokens = new InMemoryRefreshTokenStore();
        // 테스트 시간을 줄이려고 BCrypt strength를 낮춘다. 검증 동작은 동일하다.
        passwordEncoder = new BCryptPasswordEncoder(4);
        tokenService = new JwtTokenService(
                new AuthProperties("test-secret-key-that-is-long-enough-32b", 900, 1209600));
        authService = new AuthService(users, passwordEncoder, tokenService, refreshTokens);
    }

    @Test
    void rejectsSignupWithDuplicateEmail() {
        when(users.existsByEmail("a@queuemate.test")).thenReturn(true);

        ConflictException e = assertThrows(ConflictException.class, () ->
                authService.signup(new SignupRequest("a@queuemate.test", PASSWORD, "nick")));
        assertEquals("EMAIL_ALREADY_IN_USE", e.getCode());
    }

    @Test
    void rejectsSignupWithDuplicateNickname() {
        when(users.existsByEmail(any())).thenReturn(false);
        when(users.existsByNickname("nick")).thenReturn(true);

        ConflictException e = assertThrows(ConflictException.class, () ->
                authService.signup(new SignupRequest("a@queuemate.test", PASSWORD, "nick")));
        assertEquals("NICKNAME_ALREADY_IN_USE", e.getCode());
    }

    @Test
    void storesHashedPasswordNotRawPassword() {
        when(users.existsByEmail(any())).thenReturn(false);
        when(users.existsByNickname(any())).thenReturn(false);
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        authService.signup(new SignupRequest("a@queuemate.test", PASSWORD, "nick"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(users).saveAndFlush(captor.capture());
        String stored = captor.getValue().getPasswordHash();
        assertNotEquals(PASSWORD, stored);
        assertTrue(passwordEncoder.matches(PASSWORD, stored));
    }

    @Test
    void rejectsLoginWithUnknownEmail() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("nobody@queuemate.test", PASSWORD)));
    }

    @Test
    void rejectsLoginWithWrongPassword() {
        when(users.findByEmail(any())).thenReturn(Optional.of(activeUser()));

        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("a@queuemate.test", "wrong-password")));
    }

    @Test
    void rejectsLoginForSuspendedAccount() {
        User user = activeUser();
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        when(users.findByEmail(any())).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("a@queuemate.test", PASSWORD)));
    }

    @Test
    void loginIssuesUsableTokenPair() {
        User user = activeUser();
        when(users.findByEmail(any())).thenReturn(Optional.of(user));

        TokenResponse tokens = authService.login(new LoginRequest("a@queuemate.test", PASSWORD));

        assertEquals("Bearer", tokens.tokenType());
        assertEquals(900, tokens.expiresIn());
        assertEquals(user.getId(), tokenService.parseSubject(tokens.accessToken(), TokenType.ACCESS));
        assertEquals(user.getId(), tokenService.parseSubject(tokens.refreshToken(), TokenType.REFRESH));
        assertTrue(refreshTokens.contains(user.getId(), tokens.refreshToken()));
    }

    @Test
    void refreshRotatesTokenAndInvalidatesOldOne() {
        User user = activeUser();
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
        TokenResponse first = authService.login(new LoginRequest("a@queuemate.test", PASSWORD));

        TokenResponse second = authService.refresh(first.refreshToken());

        assertNotEquals(first.refreshToken(), second.refreshToken());
        assertFalse(refreshTokens.contains(user.getId(), first.refreshToken()));
        assertTrue(refreshTokens.contains(user.getId(), second.refreshToken()));
    }

    @Test
    void reusedRefreshTokenRevokesEveryTokenOfThatUser() {
        User user = activeUser();
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
        TokenResponse first = authService.login(new LoginRequest("a@queuemate.test", PASSWORD));
        TokenResponse second = authService.refresh(first.refreshToken());

        // 이미 쓴 토큰을 다시 제시하면 탈취로 보고 전부 폐기한다.
        assertThrows(InvalidTokenException.class, () -> authService.refresh(first.refreshToken()));
        assertFalse(refreshTokens.contains(user.getId(), second.refreshToken()));
    }

    @Test
    void rejectsAccessTokenAtRefreshEndpoint() {
        User user = activeUser();
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
        TokenResponse tokens = authService.login(new LoginRequest("a@queuemate.test", PASSWORD));

        assertThrows(InvalidTokenException.class, () -> authService.refresh(tokens.accessToken()));
    }

    @Test
    void logoutDropsOnlyThatRefreshToken() {
        User user = activeUser();
        when(users.findByEmail(any())).thenReturn(Optional.of(user));
        TokenResponse first = authService.login(new LoginRequest("a@queuemate.test", PASSWORD));
        TokenResponse second = authService.login(new LoginRequest("a@queuemate.test", PASSWORD));

        authService.logout(first.refreshToken());

        assertFalse(refreshTokens.contains(user.getId(), first.refreshToken()));
        assertTrue(refreshTokens.contains(user.getId(), second.refreshToken()));
    }

    private User activeUser() {
        return User.create("a@queuemate.test", passwordEncoder.encode(PASSWORD), "nick");
    }

    /** Redis 없이 rotation 동작만 검증하기 위한 대역. */
    private static final class InMemoryRefreshTokenStore implements RefreshTokenStore {

        private final Map<UUID, Set<String>> tokens = new HashMap<>();

        @Override
        public void store(UUID userId, String refreshToken) {
            tokens.computeIfAbsent(userId, key -> new HashSet<>()).add(refreshToken);
        }

        @Override
        public boolean consume(UUID userId, String refreshToken) {
            return tokens.getOrDefault(userId, Set.of()).remove(refreshToken);
        }

        @Override
        public void revokeAll(UUID userId) {
            tokens.remove(userId);
        }

        boolean contains(UUID userId, String refreshToken) {
            return tokens.getOrDefault(userId, Set.of()).contains(refreshToken);
        }
    }
}
