package com.queuemate.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 실패 — 필수 필드 누락, enum 값 오류 등 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 형식이 올바르지 않습니다", details));
    }

    /** 지원하지 않는 게임 — MatchConditionValidator가 던진다 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("BAD_REQUEST", e.getMessage()));
    }

    /**
     * Redis 장애 — fail-closed (INV-10).
     *
     * 매칭 상태의 source of truth가 Redis뿐이라, 확인이 안 되면 통과시킬 수 없다.
     * 중복 매칭을 감수하는 fallback을 두지 않는다.
     * 파티에 정원보다 많은 사람이 들어가면 데이터 오류가 아니라 사용자 피해가 된다.
     *
     * 이미 성립한 파티에는 영향이 없다. 새 요청만 거절한다.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleRedisFailure(DataAccessException e) {
        log.error("Redis 장애로 매칭 요청을 거절한다", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(ErrorResponse.of("MATCHING_UNAVAILABLE",
                        "일시적으로 매칭을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요"));
    }
}
