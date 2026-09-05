package com.queuemate.common.error;

import com.queuemate.common.security.InvalidTokenException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("잘못된 요청이다");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", detail));
    }

    /**
     * 바디를 아예 읽지 못한 경우. 깨진 JSON이거나 enum에 없는 값이다.
     *
     * <p>이걸 잡지 않으면 Spring 기본 오류 본문이 나가고 {@code code}가 없다.
     * "모든 4xx는 code와 message를 준다"는 계약이 거기서 깨진다.
     * 원인 메시지에는 패키지명과 필드 구조가 섞여 있어 그대로 내보내지 않는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("요청 본문을 읽지 못했다", e);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", "요청 본문을 읽을 수 없다"));
    }

    /** 경로 변수나 쿼리 파라미터의 타입이 맞지 않는다. uuid 자리에 uuid가 아닌 값 같은 것. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", e.getName() + ": 값의 형식이 맞지 않는다"));
    }

    /**
     * {@code @Validated}가 붙은 컨트롤러의 파라미터 제약 위반.
     *
     * <p>Bean Validation이 바디가 아니라 파라미터에서 터지면 이 예외가 온다.
     * 잡지 않으면 400이어야 할 것이 500으로 나간다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse("잘못된 요청이다");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", detail));
    }

    /** Content-Type이 JSON이 아니다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "application/json으로 보내야 한다"));
    }

    /**
     * 도메인이 던지는 인자 오류.
     *
     * <p>모드 key나 조건 값처럼 자유 문자열로 받는 것은 Bean Validation으로 못 잡고
     * 도메인이 직접 거른다. 전에는 매칭·예약·게임설정 세 패키지에만 이 규칙이 걸려 있어
     * 같은 성격의 오류가 패키지에 따라 400이 되기도 500이 되기도 했다.
     * 규칙이 자리마다 다르면 계약이 흔들린다. 전역으로 하나만 둔다.
     *
     * <p>대가는 안다. 진짜 내부 버그로 생긴 IllegalArgumentException도 400으로 나간다.
     * 그건 로그로 잡는다. 계약이 일관된 편이 낫다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(IllegalArgumentException e) {
        log.debug("도메인 인자 오류", e);
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /** 자격 증명 실패와 토큰 실패는 같은 401로 내보내 정보를 흘리지 않는다. */
    @ExceptionHandler({BadCredentialsException.class, InvalidTokenException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", "인증에 실패했다"));
    }

    /** 한도 초과. 어느 기준에 걸렸는지는 알리지 않는다. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    /** 인프라 장애 시 fail-closed. 임의로 통과시키지 않는다. */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStoreUnavailable(ServiceUnavailableException e) {
        log.error("의존 인프라 장애 code={}", e.getCode(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(e.getCode(), "일시적으로 처리할 수 없다"));
    }
}
