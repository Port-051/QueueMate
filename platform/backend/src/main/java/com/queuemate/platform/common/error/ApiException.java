package com.queuemate.platform.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 계약에 적힌 거절을 그대로 응답으로 옮기는 예외. {@link GlobalExceptionHandler} 가 {@link ErrorResponse} 로 바꾼다.
 *
 * <p>{@code code} 는 {@code contracts/platform-api.md} 에 있는 값만 쓴다 — 지어내지 않는다.
 * RuntimeException 이라 {@code @Transactional} 안에서 던지면 트랜잭션이 되돌려진다.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient List<String> details;
    /** 응답의 {@code Retry-After}(초). 없으면 {@code null} — 헤더를 싣지 않는다 */
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message)
    {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<String> details)
    {
        this(status, code, message, details, null);
    }

    private ApiException(HttpStatus status, String code, String message, List<String> details, Long retryAfterSeconds)
    {
        super(message);
        this.status = status;
        this.code = code;
        this.details = (details == null) ? List.of() : List.copyOf(details);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** "지금은 안 되고 이만큼 뒤에 다시 하라"는 거절(429 등) — 응답에 {@code Retry-After} 헤더가 실린다. 1초보다 짧게는 말하지 않는다 */
    public static ApiException retryAfter(HttpStatus status, String code, String message, long seconds)
    {
        return new ApiException(status, code, message, List.of(), Math.max(1L, seconds));
    }

    /**
     * 400 {@code VALIDATION_FAILED} — 애너테이션으로 못 거르는 검증(게임마다 다른 포지션 목록 등)에 쓴다.
     * {@code details} 의 한 줄은 {@code @Valid} 실패와 같은 {@code "필드: 사유"} 꼴이다.
     */
    public static ApiException validationFailed(String field, String reason)
    {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
                "요청 형식이 올바르지 않습니다", List.of(ErrorResponse.fieldDetail(field, reason)));
    }

    /** 401 {@code UNAUTHENTICATED} — 토큰은 멀쩡한데 그 사용자가 DB 에 없는 경우처럼, 필터를 지난 뒤에 드러나는 인증 실패. */
    public static ApiException unauthenticated()
    {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, ErrorCodes.UNAUTHENTICATED_MESSAGE);
    }
}
