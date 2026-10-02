package com.queuemate.platform.common.security;

import com.queuemate.platform.common.error.ErrorCodes;
import com.queuemate.platform.common.web.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 실패를 계약의 에러 본문으로 답한다 — 401 {@code UNAUTHENTICATED}. 쿠키가 없든, 만료됐든, 서명이 틀리든,
 * {@code token_use} 가 다르든 <b>같은 응답</b>이다 — 왜 실패했는지는 로그에만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException
    {
        log.debug("인증 실패 path={} reason={}", request.getRequestURI(), e.getMessage());
        errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED,
                ErrorCodes.UNAUTHENTICATED, ErrorCodes.UNAUTHENTICATED_MESSAGE);
    }
}
