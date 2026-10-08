package com.queuemate.common.error;

import java.util.List;

/**
 * 모든 에러 응답의 공통 형식.
 */
public record ErrorResponse(
        String code,
        String message,
        List<String> details
) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }
}
