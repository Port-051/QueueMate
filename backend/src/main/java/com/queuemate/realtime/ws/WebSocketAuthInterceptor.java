package com.queuemate.realtime.ws;

import com.queuemate.common.security.InvalidTokenException;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.common.security.TokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * handshake 단계에서 인증한다. 연결 후 인증하지 않는다 (contracts/events.md).
 *
 * 미인증 연결을 잠깐이라도 열어두면 그 상태를 관리할 타임아웃과 정리 로직이 따로 필요하고,
 * 그 사이 열린 소켓이 리소스가 된다. handshake에서 끊으면 그 상태 자체가 없다.
 */
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);
    private static final String PROTOCOL_HEADER = "Sec-WebSocket-Protocol";

    private final JwtTokenService tokenService;

    public WebSocketAuthInterceptor(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String token = extractToken(request.getHeaders().get(PROTOCOL_HEADER));
        if (token == null) {
            return reject(response, "token 없는 handshake");
        }
        try {
            UUID userId = tokenService.parseSubject(token, TokenType.ACCESS);
            attributes.put(WebSocketProtocol.USER_ID_ATTRIBUTE, userId);
            return true;
        } catch (InvalidTokenException e) {
            // 토큰 본문은 남기지 않는다 (docs/09 §3).
            return reject(response, "token 검증 실패: " + e.getMessage());
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
        // 없음.
    }

    /**
     * 브라우저는 subprotocol 목록을 콤마로 이어 한 헤더에 담기도 하고 여러 헤더로 보내기도 한다.
     * 둘 다 처리한다.
     */
    private String extractToken(List<String> headerValues) {
        if (headerValues == null) {
            return null;
        }
        return headerValues.stream()
                .flatMap(value -> List.of(value.split(",")).stream())
                .map(String::trim)
                .filter(value -> value.startsWith(WebSocketProtocol.BEARER_PREFIX))
                .map(value -> value.substring(WebSocketProtocol.BEARER_PREFIX.length()))
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private boolean reject(ServerHttpResponse response, String reason) {
        log.debug("WebSocket handshake 거부: {}", reason);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }
}
