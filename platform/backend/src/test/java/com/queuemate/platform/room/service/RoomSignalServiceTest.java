package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.SignalResult;
import com.queuemate.platform.room.notification.PushSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시그널 전달({@code lua/signal-room.lua}). 지킬 것은 둘이다 —
 * <b>같은 방의 두 사람이 아니면 아무것도 발행하지 않는다</b>, <b>내용은 글자 그대로 간다</b>.
 * 알림 채널을 실제로 구독해서 본다. 돌리는 법: {@code ./gradlew test --tests '*RoomSignalServiceTest'}
 */
class RoomSignalServiceTest extends RoomTestSupport {

    /** 서버가 뜻을 모르는 모양이다. 중첩 · 배열 · null · 특수문자 · 줄바꿈이 든 SDP 를 일부러 넣었다 */
    private static final String SIGNAL = """
            {"kind":"offer","negotiationId":7,"sdp":"v=0\\r\\no=- 46117 2 IN IP4 127.0.0.1\\r\\na=ice-ufrag:\\"x\\"/한글",
             "candidates":[{"candidate":"candidate:1 1 UDP 2122 192.0.2.1 5000 typ host","sdpMLineIndex":0},null],
             "extra":{"nested":{"deep":true}},"nothing":null}""";

    @Autowired
    private RoomSignalService roomSignalService;

    @Test
    @DisplayName("같은 방의 상대에게 WEBRTC_SIGNAL 이 가고, 보낸 내용이 글자 그대로 실려 있다. 받는 사람 한 명에게만 간다")
    void deliversTheSignalVerbatim() throws Exception
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        JsonNode signal = objectMapper.readTree(SIGNAL);

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomSignalService.send(r("r1"), u("u1"), u("u2"), signal)).isEqualTo(SignalResult.SENT);

            PushSubscriber.Received received = subscriber.next();
            assertThat(received).isNotNull();
            assertThat(received.userId()).isEqualTo("u2");
            assertThat(received.envelope().get("type").asString()).isEqualTo("WEBRTC_SIGNAL");

            JsonNode payload = received.envelope().get("payload");
            assertThat(payload.propertyNames()).containsExactlyInAnyOrder("roomId", "fromUserId", "signal");
            assertThat(labelOf(payload.get("roomId").asString())).isEqualTo("r1");
            assertThat(labelOf(payload.get("fromUserId").asString())).isEqualTo("u1");
            // 서버가 해석했다면 어딘가 달라진다 — 칸이 빠지거나, null 이 사라지거나, 이스케이프가 바뀐다
            assertThat(payload.get("signal")).isEqualTo(signal);
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("방장과 멤버 사이에도, 양방향으로 간다")
    void betweenHostAndMember() throws Exception
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        JsonNode signal = objectMapper.readTree("{\"kind\":\"answer\"}");

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomSignalService.send(r("r1"), u("host"), u("u1"), signal)).isEqualTo(SignalResult.SENT);
            assertThat(subscriber.next().userId()).isEqualTo("u1");

            assertThat(roomSignalService.send(r("r1"), u("u1"), u("host"), signal)).isEqualTo(SignalResult.SENT);
            assertThat(subscriber.next().userId()).isEqualTo("host");
        }
    }

    @Test
    @DisplayName("보낸 사람이 이 방에 없으면 NOT_IN_ROOM 이고 아무것도 발행하지 않는다 — 아무 방에도 없든, 다른 방에 있든, 이미 나갔든")
    void senderNotInRoom() throws Exception
    {
        roomService.create(r("r1"), u("host1"), Set.of());
        roomService.create(r("r2"), u("host2"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("gone"));
        roomMemberService.leave(r("r1"), u("gone"));
        JsonNode signal = objectMapper.readTree("{}");

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            for (String sender : new String[]{"stranger", "host2", "gone"})
            {
                assertThat(roomSignalService.send(r("r1"), u(sender), u("u1"), signal))
                        .as(sender).isEqualTo(SignalResult.NOT_IN_ROOM);
            }

            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("받는 사람이 이 방에 없으면 TARGET_NOT_IN_ROOM 이고 아무것도 발행하지 않는다 — 다른 방에 있는 사람에게 시그널을 쏠 수 없다")
    void targetNotInRoom() throws Exception
    {
        roomService.create(r("r1"), u("host1"), Set.of());
        roomService.create(r("r2"), u("host2"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        JsonNode signal = objectMapper.readTree("{}");

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            for (String target : new String[]{"stranger", "host2"})
            {
                assertThat(roomSignalService.send(r("r1"), u("u1"), u(target), signal))
                        .as(target).isEqualTo(SignalResult.TARGET_NOT_IN_ROOM);
            }

            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("없는 방에는 보낼 수 없다")
    void noSuchRoom() throws Exception
    {
        assertThat(roomSignalService.send(r("r9"), u("u1"), u("u2"), objectMapper.readTree("{}")))
                .isEqualTo(SignalResult.NOT_IN_ROOM);
    }

    @Test
    @DisplayName("시그널은 Redis 에 남지 않는다 — 전달하고 나면 키가 하나도 늘지 않는다")
    void signalsAreNotStored() throws Exception
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        Set<String> before = ownKeys();

        roomSignalService.send(r("r1"), u("host"), u("u1"), objectMapper.readTree(SIGNAL));

        assertThat(ownKeys()).containsExactlyInAnyOrderElementsOf(before);
    }
}
