package com.queuemate.realtime.ws;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.realtime.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/**
 * /ws의 유일한 handler.
 *
 * MDC는 요청 필터가 아니라 여기서 심는다. WebSocket은 요청 하나에 스레드 하나가
 * 붙는 모델이 아니라, 메시지마다 어느 스레드가 올지 알 수 없다.
 */
@Component
public class QueueMateWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(QueueMateWebSocketHandler.class);

    private final SessionRegistry sessions;

    public QueueMateWebSocketHandler(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = userIdOf(session);
        sessions.register(userId, session);
        withContext(session, () -> log.info("WebSocket 연결 open sessions={}",
                sessions.sessionsOf(userId).size()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = userIdOf(session);
        sessions.unregister(userId, session);
        withContext(session, () -> log.info("WebSocket 연결 close code={} reason={}",
                status.getCode(), status.getReason()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // client → server는 WEBRTC_SIGNAL만 허용된다 (contracts/events.md).
        // signaling relay는 아직 없다. 그전까지 들어온 메시지는 버린다.
        withContext(session, () -> log.debug("client 메시지 수신 bytes={}",
                message.getPayloadLength()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        withContext(session, () -> log.warn("WebSocket 전송 오류: {}", exception.getMessage()));
    }

    private UUID userIdOf(WebSocketSession session) {
        Object userId = session.getAttributes().get(WebSocketProtocol.USER_ID_ATTRIBUTE);
        if (userId == null) {
            // interceptor를 통과했으면 반드시 있다. 없으면 배선이 잘못된 것이다.
            throw new IllegalStateException("인증되지 않은 WebSocket session이다");
        }
        return (UUID) userId;
    }

    /** 로그에 주체와 session을 붙이고 끝나면 반드시 지운다. 스레드가 재사용된다. */
    private void withContext(WebSocketSession session, Runnable action) {
        MDC.put(MdcKeys.USER_ID, userIdOf(session).toString());
        MDC.put(MdcKeys.REQUEST_ID, session.getId());
        try {
            action.run();
        } finally {
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.REQUEST_ID);
        }
    }
}
