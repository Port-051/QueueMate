package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.service.AuthService;
import com.queuemate.platform.account.stats.GameStatsLoginRefresher;
import com.queuemate.platform.common.error.ErrorResponse;
import com.queuemate.platform.common.security.RefreshTokens;
import com.queuemate.platform.common.security.SessionCookies;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 재발급 · 로그아웃. 둘 다 인증 없이 부른다. 경로 · 본문 · 에러 코드의 원본은 {@code contracts/platform-api.md} "계정" 이다.
 * <b>직접 가입 · 비밀번호 로그인은 없다</b>(2026-09-26 소유자 결정) — 로그인은 소셜로만 한다({@link SocialAuthController}).
 *
 * <p>로그인시키는 쿠키는 <b>둘</b>이다 — access({@code qm_access})와 refresh({@code qm_refresh}). 무엇을 싣는지는
 * {@link SessionCookies} 가 정한다(2026-09-23 소유자 결정 — Redis 가 죽으면 access 하나만 나간다).
 *
 * <p><b>재발급이 성공하면 낡은 전적을 뒤에서 다시 받게 한다</b>(2026-09-30 소유자 결정 · P-42 — {@link GameStatsLoginRefresher}). 응답은 기다리지 않는다.
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
    private final GameStatsLoginRefresher gameStatsLoginRefresher;

    /**
     * <b>재발급</b> — access 가 만료되기 전에 프런트가 부른다. 본문이 없고 <b>{@code qm_refresh} 쿠키로만</b> 받는다.
     * 성공하면 소셜 로그인의 가입과 같은 본문에 <b>새 access · 새 refresh</b> 를 싣는다 — 옛 refresh 는 그 자리에서 버려진다(rotation).
     *
     * <p>거절은 <b>전부 같은 401 {@code INVALID_REFRESH_TOKEN}</b> 이다(쿠키가 없든 · 아무 문자열이든 · 이미 쓴 값이든 · 사용자가 사라졌든 ·
     * Redis 를 못 읽었든). 그때도 <b>refresh 쿠키를 지워 준다</b> — 못 쓰는 값을 브라우저가 계속 들고 있게 두지 않는다.
     * access 쿠키는 건드리지 않는다 — 아직 살아 있을 수 있고, 여기서 지우면 재발급 실패가 곧 로그아웃이 된다.
     *
     * <p><b>성공하면 그 사람의 낡은 전적(1시간 넘게 지난 LoL · PUBG)을 뒤에서 다시 받게 한다</b>(P-42). access 15분 · refresh 7일이고 refresh 가
     * 쓸 때마다 새 7일이라 자주 여는 사람은 진짜 로그인을 거의 하지 않아서다. 풀에 던지고 곧바로 돌아온다 — 이 응답을 늦추지도 실패시키지도 않는다.
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
        String[] cookies = SessionCookies.array(sessionCookies.login(response.userId()));
        gameStatsLoginRefresher.refreshStale(response.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies)
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
