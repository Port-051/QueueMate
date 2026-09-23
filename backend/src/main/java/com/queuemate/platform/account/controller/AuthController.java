package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.dto.LoginRequest;
import com.queuemate.platform.account.dto.SignupRequest;
import com.queuemate.platform.account.service.AuthService;
import com.queuemate.platform.common.security.AccessTokenIssuer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 · 로그인 · 로그아웃. 셋 다 인증 없이 부른다. 경로 · 본문 · 에러 코드의 원본은 {@code contracts/platform-api.md} "계정" 이다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccessTokenIssuer accessTokenIssuer;

    /** 가입만 한다 — 로그인시키지 않는다(쿠키를 주지 않는다). 계약의 가입 응답에는 {@code Set-Cookie} 가 없다 */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    /** 맞으면 access 토큰을 쿠키로 준다. 토큰은 본문에 싣지 않는다 */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request)
    {
        AuthResponse response = authService.login(request);
        String token = accessTokenIssuer.issue(response.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessTokenIssuer.cookie(token).toString())
                .body(response);
    }

    /**
     * 쿠키를 지운다. 로그인하지 않았어도 204 다 — 쿠키가 있는지, 그 토큰이 멀쩡한지 보지 않는다.
     *
     * <p><b>서버에는 지울 것이 없다</b> — access 토큰은 denylist 가 없어 만료까지 서명이 유효하다 (CLAUDE.md §5.1 (라)).
     */
    // TEMP-NO-REFRESH: refresh 토큰을 붙이면 여기서 Redis 의 qm:auth:refresh:{uuid} 를 지우고 refresh 쿠키도 같이 지운다 (CLAUDE.md §5.1 (마))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout()
    {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessTokenIssuer.expiredCookie().toString())
                .build();
    }
}
