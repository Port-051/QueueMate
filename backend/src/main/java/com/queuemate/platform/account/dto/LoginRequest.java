package com.queuemate.platform.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. <b>형식은 검증하지 않는다</b>(비어 있는지만 본다) — 형식이 틀린 아이디 · 비밀번호는 "없는 계정"과 같은
 * 401 {@code INVALID_CREDENTIALS} 로 답한다. 형식 검증의 400 으로 갈라 주면 무엇이 틀렸는지 알려 주는 셈이다.
 */
public record LoginRequest(
        @NotBlank(message = "필요합니다") String loginId,
        @NotBlank(message = "필요합니다") String password
) {
    /** 비밀번호가 실수로 로그에 찍히지 않게 한다 */
    @Override
    public String toString()
    {
        return "LoginRequest[loginId=" + loginId + "]";
    }
}
