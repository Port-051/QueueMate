package com.queuemate.platform.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code PATCH /users/me} 의 본문. 바꿀 수 있는 것은 닉네임 하나다 — 로그인 아이디는 바꿀 수 없다 (CLAUDE.md §3.5) */
public record NicknameChangeRequest(
        @NotNull(message = "필요합니다")
        @Size(min = 2, max = 16, message = "2~16자여야 합니다")
        @Pattern(regexp = NicknameRules.NO_EDGE_WHITESPACE, message = "앞뒤에 공백을 둘 수 없습니다")
        String nickname
) {
}
