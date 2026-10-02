package com.queuemate.platform.common.error;

import java.util.List;

/**
 * 모든 에러 응답의 공통 형식 — {@code {"code", "message", "details"}} ({@code contracts/platform-api.md} "공통").
 *
 * <p>{@code details} 는 <b>문자열의 배열</b>이다 — {@code matching} · {@code room} 의 {@code ErrorResponse} 와 같은 모양이라야
 * 프런트가 에러를 한 가지로 다룬다. 담을 것이 없어도 {@code null} 이 아니라 {@code []} 다.
 * 검증 실패는 {@code "필드: 사유"} 꼴로 필드마다 한 줄을 담는다.
 */
public record ErrorResponse(
        String code,
        String message,
        List<String> details
) {
    public ErrorResponse
    {
        details = (details == null) ? List.of() : List.copyOf(details);
    }

    /** 검증 실패의 한 줄 — {@code "필드: 사유"}. 꼴을 한 곳에서만 정한다 */
    public static String fieldDetail(String field, String reason)
    {
        return field + ": " + reason;
    }

    public static ErrorResponse of(String code, String message)
    {
        return new ErrorResponse(code, message, List.of());
    }
}
