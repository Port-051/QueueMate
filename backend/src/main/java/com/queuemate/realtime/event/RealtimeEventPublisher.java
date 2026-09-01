package com.queuemate.realtime.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.logging.MdcKeys;
import com.queuemate.realtime.session.SessionMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.UUID;

/**
 * 서버 이벤트를 대상 사용자들의 열린 session으로 보낸다.
 *
 * 전송 실패는 삼킨다. 이벤트를 못 받은 클라이언트는 재연결 후 REST로 현재 상태를
 * 다시 읽는다. 이벤트는 상태의 소스가 아니라 알림이다.
 *
 * 로컬 session에 먼저 보내고, 그다음 다른 노드에 넘긴다. 순서가 이렇게 된 이유는
 * Redis가 죽어도 같은 노드에 붙은 사용자에게는 이벤트가 가야 하기 때문이다.
 * 전부 Redis를 거치게 만들면 경로가 하나로 단순해지는 대신, 서버가 한 대일 때조차
 * Redis 장애가 이벤트를 전부 끊는다.
 */
@Component
public class RealtimeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventPublisher.class);

    private final SessionMessageSender sender;
    private final EventFanout fanout;
    private final ObjectMapper objectMapper;

    public RealtimeEventPublisher(SessionMessageSender sender, EventFanout fanout,
                                  ObjectMapper objectMapper) {
        this.sender = sender;
        this.fanout = fanout;
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

    /**
     * 한 세션에만 보낸다. 연결 직후 스냅샷처럼 방금 붙은 탭에만 필요한 경우에 쓴다.
     * 사용자 단위로 보내면 이미 상태를 갖고 있는 다른 탭까지 다시 그린다.
     *
     * 이 경로는 노드 간 전달을 하지 않는다. 대상 session이 이 프로세스에 있다는 것이
     * 이미 확정된 상황에서만 쓰기 때문이다.
     */
    public boolean publishTo(WebSocketSession session, ServerEvent event) {
        try {
            return sender.send(session, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.error("이벤트 직렬화 실패 type={}", event.type(), e);
            return false;
        }
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
        int delivered = sender.deliver(userIds, payload);
        // 이 노드에 전부 있었더라도 넘긴다. 같은 사용자가 다른 노드에도 탭을 열어 둘 수 있어
        // 로컬 전송 수만으로는 남은 대상이 있는지 알 수 없다.
        fanout.broadcast(userIds, event);

        MDC.put(MdcKeys.STATE_TO, event.type().name());
        log.info("이벤트 발행 type={} targets={} local={}", event.type(), userIds.size(), delivered);
        MDC.remove(MdcKeys.STATE_TO);
    }
}
