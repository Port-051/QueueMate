package com.queuemate.platform.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * TEMP-DEV-LOGIN — 개발용 로그인({@code POST /api/v1/auth/dev-login})의 본문. 걷어낼 때 이 파일째 지운다
 * ({@code contracts/platform-api.md} "개발용 로그인" · P-34).
 *
 * <p>닉네임은 <b>없어도 된다</b>(본문이 없거나 {@code nickname} 이 없거나 {@code null} 이면 {@code dev-tester}). 있으면 소셜 가입 · 닉네임 바꾸기와
 * <b>같은 규칙</b>이다 — 2~16자 · 앞뒤 공백 없음({@link SocialSignupRequest}). 빈 문자열은 "없다" 가 아니라 규칙 위반이다.
 */
public record DevLoginRequest(
        @Size(min = 2, max = 16, message = "2~16자여야 합니다")
        @Pattern(regexp = NicknameRules.NO_EDGE_WHITESPACE, message = "앞뒤에 공백을 둘 수 없습니다")
        String nickname
) {
}
