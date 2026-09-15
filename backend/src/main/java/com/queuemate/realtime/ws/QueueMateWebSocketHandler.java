package com.queuemate.realtime.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.logging.MdcKeys;
import com.queuemate.realtime.presence.DepartureGracePolicy;
import com.queuemate.realtime.presence.DeparturePendingStore;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.session.ClusterPresence;
import com.queuemate.realtime.session.SessionRegistry;
import com.queuemate.realtime.session.SessionSnapshotAssembler;
import com.queuemate.realtime.signal.ClientMessage;
import com.queuemate.realtime.signal.SignalRelayService;
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
    private final ClusterPresence presence;
    private final SignalRelayService signals;
    private final DeparturePendingStore departures;
    private final DepartureGracePolicy grace;
    private final SessionSnapshotAssembler snapshots;
    private final RealtimeEventPublisher events;
    private final ObjectMapper objectMapper;

    public QueueMateWebSocketHandler(SessionRegistry sessions, ClusterPresence presence,
                                     SignalRelayService signals,
                                     DeparturePendingStore departures, DepartureGracePolicy grace,
                                     SessionSnapshotAssembler snapshots,
                                     RealtimeEventPublisher events, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.presence = presence;
        this.signals = signals;
        this.departures = departures;
        this.grace = grace;
        this.snapshots = snapshots;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = userIdOf(session);
        sessions.register(userId, session);
        // 다른 노드가 이 사용자를 오프라인으로 보지 않게 먼저 알린다.
        presence.markOnline(userId);
        // 예약 취소가 먼저다. 스냅샷을 읽는 사이에 이탈 처리가 돌면
        // 방금 붙은 사용자에게 없어진 파티를 보내게 된다.
        departures.cancel(userId);
        withContext(session, () -> {
            log.info("WebSocket 연결 open sessions={}", sessions.sessionsOf(userId).size());
            sendSnapshot(session, userId);
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = userIdOf(session);
        sessions.unregister(userId, session);
        // 탭을 여러 개 열어 둔 경우 하나를 닫아도 아직 접속 중이다. 그때는 예약하지 않는다.
        if (!sessions.hasLocalSession(userId)) {
            // 이 노드에서는 다 닫혔다고 먼저 알려야 한다. 지우기 전에 접속 여부를 물으면
            // 자기가 남긴 기록을 보고 아직 붙어 있다고 답한다.
            presence.markOffline(userId);
        }
        // 다른 서버에 아직 붙어 있으면 이탈이 아니다.
        boolean online = presence.isOnline(userId);
        if (!online) {
            // 유예는 이 사용자가 어떤 파티에 있느냐에 따라 다르다. 게임 중이면 길게 준다.
            departures.schedule(userId, grace.graceFor(userId));
        }
        withContext(session, () -> log.info("WebSocket 연결 close code={} reason={} online={}",
                status.getCode(), status.getReason(), online));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        withContext(session, () -> {
            ClientMessage parsed;
            try {
                parsed = objectMapper.readValue(message.getPayload(), ClientMessage.class);
            } catch (Exception e) {
                // 깨진 JSON으로 연결을 끊지는 않는다. 한 프레임이 잘못됐다고
                // 통화 중인 세션을 죽이면 손해가 더 크다.
                log.debug("해석할 수 없는 client 메시지 bytes={}", message.getPayloadLength());
                return;
            }
            signals.relay(userIdOf(session), parsed);
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        withContext(session, () -> log.warn("WebSocket 전송 오류: {}", exception.getMessage()));
    }

    /**
     * 끊긴 동안 바뀐 것을 현재 상태로 대신 알린다. 첫 연결과 재연결을 구분하지 않는다.
     * 실패해도 연결은 유지한다. 클라이언트가 REST로 직접 읽는 길이 남아 있다.
     */
    private void sendSnapshot(WebSocketSession session, UUID userId) {
        try {
            events.publishTo(session, snapshots.snapshotOf(userId));
        } catch (RuntimeException e) {
            log.error("연결 스냅샷 전송 실패", e);
        }
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
