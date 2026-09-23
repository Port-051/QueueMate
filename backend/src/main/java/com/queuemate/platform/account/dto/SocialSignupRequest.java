package com.queuemate.platform.account.dto;

import com.queuemate.platform.account.domain.User;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 소셜로 처음 온 사람의 가입 요청 — 로그인 아이디와 닉네임만 정한다. <b>비밀번호가 없다.</b> 누구의 소셜 계정인지는 본문이 아니라
 * 쿠키 {@code qm_social_signup} 에서 온다(서명된 값이라 본문으로 바꿔 넣을 수 없다). 검증은 {@link SignupRequest} 의 같은 칸과 같다.
 */
public record SocialSignupRequest(
        @NotNull(message = "필요합니다")
        @Pattern(regexp = User.LOGIN_ID_PATTERN, message = "소문자 · 숫자 · 밑줄로 4~20자여야 합니다")
        String loginId,

        @NotNull(message = "필요합니다")
        @Size(min = 2, max = 16, message = "2~16자여야 합니다")
        @Pattern(regexp = NicknameRules.NO_EDGE_WHITESPACE, message = "앞뒤에 공백을 둘 수 없습니다")
        String nickname
) {
}
