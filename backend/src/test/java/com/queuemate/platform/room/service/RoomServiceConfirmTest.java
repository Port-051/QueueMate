package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.HeartbeatResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import com.queuemate.platform.room.notification.PushSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방장 확정({@code lua/confirm-room.lua})과, 확정이 입장 · 접속 확인 · 나가기에 미치는 것.
 * 핵심은 둘이다 — <b>확정된 방에는 새 사람이 못 들어온다</b>, <b>확정은 되돌릴 수 없고 저절로 풀리지도 않는다.</b>
 *
 * <p>수명을 2초로 줄여 실제로 기다리는 테스트가 있다. 돌리는 법과 6379 를 피하는 이유는 {@link RoomMemberServiceEnterTest} 와 같다 —
 * {@code ./gradlew test --tests '*RoomServiceConfirmTest'}
 */
@TestPropertySource(properties = "platform.room.ttl-seconds=2")
class RoomServiceConfirmTest extends RoomTestSupport {

    @BeforeEach
    void warmUpScripts()
    {
        warmUp();
    }

    /**
     * 이 클래스는 수명이 2초다. JVM 이 막 떴을 때는 첫 스크립트 실행과 첫 알림 발행이 2초를 넘겨서(2026-09-20 에 2.5초를 쟀다),
     * 방을 만들자마자 만료돼 버린다 — 맨 처음 도는 테스트만 가끔 ROOM_NOT_FOUND 로 실패했다. 한 바퀴 돌려 데운 뒤에 시작한다.
     */
    private void warmUp()
    {
        roomService.create(r("warm-up"), u("warm-up-host"));
        roomMemberService.enter(r("warm-up"), u("warm-up-member"));
        roomMemberService.heartbeat(r("warm-up"), u("warm-up-host"));
        roomMemberService.leave(r("warm-up"), u("warm-up-host"));
    }

