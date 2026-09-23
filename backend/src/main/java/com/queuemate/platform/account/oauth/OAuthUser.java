package com.queuemate.platform.account.oauth;

/**
 * 제공자에게서 받는 것 전부 — <b>회원 번호와 닉네임뿐이다.</b> 이메일은 받지 않는다 ({@code contracts/platform-api.md} "소셜 로그인").
 *
 * @param providerUserId 제공자 쪽 회원 번호. 카카오는 숫자, 디스코드는 문자열이라 문자열로 든다
 * @param nickname       가입 화면이 미리 채울 닉네임. 없을 수 있다({@code null})
 */
public record OAuthUser(String providerUserId, String nickname) {
}
