package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.HeartbeatResult;
import com.queuemate.platform.room.notification.PushSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 접속 확인({@code lua/heartbeat-room.lua})과 수명. 핵심은 <b>"정리 코드가 돌지 않아도 정리된다"</b>다 —
 * 나가기를 못 누르고 사라진 사람도, 앱이 죽은 동안의 방도 수명이 다하면 저절로 없어져야 한다.
 *
 * <p><b>수명을 2초로 줄여서 돌린다</b>(실제 기본값은 600초). 그래서 이 클래스는 다른 테스트보다 느리다 — 실제로 기다린다.
 * 돌리는 법: {@code ./gradlew test --tests '*RoomHeartbeatTest'}
 */
@TestPropertySource(properties = "platform.room.ttl-seconds=2")
class RoomHeartbeatTest extends RoomTestSupport {

    /** 수명(2초)이 확실히 지나도록 기다리는 시간 */
    private static final long PAST_TTL_MILLIS = 2600;

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
        roomService.create(r("warm-up"), u("warm-up-host"), Set.of());
        roomMemberService.enter(r("warm-up"), u("warm-up-member"));
        roomMemberService.heartbeat(r("warm-up"), u("warm-up-host"));
        roomMemberService.leave(r("warm-up"), u("warm-up-host"));
    }

    // ── 신호가 끊기면 저절로 사라진다 ───────────────────────────────────────

    @Test
    @DisplayName("아무도 신호를 보내지 않으면 방도 입장 표시도 전부 저절로 사라진다 — 앱이 죽어 있어도 마찬가지다")
    void everythingExpiresByItself() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));

        Thread.sleep(PAST_TTL_MILLIS);

        assertThat(ownKeys()).isEmpty();
        // 갇힌 사람이 없다 — 곧바로 새 방을 만들고 들어갈 수 있다
        assertThat(roomService.create(r("r2"), u("u1"), Set.of())).isEqualTo(CreateResult.CREATED);
        assertThat(roomMemberService.enter(r("r2"), u("host"))).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("신호를 보내는 동안에는 수명이 지나도 방에 남아 있다")
    void heartbeatKeepsThemAlive() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));

        for (int i = 0; i < 4; i++)
        {
            Thread.sleep(900);
            assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);
            assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.ALIVE);
        }

        // 수명(2초)을 훌쩍 넘긴 3.6초 뒤다
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(marker("u1")).isEqualTo("r1");
        assertThat(labelOf(redisTemplate.opsForValue().get(key("qm:room:r1:host")))).isEqualTo("host");
    }

    @Test
    @DisplayName("방장의 신호는 찾는 포지션 SET 의 수명도 늘린다 — 수명이 지나도 포지션을 골라 들어오는 방 그대로이고, 고른 포지션도 남아 있다(2026-09-30 — P-44)")
    void heartbeatKeepsTheNeedsAlive() throws InterruptedException
    {
        createPositionRoom("r1", "host", Set.of("TOP", "MID"));
        roomMemberService.enter(r("r1"), u("u1"), "MID");

        for (int i = 0; i < 4; i++)
        {
            Thread.sleep(900);
            assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);
            assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.ALIVE);
        }

        // 3.6초 뒤 — 찾는 포지션 SET 이 먼저 사라졌다면 남은 탑으로도 못 들어온다(포지션 방은 SET 에 없는 포지션을 -6 으로 거절한다). 미드는 u1 이 골라 SET 에서 빠져 있다
        assertThat(redisTemplate.opsForSet().members(key("qm:room:r1:needs"))).containsExactly("TOP");
        assertThat(position("r1", "u1")).isEqualTo("MID");
        assertThat(roomMemberService.enter(r("r1"), u("u2"), null)).isEqualTo(EnterResult.INVALID_POSITION);
        assertThat(roomMemberService.enter(r("r1"), u("u2"), "TOP")).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("신호가 끊기면 찾는 포지션 SET 도 방과 같이 저절로 사라진다")
    void needsExpireWithTheRoom() throws InterruptedException
    {
        createPositionRoom("r1", "host", Set.of("TOP", "MID"));
        roomMemberService.enter(r("r1"), u("u1"), "TOP");

        Thread.sleep(PAST_TTL_MILLIS);

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("확정한 방은 멤버의 신호도 찾는 포지션 SET 의 수명을 늘린다 — 멤버 HASH · 확정 표시 키와 같이(방장이 사라져도 방이 이어진다, D-23)")
    void confirmedMemberHeartbeatExtendsTheNeeds()
    {
        createPositionRoom("r1", "host", Set.of("TOP", "MID"));
        roomMemberService.enter(r("r1"), u("u1"), "MID");
        roomService.confirm(r("r1"), u("host"));
        redisTemplate.expire(key("qm:room:r1:needs"), java.time.Duration.ofMillis(500));

        assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.ALIVE);

        assertThat(redisTemplate.getExpire(key("qm:room:r1:needs"), TimeUnit.MILLISECONDS)).isGreaterThan(1000L);
    }

    // ── 방장의 연결 끊김 = 방이 없어진다 ────────────────────────────────────

    @Test
    @DisplayName("D-11 11번: 방의 수명은 방장의 신호만 늘린다. 멤버가 아무리 신호를 보내도 방장이 사라지면 방은 없어진다")
    void roomDiesWithoutTheHost() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));

        Thread.sleep(900);
        roomMemberService.heartbeat(r("r1"), u("u1"));
        Thread.sleep(900);
        roomMemberService.heartbeat(r("r1"), u("u1"));
        Thread.sleep(900);

        // 방장 키와 멤버 HASH 는 수명이 다했고, u1 의 입장 표시만 u1 의 신호로 살아 있다
        assertThat(redisTemplate.hasKey(key("qm:room:r1:host"))).isFalse();
        assertThat(marker("u1")).isEqualTo("r1");

        // u1 의 다음 신호가 "방이 없어졌다"를 받고, 그 자리에서 입장 표시가 지워진다
        assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.ROOM_CLOSED);
        assertThat(ownKeys()).isEmpty();
        assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.NOT_IN_ROOM);
    }

    @Test
    @DisplayName("일반 멤버의 신호는 방의 수명을 건드리지 않는다")
    void memberHeartbeatDoesNotExtendTheRoom() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        Thread.sleep(1100);
        long hostTtlBefore = redisTemplate.getExpire(key("qm:room:r1:host"), TimeUnit.MILLISECONDS);

        roomMemberService.heartbeat(r("r1"), u("u1"));

        assertThat(redisTemplate.getExpire(key("qm:room:r1:host"), TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(hostTtlBefore);
        assertThat(redisTemplate.getExpire(key("qm:user:active-room:u1"), TimeUnit.MILLISECONDS)).isGreaterThan(hostTtlBefore);
    }

    // ── 유령 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("말없이 사라진 멤버는 방장의 신호가 멤버 HASH 에서 빼고, 남은 사람들(방장 포함)이 ROOM_MEMBER_LEFT 를 받는다")
    void hostHeartbeatPrunesGhosts() throws Exception
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("ghost"));

        // ghost 만 신호를 보내지 않는다
        for (int i = 0; i < 3; i++)
        {
            Thread.sleep(900);
            roomMemberService.heartbeat(r("r1"), u("u1"));
            if (i < 2)
            {
                roomMemberService.heartbeat(r("r1"), u("host"));
            }
        }
        // ghost 의 입장 표시는 만료됐지만 SET 에는 이름이 남아 있다 — SET 의 원소에는 수명을 걸 수 없다
        assertThat(marker("ghost")).isNull();
        assertThat(members("r1")).contains("ghost");

        try (PushSubscriber subscriber = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);

            assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
            PushSubscriber.Received first = subscriber.next();
            PushSubscriber.Received second = subscriber.next();
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(Set.of(first.userId(), second.userId())).containsExactlyInAnyOrder("host", "u1");
            for (PushSubscriber.Received one : new PushSubscriber.Received[]{first, second})
            {
                assertThat(one.envelope().get("type").asString()).isEqualTo("ROOM_MEMBER_LEFT");
                assertThat(labelOf(one.envelope().get("payload").get("roomId").asString())).isEqualTo("r1");
                assertThat(labelOf(one.envelope().get("payload").get("userId").asString())).isEqualTo("ghost");
            }
            assertThat(subscriber.nothingMore()).isTrue();

            // 빠진 자리에 다른 사람이 들어올 수 있고, 다음 신호에는 더 뺄 사람도 알릴 것도 없다
            assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);
            assertThat(subscriber.nothingMore()).isTrue();
        }
    }

    @Test
    @DisplayName("유령을 뺄 때 그 사람의 입장 표시는 건드리지 않는다 — 다른 방에 가 있는 사람의 표시는 남의 것이다")
    void pruningDoesNotTouchMarkersOfOtherRooms()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        // 정상 흐름에서는 생기지 않는 어긋남이다. r1 의 멤버 HASH 에 이름이 남았는데 지금은 r2 에 있는 사람
        addMember("r1", "mover");
        redisTemplate.opsForValue().set(key("qm:user:active-room:mover"), r("r2"));

        roomMemberService.heartbeat(r("r1"), u("host"));

        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("mover")).isEqualTo("r2");
    }

    // ── 거절 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("이 방에 없는 사람의 신호는 NOT_IN_ROOM 이고 아무것도 만들거나 늘리지 않는다")
    void strangersHeartbeat()
    {
        roomService.create(r("r1"), u("host1"), Set.of());
        roomService.create(r("r2"), u("host2"), Set.of());
        Set<String> before = ownKeys();

        assertThat(roomMemberService.heartbeat(r("r1"), u("stranger"))).isEqualTo(HeartbeatResult.NOT_IN_ROOM);
        assertThat(roomMemberService.heartbeat(r("r1"), u("host2"))).isEqualTo(HeartbeatResult.NOT_IN_ROOM);
        assertThat(roomMemberService.heartbeat(r("r9"), u("host1"))).isEqualTo(HeartbeatResult.NOT_IN_ROOM);

        assertThat(ownKeys()).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    @DisplayName("나간 사람의 늦은 신호가 입장 표시를 되살리지 않는다")
    void lateHeartbeatAfterLeaving()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.leave(r("r1"), u("u1"));

        assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.NOT_IN_ROOM);

        assertThat(marker("u1")).isNull();
        assertThat(members("r1")).containsExactly("host");
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

}
