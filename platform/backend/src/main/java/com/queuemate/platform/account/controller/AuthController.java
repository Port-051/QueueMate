package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.service.AccountDeletionService;
import com.queuemate.platform.account.service.AuthService;
import com.queuemate.platform.account.stats.GameStatsLoginRefresher;
import com.queuemate.platform.common.error.ErrorResponse;
import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.common.security.RefreshTokens;
import com.queuemate.platform.common.security.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 재발급 · 로그아웃 · 회원 탈퇴. 재발급 · 로그아웃은 인증 없이 부르고 <b>회원 탈퇴만 access 토큰이 있어야 한다</b>(2026-10-02 — {@link #deleteAccount}).
 * 경로 · 본문 · 에러 코드의 원본은 {@code contracts/platform-api.md} "계정" 이다.
 * <b>직접 가입 · 비밀번호 로그인은 없다</b>(2026-09-26 소유자 결정) — 로그인은 소셜로만 한다({@link SocialAuthController}).
 *
 * <p>로그인시키는 쿠키는 <b>둘</b>이다 — access({@code qm_access})와 refresh({@code qm_refresh}). 무엇을 싣는지는
 * {@link SessionCookies} 가 정한다(2026-09-23 소유자 결정 — Redis 가 죽으면 access 하나만 나간다).
 *
 * <p><b>refresh 쿠키는 {@code Path=/api/v1/auth} 라 재발급 · 로그아웃 둘 다에 실려 온다</b>(2026-10-02 소유자 결정 — 그 전에는 재발급 경로뿐이라 로그아웃이 Redis 의 줄을 못 지웠다).
 * 쿠키는 {@code @CookieValue}(첫 쿠키 하나)가 아니라 {@link RefreshTokens#valuesIn} 으로 <b>전부</b> 읽는다 — 옛 {@code Path} 의 쿠키가 남은 브라우저는
 * 같은 이름의 쿠키를 둘 싣는다(임시 — 2026-10-02 · {@link RefreshTokens} 의 클래스 주석).
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
    private final AccountDeletionService accountDeletionService;

    /**
     * <b>재발급</b> — access 가 만료되기 전에 프런트가 부른다. 본문이 없고 <b>{@code qm_refresh} 쿠키로만</b> 받는다.
     * 성공하면 소셜 로그인의 가입과 같은 본문에 <b>새 access · 새 refresh</b> 를 싣는다 — 옛 refresh 는 그 자리에서 버려진다(rotation).
     *
     * <p><b>같은 이름의 쿠키가 둘 실리면</b>(옛 {@code Path} 의 쿠키가 남았다 — 임시 · 2026-10-02) 둘 다 버리고 그 가운데 유효한 값으로 재발급한다
     * ({@link RefreshTokens#consume(java.util.List)}) — 앞에 실린 옛 값이 이미 회전된 값이어도 로그인이 풀리지 않는다. 성공 응답은 옛 {@code Path} 의 쿠키도 지운다.
     *
     * <p>거절은 <b>전부 같은 401 {@code INVALID_REFRESH_TOKEN}</b> 이다(쿠키가 없든 · 아무 문자열이든 · 이미 쓴 값이든 · 사용자가 사라졌든 ·
     * Redis 를 못 읽었든). 그때도 <b>refresh 쿠키를 지워 준다</b>(옛 {@code Path} 의 것까지) — 못 쓰는 값을 브라우저가 계속 들고 있게 두지 않는다.
     * access 쿠키는 건드리지 않는다 — 아직 살아 있을 수 있고, 여기서 지우면 재발급 실패가 곧 로그아웃이 된다.
     *
     * <p><b>성공하면 그 사람의 낡은 전적(1시간 넘게 지난 LoL · PUBG)을 뒤에서 다시 받게 한다</b>(P-42). access 15분 · refresh 7일이고 refresh 가
     * 쓸 때마다 새 7일이라 자주 여는 사람은 진짜 로그인을 거의 하지 않아서다. 풀에 던지고 곧바로 돌아온다 — 이 응답을 늦추지도 실패시키지도 않는다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request)
    {
        Optional<AuthResponse> user = authService.refresh(RefreshTokens.valuesIn(request));
        if(user.isEmpty())
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshTokens.expiredCookies().stream().map(Object::toString).toArray(String[]::new))
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
     * <b>refresh 쿠키가 실제로 여기 실려 온다</b> — 2026-10-02 에 {@code Path} 를 {@code /api/v1/auth} 로 넓혔다(그 전에는 재발급 경로뿐이라 브라우저가 싣지 않아
     * Redis 의 줄이 남았고 로그아웃 전의 값으로 재발급이 200 이었다). 옛 {@code Path} 의 쿠키는 여기 실리지 않는다 — 그 값은 응답이 브라우저에서만 지운다(임시).
     *
     * <p>access 는 서버에 지울 것이 없다 — denylist 가 없어 만료까지 서명이 유효하다(CLAUDE.md §5.1 (라)).
     * <b>그래서 로그아웃 뒤에 남는 최대 15분은 감수한다</b> — 무효화할 수 있는 것은 refresh 쪽이다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request)
    {
        refreshTokens.revoke(RefreshTokens.valuesIn(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(sessionCookies.logout()))
                .build();
    }

    /**
     * <b>회원 탈퇴</b>(2026-10-02 소유자 결정 · P-48) — 그 사람의 데이터를 지체 없이 전부 지운다(확정된 파티 기록만 남는다 — 작성자 칸이 빈다).
     * 순서 · 거절 · 감수는 {@link AccountDeletionService}. 성공하면 204 에 <b>로그아웃과 같은 쿠키 둘을 지우는 {@code Set-Cookie}</b>(옛 {@code Path} 의 refresh 까지 — 임시)를 싣는다.
     * 카카오 · 디스코드 · 구글 쪽 연결 끊기(unlink)는 하지 않는다 — 제공자의 토큰을 저장하지 않는다(소유자 결정).
     *
     * <p><b>경로가 {@code /api/v1/auth} 아래인 이유</b>(2026-10-02 소유자 지시 "탈퇴 요청에도 refresh 토큰 실어서 버려" · 경로는 팀장이 정했다) — refresh 쿠키의
     * {@code Path} 가 {@code /api/v1/auth} 라 여기 실려 오고, 탈퇴가 끝난 뒤 <b>이 브라우저의 refresh 를 그 자리에서 Redis 에서 지운다</b>(로그아웃과 같은 코드 —
     * {@link RefreshTokens#revoke(java.util.List)}). 쿠키의 {@code Path} 를 더 넓히면 refresh 가 {@code matching} · {@code notification} 으로 가는 모든 요청에 실려서
     * 넓히지 않고 탈퇴를 옮겼다. 옛 {@code DELETE /api/v1/users/me} 는 없앴다(405).
     *
     * <p><b>{@code /api/v1/auth/**} 가운데 이것만 access 토큰(쿠키 {@code qm_access})이 있어야 한다</b> — 보안 설정이 이 경로를 {@code authenticated()} 로 따로 잡고
     * 쿠키 리졸버도 이 경로에서는 쿠키를 집는다(둘이 같은 금 — {@code SecurityConfig} · {@code CookieBearerTokenResolver#ACCOUNT_PATH}). 없거나 무효면 401 {@code UNAUTHENTICATED}
     * (다른 요청과 같은 본문 — 보안 필터의 진입점이 낸다). {@code Origin} 검사는 DELETE 라 그대로 걸린다.
     */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(@CurrentUserId Long userId, HttpServletRequest request)
    {
        accountDeletionService.delete(userId, RefreshTokens.valuesIn(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(sessionCookies.logout()))
                .build();
    }
}
