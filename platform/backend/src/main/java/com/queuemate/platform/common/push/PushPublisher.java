package com.queuemate.platform.common.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 사용자 한 명에게 가는 알림을 Redis Pub/Sub 에 발행한다 (CLAUDE.md §3.2). {@code matching} 의 {@code PushPublisher} 와 같은 방식이다.
 * <b>이 앱의 몫은 PUBLISH 까지다</b> — 구독해서 SSE 로 배달하는 것은 {@code notification} 이다. 이 앱에 {@code SseEmitter} 를 넣지 마라.
 *
 * <pre>
 * platform --PUBLISH qm:pubsub:push:{userId}--> Redis --구독--> notification --SSE--> 그 사용자의 브라우저 --GET …--> platform
 * </pre>
 *
 * <p><b>알림은 "다시 조회하라"는 신호다</b> — 닉네임 같은 데이터는 싣지 않고 가리키는 것의 id 만 싣는다. 그래서 알림이 가리키는 상태는
 * REST 로 조회할 수 있어야 한다. JSON 문자열로 보내므로 새 종류를 추가해도 {@code notification} 은 재배포하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * <b>트랜잭션 안에서 부르면 커밋된 뒤에 발행한다</b> — 되돌려진 변경(409 로 끝난 요청 등)을 알리지 않고, 알림을 받은 프런트가 다시 조회했을 때
     * 그 변경이 이미 보인다. 트랜잭션 밖에서 부르면 바로 발행한다.
     *
     * @param payload 칸의 순서를 계약과 맞추려면 순서가 있는 맵({@code LinkedHashMap})을 넘긴다. 부른 뒤에 고치지 마라
     */
    public void publishAfterCommit(Long userId, PushEventType type, Map<String, Object> payload)
    {
        if(!TransactionSynchronizationManager.isSynchronizationActive())
        {
            publish(userId, type, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit()
            {
                publish(userId, type, payload);
            }
        });
    }

    /**
     * 발행한다. <b>어떤 예외도 밖으로 내보내지 않는다</b> — 알림이 실패했다고 이미 커밋된 친구 요청이 실패로 보이면 안 된다.
     * 알림은 휘발성이고 놓치면 클라이언트가 REST 재조회로 복구한다. 대가로 발행이 틀려도 조용하다 — 그래서 구독해서 확인하는 테스트를 둔다.
     */
    void publish(Long userId, PushEventType type, Map<String, Object> payload)
    {
        try
        {
            String body = objectMapper.writeValueAsString(PushEnvelope.now(type.name(), payload));
            Long received = redis.convertAndSend(PushChannels.pushChannel(userId), body);
            // received == 0 은 실패가 아니다 — 그 사용자가 접속 중이 아닐 뿐이다. 재시도하지 않는다
            log.debug("push type={} userId={} received={}", type, userId, received);
        }
        catch(Exception e)
        {
            log.warn("push 발행 실패 type={} userId={}: {}", type, userId, e.toString());
        }
    }
}
