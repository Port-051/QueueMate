package com.queuemate.matching.notification;

import com.queuemate.matching.redisKeys.SharedKeys;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자 알림을 Redis Pub/Sub 에 발행한다.
 *
 * <p><b>이 앱의 몫은 publish 까지다.</b> 그 뒤 {@code app:realtime} 이 구독해서 SSE 로
 * 배달한다. 이 저장소에 {@code SseEmitter} / {@code WebSocketConfig} 를 넣지 마라
 * (CLAUDE.md §3).
 *
 * <pre>
 * app:matching --PUBLISH--> Redis --구독--> app:realtime --SSE--> 브라우저
 * </pre>
 *
 * <p><b>채널을 사용자별로 나눈 이유.</b> {@code app:realtime} 이 어떻게 구독하든
 * 우리 코드가 안 바뀌게 하려는 것이다. 지금은 패턴 구독
 * ({@code PSUBSCRIBE qm:pubsub:push:*} + 로컬 필터)이라 sticky session 이 필요 없고,
 * 인스턴스가 늘어 브로드캐스트 낭비가 문제가 되면 연결된 사용자만 정확히 구독
 * ({@code SUBSCRIBE qm:pubsub:push:{userId}})하는 쪽으로 저쪽만 바꾸면 된다.
 *
 * <p><b>JSON 문자열로 보내는 이유.</b> 자바 객체를 직렬화하면 받는 앱이 같은 클래스를
 * 가져야 하고, 그러면 envelope 하나 고칠 때마다 두 앱을 같이 배포해야 한다. 배포 단위를
 * 나눈 의미가 없어진다. 문자열이면 {@code app:realtime} 은 열어보지 않고 SSE {@code data:}
 * 에 그대로 실으면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 한 사용자에게 알림을 발행한다.
     *
     * <p><b>어떤 예외도 밖으로 내보내지 않는다.</b> 알림은 휘발성이고 (docs/07 §7-2)
     * 놓치면 클라이언트가 REST 재조회로 복구한다. 여기서 {@code DataAccessException} 을
     * 올리면 {@code GlobalExceptionHandler} 가 503 으로 바꿔, <b>이미 성립한 매칭이
     * 실패로 뒤집힌다.</b> INV-10 은 "Redis 없이 새 매칭을 만들지 마라"이지 "알림이
     * 실패하면 매칭을 취소해라"가 아니다.
     *
     * @param payload 담을 것이 없으면 {@code null} 이어도 된다. 빈 객체로 나간다
     */
    public void publish(String userId, PushEventType type, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(new Envelope(
                    type.name(),
                    // SSE 의 id: 필드가 되고 그대로 Last-Event-ID 다. 매번 새 값이어야 한다
                    UUID.randomUUID().toString(),
                    // Instant 는 UTC 다. LocalDateTime 을 쓰면 서버 타임존에 값이 흔들린다.
                    // 나노초까지 찍히면 로그만 지저분해지므로 밀리초에서 자른다
                    Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(),
                    // null 을 그대로 두면 payload: null 이 나가 받는 쪽이 필드를 읽다 터진다
                    payload == null ? Map.of() : payload));

            Long received = redis.convertAndSend(SharedKeys.pushChannel(userId), body);

            // received == 0 은 실패가 아니다. 그 사용자가 접속 중이 아닐 뿐이므로
            // 재시도하지 않는다. 구독자가 없는 메시지는 그냥 버려지는 것이 설계다
            log.debug("push type={} userId={} received={}", type, userId, received);

        } catch (Exception e) {
            log.warn("push 발행 실패 type={} userId={}: {}", type, userId, e.toString());
        }
    }

    /**
     * 여러 사용자에게 같은 알림을 발행한다.
     *
     * <p>파티 정원이 최대 5명이라 왕복도 최대 5번이다. 파이프라인으로 묶을 값이 아직 없다.
     */
    public void publishAll(Collection<String> userIds, PushEventType type, Map<String, Object> payload) {
        userIds.forEach(userId -> publish(userId, type, payload));
    }

    /**
     * contracts/events.md 가 정한 봉투. 네 칸이 고정이다.
     *
     * <p>{@code payload} 를 {@code Map} 으로 둔 것은 <b>임시다.</b> 계약이 14종 전부
     * payload 스키마를 정하지 않았다 (contracts/README.md "미해결 계약 구멍").
     * 스키마가 정해지면 이벤트별 record 로 바꾼다.
     */
    private record Envelope(String type, String eventId, String occurredAt,
                            Map<String, Object> payload) {
    }
}
