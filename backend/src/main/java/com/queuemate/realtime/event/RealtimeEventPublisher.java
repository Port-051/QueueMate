package com.queuemate.realtime.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.logging.MdcKeys;
import com.queuemate.realtime.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * 서버 이벤트를 대상 사용자들의 열린 session으로 보낸다.
 *
 * 전송 실패는 삼킨다. 이벤트를 못 받은 클라이언트는 재연결 후 REST로 현재 상태를
 * 다시 읽는다. 이벤트는 상태의 소스가 아니라 알림이다.
 */
@Component
public class RealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);

    private final SessionRegistry sessions;
    private final ObjectMapper objectMapper;

    public RealtimeEventPublisher(SessionRegistry sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    /**
     * 트랜잭션이 커밋된 뒤에 보낸다.
     *
     * 커밋 전에 보내면 클라이언트가 이벤트를 받고 즉시 REST로 조회했을 때 아직 반영되지
     * 않은 상태를 읽는다. 롤백되면 일어나지도 않은 일을 알린 셈이 된다.
     */
    public void publishAfterCommit(Collection<UUID> userIds, ServerEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(userIds, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(userIds, event);
            }
        });
    }

    public void publish(Collection<UUID> userIds, ServerEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 직렬화 실패는 이벤트 정의 버그다. 조용히 넘기면 영영 모른다.
            log.error("이벤트 직렬화 실패 type={}", event.type(), e);
            return;
        }
        int delivered = 0;
        for (UUID userId : userIds) {
            for (WebSocketSession session : sessions.sessionsOf(userId)) {
                delivered += send(session, payload) ? 1 : 0;
            }
        }
        MDC.put(MdcKeys.STATE_TO, event.type().name());
        log.info("이벤트 발행 type={} targets={} delivered={}",
                event.type(), userIds.size(), delivered);
        MDC.remove(MdcKeys.STATE_TO);
    }

    private boolean send(WebSocketSession session, String payload) {
        try {
            // WebSocketSession은 동시 전송에 안전하지 않다. 같은 session으로 두 스레드가
            // 동시에 쓰면 프레임이 섞인다.
            synchronized (session) {
                if (!session.isOpen()) {
                    return false;
                }
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            // 끊긴 연결이다. 여기서 정리하지 않는다. close 콜백이 레지스트리를 정리한다.
            log.debug("이벤트 전송 실패 sessionId={}: {}", session.getId(), e.getMessage());
            return false;
        }
    }
}
