package com.queuemate.chat.security;

import com.queuemate.chat.web.ErrorResponseWriter;
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
 * 인증 실패를 {@code platform} 과 같은 에러 본문으로 답한다 — 401 {@code UNAUTHENTICATED}. 쿠키가 없든, 만료됐든, 서명이 틀리든,
 * {@code token_use} 가 다르든 <b>같은 응답</b>이다 — 왜 실패했는지는 로그에만 남긴다.
 *
 * <p>WebSocket 핸드셰이크가 401 이면 브라우저의 {@code WebSocket} 은 {@code onerror} → {@code onclose} 로 끝난다. 그러면 프런트가
 * 재발급({@code POST /api/v1/auth/refresh})한 뒤 소켓을 새로 연다 — 서버 쪽 장치는 두지 않는다({@code ../platform/CLAUDE.md} §5.1 (바)).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    /** platform 의 {@code ErrorCodes.UNAUTHENTICATED_MESSAGE} 와 같은 글귀 */
    static final String UNAUTHENTICATED_MESSAGE = "로그인이 필요합니다";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        log.debug("인증 실패 path={} reason={}", request.getRequestURI(), e.getMessage());
        errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE);
    }
}
