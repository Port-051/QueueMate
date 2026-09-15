package com.queuemate.realtime.event;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * contracts/events.md의 Envelope.
 *
 * eventId는 클라이언트의 중복 제거용이다. 재연결 직후 같은 이벤트를 다시 받을 수 있으므로
 * 클라이언트가 멱등하게 처리할 수 있어야 한다.
 */
public record ServerEvent(
        EventType type,
        UUID eventId,
        OffsetDateTime occurredAt,
        Map<String, Object> payload
) {

    public static ServerEvent of(EventType type, Map<String, Object> payload) {
        return new ServerEvent(type, UUID.randomUUID(), OffsetDateTime.now(), payload);
    }
}