    // ── 확정 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("확정은 그 순간의 멤버를 돌려준다 — 게시판이 파티원을 따로 읽지 않고 이것으로 적는다(2026-09-25 2단계). 거절이면 비어 있다")
    void confirmReturnsTheMembers()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomService.confirm(r("r1"), u("u1")).members()).isEmpty();
        assertThat(roomService.confirm(r("r1"), u("host")).members().stream().map(this::labelOf))
                .containsExactlyInAnyOrder("host", "u1");
        // 이미 확정된 방 — 스크립트가 멤버를 돌려주지 않는다
        assertThat(roomService.confirm(r("r1"), u("host")).members()).isEmpty();
    }

    @Test
    @DisplayName("방장이 확정하면 확정 표시 키가 수명과 함께 쓰인다. 값은 roomId 다 — 방장의 입장 표시를 덮어쓰지 않는다")
    void confirmWritesTheMarker()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.CONFIRMED);

        assertThat(labelOf(redisTemplate.opsForValue().get(key("qm:room:r1:confirmed")))).isEqualTo("r1");
        assertThat(redisTemplate.getExpire(key("qm:room:r1:confirmed"), TimeUnit.MILLISECONDS)).isBetween(1L, 2000L);
        // KEYS 의 순서가 어긋나면 스크립트가 방장의 입장 표시 키에 쓴다 — 값이 같아서 눈에 안 띄므로 수명으로 본다
        assertThat(ownKeys()).containsExactlyInAnyOrder(
                "qm:room:r1:host", "qm:room:r1:members", "qm:room:r1:confirmed",
                "qm:user:active-room:host", "qm:user:active-room:u1");
    }

    @Test
    @DisplayName("다시 확정하면 ALREADY_CONFIRMED 이고 아무것도 바뀌지 않는다. 확정 뒤 혼자 남은 방장이 눌러도 마찬가지다")
    void confirmAgain()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));

        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.ALREADY_CONFIRMED);

        roomMemberService.leave(r("r1"), u("u1"));
        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.ALREADY_CONFIRMED);
    }

    @Test
    @DisplayName("혼자서는 확정할 수 없고, 거절된 뒤에 확정 표시가 남지 않는다 — 남으면 아무도 못 들어오는 방이 된다")
    void notEnoughMembers()
    {
        roomService.create(r("r1"), u("host"));

        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.NOT_ENOUGH_MEMBERS);

        assertThat(redisTemplate.hasKey(key("qm:room:r1:confirmed"))).isFalse();
        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ENTERED);
        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.CONFIRMED);
    }

    @Test
    @DisplayName("방장만 확정할 수 있다 — 멤버, 방 밖의 사람, roomId 를 남의 방으로 바꿔 부른 다른 방의 방장은 NOT_HOST 다")
    void onlyTheHostConfirms()
    {
        roomService.create(r("r1"), u("host1"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.create(r("r2"), u("host2"));
        roomMemberService.enter(r("r2"), u("u2"));

        for (String caller : new String[]{"u1", "stranger", "host2"})
        {
            assertThat(roomService.confirm(r("r1"), u(caller)).result()).as(caller).isEqualTo(ConfirmResult.NOT_HOST);
        }

        assertThat(redisTemplate.hasKey(key("qm:room:r1:confirmed"))).isFalse();
        assertThat(redisTemplate.hasKey(key("qm:room:r2:confirmed"))).isFalse();
    }

    @Test
    @DisplayName("없는 방은 ROOM_NOT_FOUND 이고 아무것도 생기지 않는다")
    void noSuchRoom()
    {
        assertThat(roomService.confirm(r("r9"), u("host")).result()).isEqualTo(ConfirmResult.ROOM_NOT_FOUND);

        assertThat(ownKeys()).isEmpty();
    }

    // ── 확정된 방 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("확정된 방에는 새 사람이 못 들어온다 — 멤버가 나가 자리가 비어도 마찬가지다")
    void nobodyNewEntersAConfirmedRoom()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        roomService.confirm(r("r1"), u("host"));

        assertThat(roomMemberService.enter(r("r1"), u("newcomer"))).isEqualTo(EnterResult.ROOM_CONFIRMED);

        roomMemberService.leave(r("r1"), u("u2"));
        assertThat(roomMemberService.enter(r("r1"), u("newcomer"))).isEqualTo(EnterResult.ROOM_CONFIRMED);
        // 나간 파티원도 새 사람이다 — 되돌아올 수 없다
        assertThat(roomMemberService.enter(r("r1"), u("u2"))).isEqualTo(EnterResult.ROOM_CONFIRMED);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(redisTemplate.hasKey(key("qm:user:active-room:newcomer"))).isFalse();
    }

    @Test
    @DisplayName("확정된 방에 이미 있는 사람의 재입장(새로고침)은 그대로 ALREADY_ENTERED 다")
    void membersOfAConfirmedRoomCanRefresh()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));

        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ALREADY_ENTERED);
        assertThat(roomMemberService.enter(r("r1"), u("host"))).isEqualTo(EnterResult.ALREADY_ENTERED);
    }

    @Test
    @DisplayName("확정은 저절로 풀리지 않는다 — 방장의 접속 확인이 확정 표시의 수명도 늘린다")
    void confirmationSurvivesTheTtl() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));

        for (int i = 0; i < 4; i++)
        {
            Thread.sleep(900);
            assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);
            roomMemberService.heartbeat(r("r1"), u("u1"));
        }

        // 수명(2초)을 훌쩍 넘긴 3.6초 뒤다. 확정 표시가 만료됐다면 새 사람이 들어온다
        assertThat(redisTemplate.hasKey(key("qm:room:r1:confirmed"))).isTrue();
        assertThat(roomMemberService.enter(r("r1"), u("newcomer"))).isEqualTo(EnterResult.ROOM_CONFIRMED);
    }

    @Test
    @DisplayName("확정하지 않은 방에서 방장의 접속 확인이 확정 표시 키를 만들지 않는다")
    void heartbeatDoesNotCreateTheMarker()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        roomMemberService.heartbeat(r("r1"), u("host"));

        assertThat(redisTemplate.hasKey(key("qm:room:r1:confirmed"))).isFalse();
        assertThat(roomMemberService.enter(r("r1"), u("u2"))).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("확정한 방은 방장이 나가도 없어지지 않는다 — 남은 사람이 방장을 넘겨받고 확정 표시도 그대로다")
    void hostLeavingAConfirmedRoomHandsOverTheHostRole()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));

        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.LEFT);

        assertThat(labelOf(redisTemplate.opsForValue().get(key("qm:room:r1:host")))).isEqualTo("u1");
        assertThat(members("r1")).containsExactly("u1");
        assertThat(redisTemplate.hasKey(key("qm:room:r1:confirmed"))).isTrue();
        // 나간 방장은 매칭도 다른 방 입장도 할 수 있어야 한다 — 입장 표시가 남으면 안 된다
        assertThat(redisTemplate.hasKey(key("qm:user:active-room:host"))).isFalse();
        assertThat(labelOf(redisTemplate.opsForValue().get(key("qm:user:active-room:u1")))).isEqualTo("r1");
        // 방장 키에 수명이 남아 있어야 한다. 수명 없는 키가 되면 방이 영원히 남는다
        assertThat(redisTemplate.getExpire(key("qm:room:r1:host"))).isPositive();
        // 확정한 방이므로 빈자리가 생겨도 새 사람은 못 들어온다
        assertThat(roomMemberService.enter(r("r1"), u("u9"))).isEqualTo(EnterResult.ROOM_CONFIRMED);
    }

    @Test
    @DisplayName("방장을 넘겨받는 사람은 남은 멤버 가운데 한 명이고, 넘겨받은 사람은 방장으로서 강퇴할 수 있다")
    void theNewHostIsOneOfTheRemainingMembers()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        roomService.confirm(r("r1"), u("host"));

        roomMemberService.leave(r("r1"), u("host"));

        String newHost = labelOf(redisTemplate.opsForValue().get(key("qm:room:r1:host")));
        assertThat(newHost).isIn("u1", "u2");
        String other = newHost.equals("u1") ? "u2" : "u1";
        assertThat(roomMemberService.kick(r("r1"), u(other), u(newHost))).isEqualTo(KickResult.NOT_HOST);
        assertThat(roomMemberService.kick(r("r1"), u(newHost), u(other))).isEqualTo(KickResult.KICKED);
    }

    @Test
    @DisplayName("확정한 방이라도 넘겨받을 사람이 없으면 방장이 나갈 때 방이 통째로 없어진다 — 같은 roomId 로 다시 만든 방은 확정돼 있지 않다")
    void hostLeavingAConfirmedRoomAloneRemovesEverything()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));
        roomMemberService.leave(r("r1"), u("u1"));

        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.ROOM_CLOSED);

        assertThat(ownKeys()).isEmpty();
        assertThat(roomService.create(r("r1"), u("u1"))).isEqualTo(CreateResult.CREATED);
        assertThat(roomMemberService.enter(r("r1"), u("host"))).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("확정하지 않은 방은 예전 그대로다 — 방장이 나가면 남은 사람이 있어도 방이 통째로 없어진다")
    void hostLeavingAnUnconfirmedRoomStillClosesIt()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.ROOM_CLOSED);

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("확정한 방의 방장이 말없이 사라지면, 계속 신호를 보내던 멤버가 방장을 넘겨받고 옛 방장은 멤버에서 빠진다")
    void aMemberTakesOverWhenTheHostOfAConfirmedRoomVanishes() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));

        // 방장은 신호를 보내지 않는다. u1 만 0.5초마다 보낸다 — 수명(2초)이 지나 방장 키와 방장의 입장 표시가 만료된다
        for (int i = 0; i < 7; i++)
        {
            Thread.sleep(500);
            assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).as("signal %d", i).isEqualTo(HeartbeatResult.ALIVE);
        }

        assertThat(labelOf(redisTemplate.opsForValue().get(key("qm:room:r1:host")))).isEqualTo("u1");
        assertThat(members("r1")).containsExactly("u1");
        assertThat(redisTemplate.hasKey(key("qm:room:r1:confirmed"))).isTrue();
        assertThat(redisTemplate.getExpire(key("qm:room:r1:host"))).isPositive();
    }

    @Test
    @DisplayName("방장이 말없이 사라지면 확정된 방도 수명이 다해 확정 표시까지 전부 사라진다")
    void confirmedRoomExpiresWithoutTheHost() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomService.confirm(r("r1"), u("host"));

        Thread.sleep(2600);

        assertThat(ownKeys()).isEmpty();
    }

    // ── 알림 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("확정하면 그 순간 방에 있던 전원(방장 포함)이 ROOM_CONFIRMED 를 받고, payload 의 members 가 실제 멤버와 같다")
    void everyoneIsNotified() throws Exception
    {
        // 구독자를 먼저 만든다. 이 클래스는 수명이 2초라, 방을 만든 뒤에 구독을 걸면 그 사이에 방이 만료될 수 있다
        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            roomService.create(r("r1"), u("host"));
            roomMemberService.enter(r("r1"), u("u1"));
            roomMemberService.enter(r("r1"), u("u2"));
            // 입장 알림 셋(u1 입장 → host, u2 입장 → host · u1)을 먼저 비운다
            for (int i = 0; i < 3; i++)
            {
                assertThat(subscriber.next()).isNotNull();
            }

            assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.CONFIRMED);

            List<String> recipients = new ArrayList<>();
            for (int i = 0; i < 3; i++)
            {
                PushSubscriber.Received received = subscriber.next();
                assertThat(received).isNotNull();
                recipients.add(received.userId());
                assertThat(received.envelope().get("type").asString()).isEqualTo("ROOM_CONFIRMED");
                JsonNode payload = received.envelope().get("payload");
                assertThat(labelOf(payload.get("roomId").asString())).isEqualTo("r1");
                List<String> payloadMembers = new ArrayList<>();
                payload.get("members").forEach(node -> payloadMembers.add(labelOf(node.asString())));
                assertThat(payloadMembers).containsExactlyInAnyOrderElementsOf(members("r1"));
            }
            assertThat(recipients).containsExactlyInAnyOrder("host", "u1", "u2");
            assertThat(subscriber.nothingMore()).isTrue();

            // 거절과 재확정은 아무에게도 가지 않는다
            roomService.confirm(r("r1"), u("host"));
            roomService.confirm(r("r1"), u("u1"));
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    // ── 동시성 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("확정하는 순간 여러 명이 입장을 눌러도, 확정이 돌려준 멤버와 실제 방의 멤버가 같다 — 확정 뒤에 끼어든 사람이 없다")
    void nobodySlipsInDuringConfirmation() throws InterruptedException
    {
        users("c", 21);
        for (int round = 0; round < 20; round++)
        {
            deleteOwnKeys();
            roomService.create(r("r1"), u("host"));
            roomMemberService.enter(r("r1"), u("u0"));
            Map<EnterResult, AtomicInteger> entered = new ConcurrentHashMap<>();

            try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
            {
                runConcurrently(21, i -> {
                    if (i == 10)
                    {
                        roomService.confirm(r("r1"), u("host"));
                    }
                    else
                    {
                        EnterResult result = roomMemberService.enter(r("r1"), u("c" + i));
                        entered.computeIfAbsent(result, r -> new AtomicInteger()).incrementAndGet();
                    }
                });

                // 확정 알림의 members 가 "확정한 그 순간의 멤버"다. 그 뒤에 들어온 사람이 있으면 실제 SET 이 더 크다
                List<String> confirmedMembers = null;
                for (PushSubscriber.Received received = subscriber.next(); received != null; received = subscriber.next())
                {
                    if ("ROOM_CONFIRMED".equals(received.envelope().get("type").asString()))
                    {
                        confirmedMembers = new ArrayList<>();
                        for (JsonNode node : received.envelope().get("payload").get("members"))
                        {
                            confirmedMembers.add(labelOf(node.asString()));
                        }
                        break;
                    }
                }
                assertThat(confirmedMembers).as("round %d", round).isNotNull();
                assertThat(members("r1")).as("round %d", round).containsExactlyInAnyOrderElementsOf(confirmedMembers);
                assertThat(members("r1").size()).isLessThanOrEqualTo(5);
            }
        }
    }
}
