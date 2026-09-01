package com.queuemate.realtime.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.realtime.session.NodeIdentity;
import com.queuemate.realtime.session.SessionMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 다른 서버에 붙어 있는 사용자에게 이벤트를 넘긴다.
 *
 * WebSocket session은 그 연결을 받은 프로세스의 메모리에만 있다. 서버가 여러 대면
 * 파티원 둘이 서로 다른 서버에 붙는 일이 흔하고, 그때 한쪽에서 만든 이벤트가
 * 다른 쪽에 가지 않는다. signaling까지 못 가면 음성 통화가 아예 성립하지 않는다.
 *
 * 채널을 하나만 둔다. 모든 노드가 모든 이벤트를 받고 자기에게 없는 대상은 버린다.
 * 대상이 어느 노드에 있는지 표를 만들어 그 노드에만 보내는 방법도 있지만, 그 표가
 * 낡으면 메시지가 조용히 사라진다. 지금 고치려는 문제가 정확히 그것이라 택하지 않았다.
 * 노드 수가 늘면 낭비가 노드 수에 비례하므로 그때 다시 본다.
 */
@Component
public class EventFanout implements MessageListener {

    public static final String CHANNEL = "qm:ws:fanout";

    private static final Logger log = LoggerFactory.getLogger(EventFanout.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final NodeIdentity node;
    private final SessionMessageSender sender;

    public EventFanout(StringRedisTemplate redis, ObjectMapper objectMapper,
                       NodeIdentity node, SessionMessageSender sender) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.node = node;
        this.sender = sender;
    }

    /**
     * 다른 노드에 전달을 부탁한다. 로컬 전송은 부르는 쪽이 이미 끝냈다.
     *
     * 실패해도 예외를 올리지 않는다. Redis가 죽어도 같은 노드에 붙은 사용자에게는
     * 이벤트가 갔다. 여기서 막으면 단일 노드일 때조차 이벤트가 끊긴다.
     * 이벤트는 상태의 소스가 아니라 알림이고, 클라이언트는 재연결 스냅샷으로 메운다.
     */
    public void broadcast(Collection<UUID> userIds, ServerEvent event) {
        if (userIds.isEmpty()) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(
                    new FanoutMessage(node.id(), List.copyOf(userIds), event));
            redis.convertAndSend(CHANNEL, envelope);
        } catch (DataAccessException e) {
            log.warn("노드 간 이벤트 전달 실패 type={} targets={}", event.type(), userIds.size());
        } catch (Exception e) {
            log.error("노드 간 이벤트 직렬화 실패 type={}", event.type(), e);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        FanoutMessage received;
        try {
            received = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), FanoutMessage.class);
        } catch (Exception e) {
            // 다른 버전의 노드가 보낸 형식일 수 있다. 한 건을 못 읽었다고 구독을 끊지 않는다.
            log.warn("노드 간 이벤트 해석 실패: {}", e.getMessage());
            return;
        }
        if (node.id().equals(received.nodeId())) {
            // 내가 보낸 것이다. 발행하기 전에 이미 로컬 session으로 보냈다.
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(received.event());
        } catch (Exception e) {
            log.error("전달받은 이벤트 직렬화 실패 type={}", received.event().type(), e);
            return;
        }
        int delivered = sender.deliver(received.userIds(), payload);
        if (delivered > 0) {
            log.debug("다른 노드의 이벤트 전달 type={} from={} delivered={}",
                    received.event().type(), received.nodeId(), delivered);
        }
    }

    /** contracts/events.md의 노드 간 envelope. */
    public record FanoutMessage(UUID nodeId, List<UUID> userIds, ServerEvent event) {
    }
}
