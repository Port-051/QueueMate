package com.queuemate.platform.account.dto;

import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.common.web.MaxBytes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입 요청. 검증의 원본은 {@code contracts/platform-api.md} "계정" 이다.
 *
 * @param loginId  로그인 아이디. 소문자 · 숫자 · 밑줄 4~20자. 로그인할 때만 쓴다 — 사용자 번호({@code userId})는 DB 가 매긴다. DB 도 같은 CHECK 를 건다
 * @param password 8~72자. BCrypt 가 72<b>바이트</b>까지만 봐서 바이트 길이도 같이 본다(한글은 한 글자가 3바이트다)
 * @param nickname 2~16자, 앞뒤 공백 없음. 유일하다
 */
public record SignupRequest(
        @NotNull(message = "필요합니다")
        @Pattern(regexp = User.LOGIN_ID_PATTERN, message = "소문자 · 숫자 · 밑줄로 4~20자여야 합니다")
        String loginId,

        @NotNull(message = "필요합니다")
        @Size(min = 8, max = 72, message = "8~72자여야 합니다")
        @MaxBytes(value = 72, message = "UTF-8 로 72바이트를 넘을 수 없습니다")
        String password,

        @NotNull(message = "필요합니다")
        @Size(min = 2, max = 16, message = "2~16자여야 합니다")
        @Pattern(regexp = NicknameRules.NO_EDGE_WHITESPACE, message = "앞뒤에 공백을 둘 수 없습니다")
        String nickname
) {
    /** 비밀번호가 실수로 로그에 찍히지 않게 한다 — record 의 기본 toString 은 모든 칸을 찍는다 */
    @Override
    public String toString()
    {
        return "SignupRequest[loginId=" + loginId + ", nickname=" + nickname + "]";
    }
}
