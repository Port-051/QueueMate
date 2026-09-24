package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.dto.LoginRequest;
import com.queuemate.platform.account.dto.SignupRequest;
import com.queuemate.platform.account.service.AuthService;
import com.queuemate.platform.common.error.ErrorResponse;
import com.queuemate.platform.common.security.RefreshTokens;
import com.queuemate.platform.common.security.SessionCookies;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 가입 · 로그인 · 재발급 · 로그아웃. 넷 다 인증 없이 부른다. 경로 · 본문 · 에러 코드의 원본은 {@code contracts/platform-api.md} "계정" 이다.
 *
 * <p>로그인시키는 쿠키는 <b>둘</b>이다 — access({@code qm_access})와 refresh({@code qm_refresh}). 무엇을 싣는지는
 * {@link SessionCookies} 가 정한다(2026-09-23 소유자 결정 — Redis 가 죽으면 access 하나만 나간다).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 재발급이 거절될 때의 에러 코드. <b>이유를 가르지 않는다</b> — 쿠키가 없든 이미 쓴 값이든 이 하나다 */
    static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";

    private final AuthService authService;
    private final SessionCookies sessionCookies;
    private final RefreshTokens refreshTokens;

    /** 가입만 한다 — 로그인시키지 않는다(쿠키를 주지 않는다). 계약의 가입 응답에는 {@code Set-Cookie} 가 없다 */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /** 맞으면 access · refresh 를 쿠키로 준다. 토큰은 본문에 싣지 않는다 */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request)
    {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(sessionCookies.login(response.userId())))
                .body(response);
    }

    /**
     * <b>재발급</b> — access 가 만료되기 전에 프런트가 부른다. 본문이 없고 <b>{@code qm_refresh} 쿠키로만</b> 받는다.
     * 성공하면 로그인과 같은 본문에 <b>새 access · 새 refresh</b> 를 싣는다 — 옛 refresh 는 그 자리에서 버려진다(rotation).
     *
     * <p>거절은 <b>전부 같은 401 {@code INVALID_REFRESH_TOKEN}</b> 이다(쿠키가 없든 · 아무 문자열이든 · 이미 쓴 값이든 · 사용자가 사라졌든 ·
     * Redis 를 못 읽었든). 그때도 <b>refresh 쿠키를 지워 준다</b> — 못 쓰는 값을 브라우저가 계속 들고 있게 두지 않는다.
     * access 쿠키는 건드리지 않는다 — 아직 살아 있을 수 있고, 여기서 지우면 재발급 실패가 곧 로그아웃이 된다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = RefreshTokens.COOKIE, required = false) String refreshToken)
    {
        Optional<AuthResponse> user = authService.refresh(refreshToken);
        if(user.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshTokens.expiredCookie().toString())
                    .body(ErrorResponse.of(INVALID_REFRESH_TOKEN, "다시 로그인해 주세요"));
        }
        AuthResponse response = user.orElseThrow();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(sessionCookies.login(response.userId())))
                .body(response);
    }

    /**
     * 로그아웃 — Redis 의 refresh 를 지우고 쿠키 둘을 지운다. <b>쿠키가 없어도 · Redis 가 죽어 있어도 204</b> 다
     * (그 경우 그 refresh 는 수명이 다할 때까지 살아 있다 — {@link RefreshTokens#revoke}).
     *
     * <p>access 는 서버에 지울 것이 없다 — denylist 가 없어 만료까지 서명이 유효하다(CLAUDE.md §5.1 (라)).
     * <b>그래서 로그아웃 뒤에 남는 최대 15분은 감수한다</b> — 무효화할 수 있는 것은 refresh 쪽이다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokens.COOKIE, required = false) String refreshToken)
    {
        refreshTokens.revoke(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(sessionCookies.logout()))
                .build();
    }
}
