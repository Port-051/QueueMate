package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.dto.DevLoginRequest;
import com.queuemate.platform.account.service.DevLoginService;
import com.queuemate.platform.common.security.SessionCookies;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMP-DEV-LOGIN — <b>개발용 로그인</b> {@code POST /api/v1/auth/dev-login}. 2026-09-29 소유자 결정 — 소셜 앱 키가 없어 아무도 로그인할 수 없는 동안
 * 프런트와 백엔드를 붙여 보려고 <b>임시로</b> 연다({@code contracts/platform-api.md} "개발용 로그인" · P-34).
 * <b>걷어낼 때는 {@code grep -rn TEMP-DEV-LOGIN} 이 가리키는 것을 전부 지운다</b> — 이 파일 · {@link DevLoginService} · {@link DevLoginRequest} ·
 * {@code application.yaml} 과 테스트 설정의 한 줄씩 · 테스트 둘.
 *
 * <p><b>설정 {@code platform.auth.dev-login-enabled}(환경변수 {@code DEV_LOGIN_ENABLED}, 기본 {@code false})가 켜져 있을 때만 빈이 생긴다.</b>
 * 꺼져 있으면 이 경로가 <b>아예 없어</b> 없는 경로와 글자까지 같은 404 {@code NOT_FOUND} 다 — 있는지조차 알리지 않는다.
 *
 * <p><b>쿠키를 건너뛰는 스위치가 아니다 — 진짜 access · refresh 쿠키를 발급한다</b>({@link SessionCookies} — 소셜 가입 · 재발급과 같은 길).
 * 검증하는 쪽(이 앱의 보안 필터 · {@code matching} · {@code notification})은 바뀌지 않고 여전히 서명된 토큰만 받는다
 * (루트 규칙이 금지한 "쿠키가 없으면 {@code userId}" 개발 스위치와 다르다). 대신 켜 두면 <b>누구든 아무 닉네임의 사용자로</b> 로그인할 수 있다 — 운영에서 켜지 마라.
 *
 * <p>{@code /api/v1/auth/**} 라 인증 없이 부르고, POST 라 {@code Origin} 검사({@code OriginCheckFilter})를 그대로 받는다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnBooleanProperty(DevLoginService.ENABLED_PROPERTY)
public class DevLoginController {

    /** 본문이 없거나 {@code nickname} 이 없을 때의 닉네임. 닉네임 규칙(2~16자 · 앞뒤 공백 없음)을 지킨다 */
    static final String DEFAULT_NICKNAME = "dev-tester";

    private final DevLoginService devLoginService;
    private final SessionCookies sessionCookies;

    public DevLoginController(DevLoginService devLoginService, SessionCookies sessionCookies)
    {
        this.devLoginService = devLoginService;
        this.sessionCookies = sessionCookies;
        // 뜰 때 한 줄 — 운영 로그에 이것이 보이면 DEV_LOGIN_ENABLED 가 잘못 들어간 것이다
        log.warn("개발용 로그인(POST /api/v1/auth/dev-login)이 켜져 있다 — 운영에서는 DEV_LOGIN_ENABLED 를 두지 마라");
    }

    /**
     * 그 닉네임의 사용자가 있으면 그 사람으로, 없으면 만들어서 <b>로그인시킨다</b> — 200 {@code {userId, nickname}} + {@code Set-Cookie} 둘
     * ({@code qm_access} · {@code qm_refresh}. Redis 가 죽었으면 access 하나 — {@link SessionCookies#login}). 닉네임 규칙을 어기면 400 {@code VALIDATION_FAILED}.
     */
    @PostMapping("/dev-login")
    public ResponseEntity<AuthResponse> devLogin(@Valid @RequestBody(required = false) DevLoginRequest request)
    {
        String nickname = (request == null || request.nickname() == null) ? DEFAULT_NICKNAME : request.nickname();
        AuthResponse response = devLoginService.login(nickname);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(sessionCookies.login(response.userId())))
                .body(response);
    }
}
