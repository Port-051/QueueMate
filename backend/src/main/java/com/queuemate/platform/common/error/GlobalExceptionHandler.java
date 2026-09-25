package com.queuemate.platform.common.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 컨트롤러 밖으로 나온 예외를 공통 에러 형식({@link ErrorResponse})으로 바꾼다.
 *
 * <p>인증 실패(401)와 {@code Origin} 거절(403)은 여기로 오지 않는다 — 컨트롤러에 닿기 전에 필터가 같은 모양으로 답한다
 * ({@code common.security.ApiAuthenticationEntryPoint} · {@code common.web.OriginCheckFilter}).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE = "요청 형식이 올바르지 않습니다";

    /** 계약에 적힌 거절 — 서비스가 상태 코드와 에러 코드를 정해서 던진 것이다 */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e)
    {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.getStatus());
        if(e.getRetryAfterSeconds() != null)
        {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()));
        }
        return response.body(new ErrorResponse(e.getCode(), e.getMessage(), e.getDetails()));
    }

    /** {@code @Valid} 실패 — {@code details} 에 {@code "필드: 사유"} 꼴로 필드마다 한 줄을 담는다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e)
    {
        // 한 필드가 여러 검증에 걸리면 처음 것만 남긴다. 순서를 고정하려고 LinkedHashMap 이다
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        List<String> details = fields.entrySet().stream()
                .map(field -> ErrorResponse.fieldDetail(field.getKey(), field.getValue()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.VALIDATION_FAILED, VALIDATION_MESSAGE, details));
    }

    /** 본문이 없거나 JSON 이 아니다. 어느 필드인지 말할 수 없어 {@code details} 는 비어 있다 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e)
    {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCodes.VALIDATION_FAILED, "요청 본문을 읽을 수 없습니다"));
    }

    /**
     * 필수 쿼리 파라미터가 없다 — 지금 그런 것은 게시판 목록의 {@code game} 하나다(2026-09-25 소유자 결정).
     *
     * <p><b>빈 값(`?game=`)도 여기로 온다</b> — 스프링이 enum 으로 바꾸다 {@code null} 이 되면 "안 준 것" 으로 다룬다.
     * 이 핸들러가 없으면 아래 {@link #handleUnexpected} 를 거쳐 {@code details} 가 <b>빈 채로</b> 나가 어느 파라미터가 빠졌는지 알 수 없다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e)
    {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.VALIDATION_FAILED, VALIDATION_MESSAGE,
                        List.of(ErrorResponse.fieldDetail(e.getParameterName(), "필요합니다"))));
    }

    /** 경로 변수 · 쿼리 파라미터를 기대한 형으로 바꾸지 못했다 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e)
    {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCodes.VALIDATION_FAILED, VALIDATION_MESSAGE,
                        List.of(ErrorResponse.fieldDetail(e.getName(), "올바른 값이 아닙니다"))));
    }

    /** 모르는 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCodes.NOT_FOUND, "없는 경로입니다"));
    }

    /**
     * 그 밖의 모든 것. Spring MVC 가 상태 코드를 정해 둔 예외(405 · 415 등)는 그 상태 코드를 지키고,
     * 나머지는 500 {@code INTERNAL_ERROR} 다. <b>스택은 로그에만 남긴다</b> — 응답에는 싣지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) throws Exception
    {
        // Spring Security 의 예외는 보안 필터가 401 · 403 으로 옮긴다. 여기서 삼키면 500 이 된다
        if(e instanceof AuthenticationException || e instanceof AccessDeniedException)
        {
            throw e;
        }
        if(e instanceof org.springframework.web.ErrorResponse mvcError && mvcError.getStatusCode().is4xxClientError())
        {
            return handleMvcClientError(mvcError.getStatusCode());
        }
        log.error("처리하지 못한 예외다", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCodes.INTERNAL_ERROR, "서버에서 오류가 났습니다"));
    }

    /**
     * 계약의 공통 에러에 없는 4xx(405 · 406 · 415 등). 400 과 404 는 계약의 코드로, 나머지는 HTTP 상태의 이름을 코드로 쓴다
     * (예: {@code METHOD_NOT_ALLOWED}) — 계약에 없는 값이다. 클라이언트가 계약대로 부르면 볼 일이 없다.
     */
    private ResponseEntity<ErrorResponse> handleMvcClientError(HttpStatusCode statusCode)
    {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code;
        if(status == HttpStatus.BAD_REQUEST)
        {
            code = ErrorCodes.VALIDATION_FAILED;
        }
        else if(status == HttpStatus.NOT_FOUND)
        {
            code = ErrorCodes.NOT_FOUND;
        }
        else
        {
            code = (status == null) ? "BAD_REQUEST" : status.name();
        }
        return ResponseEntity.status(statusCode).body(ErrorResponse.of(code, "요청을 처리할 수 없습니다"));
    }
}
