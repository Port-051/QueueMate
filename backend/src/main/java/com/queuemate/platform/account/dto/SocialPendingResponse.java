package com.queuemate.platform.account.dto;

/**
 * {@code GET /auth/social/pending} 의 응답 — 가입 화면이 미리 채울 값.
 *
 * @param provider          대문자 이름({@code KAKAO} · {@code DISCORD}) — {@code users/me} 의 {@code socialProviders} 와 같은 표기다
 * @param suggestedNickname 제공자 쪽 닉네임을 16자로 자른 것. 없으면 {@code null}. 제안일 뿐이다 — 이미 남이 쓰는 닉네임일 수 있다
 */
public record SocialPendingResponse(String provider, String suggestedNickname) {
}
