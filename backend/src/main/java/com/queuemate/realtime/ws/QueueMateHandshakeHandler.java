package com.queuemate.realtime.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.util.Map;

/**
 * 선택한 subprotocol로 버전만 되돌려준다.
 *
 * 기본 구현은 클라이언트가 보낸 첫 protocol을 그대로 고를 수 있는데, 그러면 토큰이
 * 응답 헤더에 실려 돌아간다. 명시적으로 버전만 고른다.
 */
@Component
public class QueueMateHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected String selectProtocol(java.util.List<String> requested, WebSocketHandler handler) {
        return requested != null && requested.stream().anyMatch(WebSocketProtocol.VERSION::equals)
                ? WebSocketProtocol.VERSION
                : null;
    }

    @Override
    protected java.security.Principal determineUser(ServerHttpRequest request,
                                                    WebSocketHandler handler,
                                                    Map<String, Object> attributes) {
        // 인증은 interceptor가 attribute로 넘긴다. Principal은 쓰지 않는다.
        return null;
    }
}
