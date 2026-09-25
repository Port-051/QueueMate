package com.queuemate.platform.room.service;

import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.common.push.PushPublisher;
import com.queuemate.platform.party.board.BoardSignalPublisher;
import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.notification.PushSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 방 안의 일의 알림이 계약(contracts/events.md "Envelope" · contracts/room-api.md "알림")대로 나가는지 <b>구독해서</b> 본다.
 *
 * <p>합치기 전의 {@code room} 에는 자기 {@code PushPublisher} 가 있었고 이 테스트가 그것을 봤다({@code PushPublisherTest}). 2026-09-25 에 합치며
 * 이 앱의 {@code common.push.PushPublisher} 를 쓰게 바꿨다 — 방의 알림이 그 길({@link RoomNotifier})로도 같은 봉투 · 같은 채널로 나가는지를 본다.
 */
class RoomNotifierTest extends RoomTestSupport {

    @Autowired
    private RoomNotifier roomNotifier;

    @Test
    @DisplayName("봉투는 네 칸이고, 그 사용자의 채널(사용자 번호)로 나간다")
    void envelopeHasFourFields() throws Exception
    {
        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            roomNotifier.toUser(u("u1"), PushEventType.ROOM_CLOSED, Map.of("roomId", r("r1")));

            PushSubscriber.Received received = subscriber.next();
            assertThat(received).isNotNull();
            assertThat(received.userId()).isEqualTo("u1");

            JsonNode envelope = received.envelope();
            assertThat(envelope.propertyNames()).containsExactlyInAnyOrder("type", "eventId", "occurredAt", "payload");
            assertThat(envelope.get("type").asString()).isEqualTo("ROOM_CLOSED");
            assertThat(envelope.get("payload").get("roomId").asString()).isEqualTo(r("r1"));
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("eventId 는 매번 새 UUID 이고, occurredAt 은 밀리초까지의 ISO-8601 UTC 다")
    void eventIdAndOccurredAt() throws Exception
    {
        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            roomNotifier.toUser(u("u1"), PushEventType.ROOM_CLOSED, Map.of());
            roomNotifier.toUser(u("u1"), PushEventType.ROOM_CLOSED, Map.of());

            JsonNode first = subscriber.next().envelope();
            JsonNode second = subscriber.next().envelope();

            // UUID 가 아니면 여기서 예외다. SSE 의 id: 가 되므로 같으면 안 된다
            UUID.fromString(first.get("eventId").asString());
            assertThat(first.get("eventId").asString()).isNotEqualTo(second.get("eventId").asString());

            String occurredAt = first.get("occurredAt").asString();
            // 예: 2026-09-19T14:54:02.401Z — 끝의 Z 가 UTC 다. epoch 숫자나 나노초까지 찍힌 값이면 안 맞는다
            assertThat(occurredAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z");
            assertThat(Instant.parse(occurredAt)).isBetween(Instant.now().minusSeconds(10), Instant.now().plusSeconds(1));
        }
    }

    @Test
    @DisplayName("payload 가 null 이어도 봉투에는 빈 객체로 나간다")
    void nullPayloadBecomesEmptyObject() throws Exception
    {
        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            roomNotifier.toUser(u("u1"), PushEventType.ROOM_CLOSED, null);

            JsonNode payload = subscriber.next().envelope().get("payload");
            assertThat(payload.isObject()).isTrue();
            assertThat(payload.isEmpty()).isTrue();
        }
    }

    @Test
    @DisplayName("사용자 번호가 아닌 userId 에게는 보내지 않고, 예외도 내지 않는다 — 이미 성립한 입장 · 나가기를 뒤집지 않는다")
    void nonNumericUserIdIsSkipped() throws Exception
    {
        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThatCode(() -> roomNotifier.toUser("not-a-number", PushEventType.ROOM_CLOSED, Map.of()))
                    .doesNotThrowAnyException();

            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("Redis 에 붙지 못해도 예외를 밖으로 내보내지 않는다 — 알림 때문에 입장이 실패로 뒤집히면 안 된다")
    void doesNotThrowWhenRedisIsDown()
    {
        // 아무도 듣지 않는 포트다
        LettuceConnectionFactory dead = new LettuceConnectionFactory("localhost", 1);
        dead.afterPropertiesSet();
        try
        {
            StringRedisTemplate deadRedis = new StringRedisTemplate(dead);
            RoomNotifier notifier = new RoomNotifier(new PushPublisher(deadRedis, objectMapper),
                    new BoardSignalPublisher(deadRedis, objectMapper));

            assertThatCode(() -> notifier.toUser("1", PushEventType.ROOM_CLOSED, Map.of())).doesNotThrowAnyException();
            assertThatCode(notifier::boardChanged).doesNotThrowAnyException();
        }
        finally
        {
            dead.destroy();
        }
    }
}
