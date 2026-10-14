package com.queuemate.chat.web;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * <b>테스트용 발판</b>(2026-10-09) — 보안 필터가 쿠키의 access 토큰을 받아 {@code sub} 를 "나" 로 넘기는지 증명하는 가장 작은 인증 경로다
 * ({@code security/AuthenticationTest}). 실제 기능이 아니다 — 소유자가 첫 실제 컨트롤러({@code /api/v1/chat/dm/…})를 넣으면 테스트를 그쪽으로 옮기고
 * 이 파일은 지워도 된다. "나" 를 꺼내는 모양({@code @AuthenticationPrincipal Jwt} 의 {@code getSubject()})은 그대로 가져가면 된다.
 */
@RestController
public class PingController {

    @GetMapping(value = "/api/v1/chat/ping", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> ping(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("userId", jwt.getSubject());
    }
}
