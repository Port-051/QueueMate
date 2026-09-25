package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 강퇴({@code lua/kick-room.lua})의 다섯 갈래와 동시성. 핵심은 둘이다 —
 * <b>방장만 강퇴할 수 있고</b>(방장 키와 비교한다), <b>거절된 강퇴는 아무것도 바꾸지 않는다.</b>
 * 강퇴된 사람의 입장 표시 키가 남으면 그 사람은 입장도 매칭도 못 한다.
 *
 * <p>돌리는 법과 6379 를 피하는 이유는 {@link RoomMemberServiceEnterTest} 와 같다 —
 * {@code ./gradlew test --tests '*RoomMemberServiceKickTest'}
 */
class RoomMemberServiceKickTest extends RoomTestSupport {

    // ── 강퇴했다 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("D-11 8번: 방장이 멤버를 강퇴하면 그 사람만 빠지고 입장 표시가 지워진다. 방과 나머지 사람은 그대로다")
    void hostKicksAMember()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u1"))).isEqualTo(KickResult.KICKED);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u2");
        assertThat(marker("u1")).isNull();
        assertThat(marker("u2")).isEqualTo("r1");
        assertThat(marker("host")).isEqualTo("r1");
        assertThat(host("r1")).isEqualTo("host");
    }

    @Test
    @DisplayName("강퇴된 사람은 곧바로 다른 방에 들어가거나 새 방을 만들 수 있다")
    void kickedUserIsFree()
    {
        roomService.create(r("r1"), u("host"));
        roomService.create(r("r2"), u("host2"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        roomMemberService.kick(r("r1"), u("host"), u("u1"));
        roomMemberService.kick(r("r1"), u("host"), u("u2"));

        assertThat(roomMemberService.enter(r("r2"), u("u1"))).isEqualTo(EnterResult.ENTERED);
        assertThat(roomService.create(r("r3"), u("u2"))).isEqualTo(CreateResult.CREATED);
    }

    @Test
    @DisplayName("강퇴된 자리에 다른 사람이 들어올 수 있다 — 정원은 멤버 SET 의 크기다")
    void kickFreesASeat()
    {
        roomService.create(r("r1"), u("host"));
        for (int i = 1; i <= 4; i++)
        {
            roomMemberService.enter(r("r1"), u("u" + i));
        }
        assertThat(roomMemberService.enter(r("r1"), u("u5"))).isEqualTo(EnterResult.FULL);

        roomMemberService.kick(r("r1"), u("host"), u("u1"));

        assertThat(roomMemberService.enter(r("r1"), u("u5"))).isEqualTo(EnterResult.ENTERED);
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u2", "u3", "u4", "u5");
    }

    @Test
    @DisplayName("지금의 동작: 강퇴된 사람이 같은 방에 다시 입장할 수 있다 — 재입장을 막을지는 미정이다 (CLAUDE.md §7)")
    void kickedUserCanEnterAgain()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.kick(r("r1"), u("host"), u("u1"));

        // 막기로 정해지면 이 테스트가 깨진다 — 그때 기대값을 바꾼다
        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ENTERED);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(marker("u1")).isEqualTo("r1");
    }

    // ── 거절 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("방장이 아닌 멤버 · 방 밖의 사람 · 다른 방의 방장이 부른 강퇴는 NOT_HOST 이고 아무것도 바뀌지 않는다")
    void onlyTheHostCanKick()
    {
        roomService.create(r("r1"), u("host"));
        roomService.create(r("r2"), u("host2"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        Map<String, Object> before = snapshot();

        assertThat(roomMemberService.kick(r("r1"), u("u1"), u("u2"))).isEqualTo(KickResult.NOT_HOST);
        assertThat(roomMemberService.kick(r("r1"), u("stranger"), u("u2"))).isEqualTo(KickResult.NOT_HOST);
        assertThat(roomMemberService.kick(r("r1"), u("host2"), u("u2"))).isEqualTo(KickResult.NOT_HOST);
        // 방장을 내보내려는 시도도, 자기 자신을 대상으로 한 것도 방장이 아니면 같은 답이다
        assertThat(roomMemberService.kick(r("r1"), u("u1"), u("host"))).isEqualTo(KickResult.NOT_HOST);
        assertThat(roomMemberService.kick(r("r1"), u("u1"), u("u1"))).isEqualTo(KickResult.NOT_HOST);

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("방장이 자기 자신을 강퇴하려 하면 CANNOT_KICK_SELF 이고 방이 그대로다 — 방장이 나가려면 나가기를 쓴다")
    void hostCannotKickSelf()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        Map<String, Object> before = snapshot();

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("host"))).isEqualTo(KickResult.CANNOT_KICK_SELF);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(host("r1")).isEqualTo("host");
        assertThat(marker("host")).isEqualTo("r1");
    }

    @Test
    @DisplayName("방에 없는 대상 · 이미 나간 대상 · 이미 강퇴된 대상은 TARGET_NOT_IN_ROOM 이고 아무것도 바뀌지 않는다")
    void targetNotInRoom()
    {
        roomService.create(r("r1"), u("host"));
        roomService.create(r("r2"), u("host2"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        roomMemberService.enter(r("r2"), u("u3"));
        roomMemberService.leave(r("r1"), u("u1"));
        roomMemberService.kick(r("r1"), u("host"), u("u2"));
        Map<String, Object> before = snapshot();

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("stranger"))).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);
        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u1"))).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);
        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u2"))).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);
        // 다른 방에 있는 사람이다. 그 사람의 입장 표시(r2)를 건드리면 안 된다
        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u3"))).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(marker("u3")).isEqualTo("r2");
    }

    @Test
    @DisplayName("없는 방 · 방장이 나가 없어진 방의 강퇴는 ROOM_NOT_FOUND 이고 아무것도 쓰지 않는다")
    void roomNotFound()
    {
        assertThat(roomMemberService.kick(r("r9"), u("host"), u("u1"))).isEqualTo(KickResult.ROOM_NOT_FOUND);
        assertThat(ownKeys()).isEmpty();

        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.leave(r("r1"), u("host"));

        // 방이 없어진 뒤에 늦게 도착한 강퇴다
        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u1"))).isEqualTo(KickResult.ROOM_NOT_FOUND);
        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("대상의 입장 표시가 다른 방을 가리키면 지우지 않는다 — 멤버 SET 에서만 뺀다")
    void kickDoesNotTouchAMarkerThatPointsToAnotherRoom()
    {
        roomService.create(r("r1"), u("host"));
        // 정상 흐름에서는 생기지 않는 어긋남이다(멤버 SET 에 이름만 남았고 본인은 다른 방에 가 있다).
        // 생겼다고 해도 남의 방 표시를 지우면 안 된다
        redisTemplate.opsForSet().add(key("qm:room:r1:members"), u("ghost"));
        redisTemplate.opsForValue().set(key("qm:user:active-room:ghost"), r("r2"));

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("ghost"))).isEqualTo(KickResult.KICKED);

        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("ghost")).isEqualTo("r2");
    }

    @Test
    @DisplayName("강퇴는 수명을 늘리지 않는다 — 방장 키 · 멤버 SET · 남은 사람의 입장 표시의 TTL 을 다시 걸지 않는다")
    void kickDoesNotExtendTtl()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        redisTemplate.expire(key("qm:room:r1:host"), Duration.ofSeconds(100));
        redisTemplate.expire(key("qm:room:r1:members"), Duration.ofSeconds(100));
        redisTemplate.expire(key("qm:user:active-room:u2"), Duration.ofSeconds(100));

        roomMemberService.kick(r("r1"), u("host"), u("u1"));

        assertThat(redisTemplate.getExpire(key("qm:room:r1:host"), TimeUnit.SECONDS)).isBetween(1L, 100L);
        assertThat(redisTemplate.getExpire(key("qm:room:r1:members"), TimeUnit.SECONDS)).isBetween(1L, 100L);
        assertThat(redisTemplate.getExpire(key("qm:user:active-room:u2"), TimeUnit.SECONDS)).isBetween(1L, 100L);
    }

    // ── 동시성 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("방장이 같은 사람을 동시에 100번 강퇴해도 한 번만 강퇴되고 나머지는 TARGET_NOT_IN_ROOM 이다")
    void sameTargetIsKickedOnce() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        Map<KickResult, AtomicInteger> counts = new ConcurrentHashMap<>();

        runConcurrently(100, i -> count(counts, roomMemberService.kick(r("r1"), u("host"), u("u1"))));

        assertThat(counts.get(KickResult.KICKED)).hasValue(1);
        assertThat(counts.get(KickResult.TARGET_NOT_IN_ROOM)).hasValue(99);
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u2");
        assertThat(marker("u1")).isNull();
    }

    @Test
    @DisplayName("방장이 강퇴하는 순간 그 사람이 나가기를 눌러도, 끝난 뒤 그 사람은 멤버 SET 에도 없고 입장 표시도 없다")
    void kickAndLeaveAtTheSameTime() throws InterruptedException
    {
        for (int round = 0; round < 50; round++)
        {
            deleteOwnKeys();
            roomService.create(r("r1"), u("host"));
            roomMemberService.enter(r("r1"), u("u1"));
            roomMemberService.enter(r("r1"), u("u2"));
            AtomicReference<KickResult> kicked = new AtomicReference<>();
            AtomicReference<LeaveResult> left = new AtomicReference<>();

            runConcurrently(2, i -> {
                if (i == 0)
                {
                    kicked.set(roomMemberService.kick(r("r1"), u("host"), u("u1")));
                }
                else
                {
                    left.set(roomMemberService.leave(r("r1"), u("u1")));
                }
            });

            // 둘 중 하나만 그 사람을 뺐다 — 강퇴가 이겼으면 나가기는 NOT_IN_ROOM, 나가기가 이겼으면 강퇴는 TARGET_NOT_IN_ROOM 이다
            boolean kickWon = kicked.get() == KickResult.KICKED && left.get() == LeaveResult.NOT_IN_ROOM;
            boolean leaveWon = kicked.get() == KickResult.TARGET_NOT_IN_ROOM && left.get() == LeaveResult.LEFT;
            assertThat(kickWon || leaveWon).as("round %d: kick=%s leave=%s", round, kicked.get(), left.get()).isTrue();

            assertThat(members("r1")).as("round %d", round).containsExactlyInAnyOrder("host", "u2");
            assertThat(marker("u1")).as("round %d", round).isNull();
            assertThat(marker("u2")).isEqualTo("r1");
            assertThat(host("r1")).isEqualTo("host");
        }
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /**
     * 이 테스트의 키 전부와 그 값(이름표로). "아무것도 바뀌지 않았다"를 비교할 때 쓴다(수명은 보지 않는다 — 시간이 가면 저절로 준다).
     * 활성 요청 키는 이 테스트가 만들지 않는다 — 방 키와 입장 표시 키뿐이다
     */
    private Map<String, Object> snapshot()
    {
        Map<String, Object> all = new TreeMap<>();
        for (String labelled : ownKeys())
        {
            if (labelled.endsWith(":members"))
            {
                all.put(labelled, members(labelled.split(":")[2]));
            }
            else
            {
                all.put(labelled, labelOf(redisTemplate.opsForValue().get(key(labelled))));
            }
        }
        return all;
    }

    private static void count(Map<KickResult, AtomicInteger> counts, KickResult result)
    {
        counts.computeIfAbsent(result, r -> new AtomicInteger()).incrementAndGet();
    }

}
