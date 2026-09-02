package com.queuemate.auth.api;

import com.queuemate.auth.api.AuthDtos.LoginRequest;
import com.queuemate.auth.api.AuthDtos.RefreshRequest;
import com.queuemate.auth.api.AuthDtos.SignupRequest;
import com.queuemate.auth.api.AuthDtos.TokenResponse;
import com.queuemate.auth.service.AuthService;
import com.queuemate.auth.service.LoginAttemptGuard;
import com.queuemate.auth.service.SignupRateGuard;
import com.queuemate.user.api.UserDtos.UserProfileResponse;
import com.queuemate.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptGuard loginAttempts;
    private final SignupRateGuard signupRate;

    public AuthController(AuthService authService, LoginAttemptGuard loginAttempts,
                          SignupRateGuard signupRate) {
        this.authService = authService;
        this.loginAttempts = loginAttempts;
        this.signupRate = signupRate;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserProfileResponse> signup(@Valid @RequestBody SignupRequest request,
                                                      HttpServletRequest httpRequest) {
        // 비밀번호 해싱 전에 막는다. 해싱이 이 요청에서 가장 비싼 연산이다.
        signupRate.checkAllowed(httpRequest.getRemoteAddr());
        User user = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserProfileResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        // 비밀번호를 확인하기 전에 막는다. 확인한 뒤에 세면 막으려던 연산을 그대로 하게 된다.
        String clientIp = httpRequest.getRemoteAddr();
        loginAttempts.checkAllowed(request.email(), clientIp);
        TokenResponse tokens = authService.login(request);
        loginAttempts.recordSuccess(request.email(), clientIp);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
