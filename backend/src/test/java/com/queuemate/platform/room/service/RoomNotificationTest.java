package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import com.queuemate.platform.room.notification.PushSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입장 · 나가기 · 방 닫힘 · 강퇴가 <b>누구에게 무엇을</b> 알리는지. 알림 채널을 실제로 구독해서 본다
 * (계약은 contracts/room-api.md "알림").
 * 돌리는 법: {@code ./gradlew test --tests '*RoomNotificationTest'}
 */
class RoomNotificationTest extends RoomTestSupport {

    @Test
    @DisplayName("누가 들어오면 방에 이미 있던 사람들이 ROOM_MEMBER_ENTERED 를 받는다. 들어온 본인은 받지 않는다")
    void entered() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.enter(r("r1"), u("u2"))).isEqualTo(EnterResult.ENTERED);

            List<PushSubscriber.Received> received = take(subscriber, 2);
            assertThat(received).extracting(PushSubscriber.Received::userId).containsExactlyInAnyOrder("host", "u1");
            for (PushSubscriber.Received one : received)
            {
                assertThat(one.envelope().get("type").asString()).isEqualTo("ROOM_MEMBER_ENTERED");
                assertThat(payloadOf(one)).isEqualTo(Map.of("roomId", "r1", "userId", "u2"));
            }
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("방을 만들 때와, 입장이 거절되거나 이미 들어와 있을 때는 아무에게도 알리지 않는다")
    void nothingWhenNobodyEntered() throws Exception
    {
        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            roomService.create(r("r1"), u("host"));
            assertThat(subscriber.nothingMore()).isTrue();

            for (int i = 1; i <= 4; i++)
            {
                roomMemberService.enter(r("r1"), u("u" + i));
            }
            take(subscriber, 1 + 2 + 3 + 4);

            assertThat(roomMemberService.enter(r("r1"), u("u5"))).isEqualTo(EnterResult.FULL);
            assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ALREADY_ENTERED);
            assertThat(roomMemberService.enter(r("r9"), u("u6"))).isEqualTo(EnterResult.ROOM_NOT_FOUND);
            redisTemplate.opsForHash().put(key("qm:user:active-request:u7"), "requestId", "req-1");
            assertThat(roomMemberService.enter(r("r1"), u("u7"))).isEqualTo(EnterResult.ACTIVE_REQUEST_EXISTS);

            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("누가 나가면 방에 남은 사람들이 ROOM_MEMBER_LEFT 를 받는다. 나간 본인은 받지 않는다")
    void left() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.LEFT);

            List<PushSubscriber.Received> received = take(subscriber, 2);
            assertThat(received).extracting(PushSubscriber.Received::userId).containsExactlyInAnyOrder("host", "u2");
            for (PushSubscriber.Received one : received)
            {
                assertThat(one.envelope().get("type").asString()).isEqualTo("ROOM_MEMBER_LEFT");
                assertThat(payloadOf(one)).isEqualTo(Map.of("roomId", "r1", "userId", "u1"));
            }
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("방장이 나가면 방에 있던 사람들이 ROOM_CLOSED 를 받는다 — 멤버 SET 이 이미 지워졌어도. 방장 본인은 받지 않는다")
    void closed() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.ROOM_CLOSED);

            List<PushSubscriber.Received> received = take(subscriber, 2);
            assertThat(received).extracting(PushSubscriber.Received::userId).containsExactlyInAnyOrder("u1", "u2");
            for (PushSubscriber.Received one : received)
            {
                assertThat(one.envelope().get("type").asString()).isEqualTo("ROOM_CLOSED");
                assertThat(payloadOf(one)).isEqualTo(Map.of("roomId", "r1"));
            }
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("이 방에 없는 사람의 나가기는 아무에게도 알리지 않는다")
    void nothingWhenNobodyLeft() throws Exception
    {
        roomService.create(r("r1"), u("host"));

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.leave(r("r1"), u("stranger"))).isEqualTo(LeaveResult.NOT_IN_ROOM);

            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("강퇴하면 방에 남은 사람들(방장 포함)과 강퇴된 본인이 ROOM_MEMBER_KICKED 를 받는다")
    void kicked() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.kick(r("r1"), u("host"), u("u1"))).isEqualTo(KickResult.KICKED);

            // 강퇴된 본인(u1)도 받는다 — 자기가 부른 요청이 아니라서 알림이 아니면 알 길이 없다
            List<PushSubscriber.Received> received = take(subscriber, 3);
            assertThat(received).extracting(PushSubscriber.Received::userId)
                    .containsExactlyInAnyOrder("host", "u2", "u1");
            for (PushSubscriber.Received one : received)
            {
                assertThat(one.envelope().get("type").asString()).isEqualTo("ROOM_MEMBER_KICKED");
                assertThat(payloadOf(one)).isEqualTo(Map.of("roomId", "r1", "userId", "u1"));
            }
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("거절된 강퇴는 아무에게도 알리지 않는다 — 방장이 아니다 · 자기 자신 · 방에 없는 대상 · 없는 방")
    void nothingWhenNobodyWasKicked() throws Exception
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.kick(r("r1"), u("u1"), u("u2"))).isEqualTo(KickResult.NOT_HOST);
            assertThat(roomMemberService.kick(r("r1"), u("host"), u("host"))).isEqualTo(KickResult.CANNOT_KICK_SELF);
            assertThat(roomMemberService.kick(r("r1"), u("host"), u("stranger"))).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);
            assertThat(roomMemberService.kick(r("r9"), u("host"), u("u1"))).isEqualTo(KickResult.ROOM_NOT_FOUND);

            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static List<PushSubscriber.Received> take(PushSubscriber subscriber, int count) throws InterruptedException
    {
        List<PushSubscriber.Received> taken = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            PushSubscriber.Received one = subscriber.next();
            assertThat(one).as("알림 %d개를 기다렸는데 %d개만 왔다", count, i).isNotNull();
            taken.add(one);
        }
        return taken;
    }

    /** {@code payload} 의 값(방 번호 · 사용자 번호 — 전부 문자열이다)을 이름표로 바꿔 돌려준다 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadOf(PushSubscriber.Received received)
    {
        Map<String, Object> payload = objectMapper.treeToValue(received.envelope().get("payload"), Map.class);
        Map<String, Object> labelled = new java.util.LinkedHashMap<>();
        payload.forEach((name, value) -> labelled.put(name, labelOf(String.valueOf(value))));
        return labelled;
    }
}
