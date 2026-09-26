package com.queuemate.platform.account.dto;

/**
 * 소셜 가입 · 재발급의 응답 본문. <b>토큰은 본문에 싣지 않는다</b> — 쿠키로만 나간다(스크립트가 읽을 수 없게 {@code HttpOnly}).
 *
 * @param userId   사용자 번호 — 시스템 안팎에서 이 사람을 가리키는 유일한 값이다(JWT 의 {@code sub} · URL 의 {@code {userId}} 등)
 * @param nickname 보여 주는 이름
 */
public record AuthResponse(Long userId, String nickname) {
}
