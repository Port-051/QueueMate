package com.queuemate.common.security;

import com.queuemate.common.web.ErrorResponseWriter;
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
 * 인증 실패 — 401 {@code UNAUTHENTICATED}. 쿠키가 없든, 만료됐든, 서명이 틀리든, {@code token_use} · {@code sub} 가 어긋나든
 * <b>같은 응답</b>이다({@code platform} 과 같은 코드 · 같은 본문 모양). 왜 실패했는지는 로그에만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        log.debug("인증 실패 path={} reason={}", request.getRequestURI(), e.getMessage());
        errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED, "로그인이 필요합니다");
    }
}
