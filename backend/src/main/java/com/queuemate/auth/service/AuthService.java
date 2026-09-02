package com.queuemate.auth.service;

import com.queuemate.auth.api.AuthDtos.LoginRequest;
import com.queuemate.auth.api.AuthDtos.SignupRequest;
import com.queuemate.auth.api.AuthDtos.TokenResponse;
import com.queuemate.common.error.ConflictException;
import com.queuemate.common.security.InvalidTokenException;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.common.security.TokenType;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final RefreshTokenStore refreshTokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       JwtTokenService tokenService, RefreshTokenStore refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public User signup(SignupRequest request) {
        // 사전 검사는 안내용이고, 최종 판정은 DB unique 제약이다.
        if (users.existsByEmail(request.email())) {
            throw new ConflictException("EMAIL_ALREADY_IN_USE", "이미 사용 중인 이메일이다");
        }
        if (users.existsByNickname(request.nickname())) {
            throw new ConflictException("NICKNAME_ALREADY_IN_USE", "이미 사용 중인 닉네임이다");
        }
        try {
            User user = User.create(
                    request.email(), passwordEncoder.encode(request.password()), request.nickname());
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시 가입 경합. 사전 검사를 통과해도 여기서 걸린다.
            throw new ConflictException("EMAIL_OR_NICKNAME_ALREADY_IN_USE", "이미 사용 중인 값이다");
        }
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않다"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 어느 쪽이 틀렸는지 구분해서 알려주지 않는다. 계정 존재 여부가 새어나간다.
            throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않다");
        }
        if (!user.isActive()) {
            throw new BadCredentialsException("사용할 수 없는 계정이다");
        }
        return issueTokens(user.getId());
    }

    /** rotation: 쓴 refresh token은 즉시 폐기하고 새로 발급한다. */
    public TokenResponse refresh(String refreshToken) {
        UUID userId = tokenService.parseSubject(refreshToken, TokenType.REFRESH);
        if (!refreshTokens.consume(userId, refreshToken)) {
            // 이미 사용됐거나 폐기된 토큰이다. 탈취 가능성이 있어 전부 무효화한다.
            refreshTokens.revokeAll(userId);
            log.warn("refresh token 재사용 감지, 전체 폐기 userId={}", userId);
            throw new InvalidTokenException("이미 사용된 refresh token이다");
        }
        return issueTokens(userId);
    }

    public void logout(String refreshToken) {
        UUID userId = tokenService.parseSubject(refreshToken, TokenType.REFRESH);
        refreshTokens.consume(userId, refreshToken);
    }

    private TokenResponse issueTokens(UUID userId) {
        String accessToken = tokenService.issueAccessToken(userId);
        String refreshToken = tokenService.issueRefreshToken(userId);
        refreshTokens.store(userId, refreshToken);
        return TokenResponse.bearer(accessToken, refreshToken, tokenService.accessTokenTtlSeconds());
    }
}
