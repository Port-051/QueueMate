package com.queuemate.platform.common.error;

/**
 * 여러 곳(예외 처리기 · 보안 필터)에서 같이 쓰는 공통 에러 코드. 원본은 {@code contracts/platform-api.md} "공통" 이다.
 * 도메인의 에러 코드({@code NICKNAME_TAKEN} 등)는 그 도메인의 서비스에 둔다.
 */
public final class ErrorCodes {

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ORIGIN_NOT_ALLOWED = "ORIGIN_NOT_ALLOWED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    public static final String UNAUTHENTICATED_MESSAGE = "로그인이 필요합니다";

    private ErrorCodes()
    {
    }
}
