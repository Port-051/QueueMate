package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * <b>방 안의 일의 요청에만</b> 걸리는 예외 처리 — {@code contracts/room-api.md} "공통 에러" 의 두 코드를 지킨다.
 * 나머지(401 · 모르는 경로 · 500 등)는 이 앱의 {@code common.error.GlobalExceptionHandler} 가 그대로 맡는다. 본문의 모양은 한 벌({@link ErrorResponse})이다.
 *
 * <ul>
 *   <li><b>400 {@code INVALID_REQUEST}</b> — 이 앱의 공통 코드는 {@code VALIDATION_FAILED} 인데 {@code room} 앱은 같은 자리에 이 이름을 썼다.
 *       2026-09-25 에 합친 1단계는 "동작은 그대로 두고 인증만 바꾼다"라서 방의 요청에서는 옛 이름을 지킨다. 하나로 합칠지는 2단계에서 정한다</li>
 *   <li><b>503 {@code ROOM_UNAVAILABLE}</b> + {@code Retry-After: 5} — Redis 에 닿지 못했다. 방의 상태는 Redis 에만 있어서 확인이 안 되면 통과시킬 수 없다 —
 *       정원을 못 세는데 입장시키거나, 같은 방인지 못 보는데 시그널을 전달하지 않는다. 이 처리기가 없으면 500 {@code INTERNAL_ERROR} 가 된다</li>
 * </ul>
 *
 * <p><b>순서가 중요하다.</b> {@code GlobalExceptionHandler} 는 {@code Exception} 을 다 받는 처리기를 가져서, 이쪽이 먼저 물어지지 않으면 이 처리기에 닿지 않는다 —
 * 그래서 가장 앞 순서다. 범위를 방의 컨트롤러로 좁혀 두었으므로 다른 요청에는 걸리지 않는다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = RoomController.class)
public class RoomExceptionHandler {

    /** {@code @Valid} 실패 — 필수 칸이 비었다. {@code details} 는 {@code "필드: 사유"} 꼴이다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e)
    {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> ErrorResponse.fieldDetail(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(RoomErrors.INVALID_REQUEST, "요청 형식이 올바르지 않습니다", details));
    }

    /** 본문이 없거나 JSON 이 아니다 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e)
    {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(RoomErrors.INVALID_REQUEST, "요청 본문을 읽을 수 없습니다"));
    }

    /** Redis 에 닿지 못했다 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleRedisFailure(DataAccessException e)
    {
        log.error("Redis 에 닿지 못해 방의 요청을 거절한다", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(ErrorResponse.of(RoomErrors.ROOM_UNAVAILABLE,
                        "일시적으로 파티방을 이용할 수 없습니다. 잠시 후 다시 시도해 주세요"));
    }
}
