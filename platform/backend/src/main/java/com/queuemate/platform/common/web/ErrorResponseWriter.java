package com.queuemate.platform.common.web;

import com.queuemate.platform.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 필터에서 공통 에러 본문을 직접 쓴다. 컨트롤러에 닿기 전에 거절하는 곳(인증 실패 · {@code Origin} 거절)은
 * {@code @RestControllerAdvice} 를 거치지 않는다 — 그래도 클라이언트가 보는 에러 본문은 같은 모양이어야 한다.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException
    {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(code, message)));
    }
}
