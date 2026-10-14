package com.queuemate.platform.account.oauth;

/**
 * 제공자에게서 받는 것 전부 — <b>회원 번호 · 닉네임 · (검증된) 이메일</b>이다. 이메일은 2026-10-13 소유자 결정으로 더했다
 * ({@code contracts/platform-api.md} P-58). 전화번호는 받지 않는다.
 *
 * @param providerUserId 제공자 쪽 회원 번호. 카카오는 숫자, 디스코드 · 구글은 문자열이라 문자열로 든다
 * @param nickname       가입 화면이 미리 채울 닉네임. 없을 수 있다({@code null})
 * @param email          제공자가 <b>검증됐다고 말한</b> 이메일만. 동의하지 않았거나 · 칸이 없거나 · 검증되지 않았으면 {@code null} —
 *                       그래도 가입 · 로그인은 된다(선택이다). 식별자가 아니다 — 이것으로 사용자를 찾지 않는다
 */
public record OAuthUser(String providerUserId, String nickname, String email) {
}
