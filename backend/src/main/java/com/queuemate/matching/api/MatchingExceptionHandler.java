package com.queuemate.matching.api;

import com.queuemate.common.error.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 매칭/예약이 던지는 도메인 인자 오류를 400으로 내린다.
 *
 * <p>모드 key나 key condition 값은 자유 문자열이라 Bean Validation으로 못 잡는다.
 * 범위를 매칭 패키지로 한정해 다른 모듈의 예외 처리에 끼어들지 않는다.
 */
@RestControllerAdvice(basePackages = {
        "com.queuemate.matching",
        "com.queuemate.reservation",
        "com.queuemate.gameconfig"})
public class MatchingExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", e.getMessage()));
    }
}
