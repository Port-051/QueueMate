package com.queuemate.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 필터가 막은 401에도 {@link ErrorResponse}를 실어 준다.
 *
 * <p>이게 없으면 필터 단계 401만 본문이 비어 클라이언트가 두 갈래 처리를 해야 한다.
 * "모든 4xx는 code와 message를 준다"를 예외 없이 지키기 위해 여기서도 같은 형태로 쓴다.
 *
 * <p>실패 원인은 담지 않는다. 토큰이 없는 것과 만료된 것과 위조된 것을 구분해 주면
 * 공격자에게 정보를 준다.
 */
@Component
public class ErrorResponseEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ErrorResponseEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse("UNAUTHORIZED", "인증에 실패했다"));
    }
}
