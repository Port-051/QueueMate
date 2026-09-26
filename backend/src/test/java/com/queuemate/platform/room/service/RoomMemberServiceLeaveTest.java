package com.queuemate.platform.room.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.LeaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 나가기({@code lua/leave-room.lua})의 세 갈래와 동시성. 핵심은 하나다 —
 * <b>방이 없어진 뒤에 입장 표시 키가 하나라도 남으면 그 사람은 입장도 매칭도 못 한다.</b>
 *
 * <p>돌리는 법과 6379 를 피하는 이유는 {@link RoomMemberServiceEnterTest} 와 같다 —
 * {@code ./gradlew test --tests '*RoomMemberServiceLeaveTest'}
 */
class RoomMemberServiceLeaveTest extends RoomTestSupport {

    // ── 세 갈래 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버가 나가면 그 사람만 빠진다. 방과 나머지 사람은 그대로다")
    void memberLeaves()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.LEFT);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u2");
        assertThat(marker("u1")).isNull();
        assertThat(marker("u2")).isEqualTo("r1");
        assertThat(host("r1")).isEqualTo("host");
    }

    @Test
    @DisplayName("나갔다가 다시 들어올 수 있고, 나간 자리에 다른 사람이 들어올 수 있다")
    void canEnterAgainAfterLeaving()
    {
        roomService.create(r("r1"), u("host"));
        for (int i = 1; i <= 4; i++)
        {
            roomMemberService.enter(r("r1"), u("u" + i));
        }
        assertThat(roomMemberService.enter(r("r1"), u("u5"))).isEqualTo(EnterResult.FULL);

        roomMemberService.leave(r("r1"), u("u1"));

        assertThat(roomMemberService.enter(r("r1"), u("u5"))).isEqualTo(EnterResult.ENTERED);
        // 나간 사람은 다른 방에도 갈 수 있다
        roomService.create(r("r2"), u("host2"));
        assertThat(roomMemberService.enter(r("r2"), u("u1"))).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("D-11 11번: 방장이 나가면 방이 없어지고, 남아 있던 전원의 입장 표시 키가 함께 지워진다. 그 글도 그 자리에서 만료된다(2026-09-25 — 확정 전에는 방과 글이 같이 끝난다)")
    void hostLeavesAndTheRoomIsClosed()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.ROOM_CLOSED);

        // 하나라도 남으면 그 사람은 없는 방에 갇혀 입장도 매칭도 못 한다
        assertThat(ownKeys()).isEmpty();
        assertThat(postStatus("r1")).isEqualTo("EXPIRED");
        // 입장은 글부터 본다 — 글이 끝났으니 방의 404 ROOM_NOT_FOUND 가 아니라 글의 409 다
        assertThatThrownBy(() -> roomMemberService.enter(r("r1"), u("u3")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("POST_NOT_RECRUITING"));
        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("멤버가 나가는 것과 확정한 방의 방장이 나가는 것(승계)은 글을 건드리지 않는다")
    void onlyClosingExpiresThePost()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        assertThat(roomMemberService.leave(r("r1"), u("u2"))).isEqualTo(LeaveResult.LEFT);
        assertThat(postStatus("r1")).isEqualTo("RECRUITING");

        roomService.confirm(r("r1"), u("host"));
        // 확정은 글의 기록을 거치지 않고 방에만 했다 — 글이 모집 중으로 남아 있어도 승계면 만료시키지 않는다는 것을 본다
        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.LEFT);
        assertThat(host("r1")).isEqualTo("u1");
        assertThat(postStatus("r1")).isEqualTo("RECRUITING");
    }

    @Test
    @DisplayName("방이 없어진 뒤, 있던 사람들은 곧바로 다른 방에 들어가거나 새 방을 만들 수 있다")
    void everyoneIsFreeAfterTheRoomIsClosed()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.leave(r("r1"), u("host"));

        assertThat(roomService.create(r("r2"), u("u1")).name()).isEqualTo("CREATED");
        assertThat(roomMemberService.enter(r("r2"), u("host"))).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("이 방에 없는 사람 · 이미 나간 사람 · 없는 방은 NOT_IN_ROOM 이고 아무것도 지우지 않는다")
    void notInRoom()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomMemberService.leave(r("r1"), u("stranger"))).isEqualTo(LeaveResult.NOT_IN_ROOM);
        assertThat(roomMemberService.leave(r("r9"), u("u1"))).isEqualTo(LeaveResult.NOT_IN_ROOM);
        roomMemberService.leave(r("r1"), u("u1"));
        assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.NOT_IN_ROOM);

        assertThat(members("r1")).containsExactly("host");
        assertThat(host("r1")).isEqualTo("host");
    }

    @Test
    @DisplayName("늦게 도착한 나가기: 그 사이 다른 방에 들어간 사람의 입장 표시를 지우지 않는다")
    void lateLeaveDoesNotTouchTheOtherRoom()
    {
        roomService.create(r("r1"), u("host1"));
        roomService.create(r("r2"), u("host2"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.leave(r("r1"), u("u1"));
        roomMemberService.enter(r("r2"), u("u1"));

        // r1 나가기가 한 번 더, 늦게 도착했다
        assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.NOT_IN_ROOM);

        assertThat(marker("u1")).isEqualTo("r2");
        assertThat(members("r2")).containsExactlyInAnyOrder("host2", "u1");
    }

    @Test
    @DisplayName("방을 없앨 때도 남의 표시는 지우지 않는다 — 입장 표시가 다른 방을 가리키는 멤버가 섞여 있어도")
    void closingTheRoomOnlyClearsMarkersThatPointToIt()
    {
        roomService.create(r("r1"), u("host"));
        // 정상 흐름에서는 생기지 않는 어긋남이다. 생겼다고 해도 남의 방 표시를 지우면 안 된다
        redisTemplate.opsForSet().add(key("qm:room:r1:members"), u("ghost"));
        redisTemplate.opsForValue().set(key("qm:user:active-room:ghost"), r("r2"));

        roomMemberService.leave(r("r1"), u("host"));

        assertThat(marker("ghost")).isEqualTo("r2");
        assertThat(marker("host")).isNull();
    }

    // ── 동시성 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("방장이 나가는 순간 100명이 입장을 눌러도, 끝난 뒤 없는 방을 가리키는 입장 표시 키가 남지 않는다")
    void noOrphanMarkersWhenHostLeavesDuringEntries() throws InterruptedException
    {
        users("u", 101);
        for (int round = 0; round < 20; round++)
        {
            deleteOwnKeys();
            // 방장이 나가면 글이 만료된다(2026-09-25) — 되살려 두지 않으면 둘째 판부터 입장이 전부 글에서 409 로 막혀 경쟁이 일어나지 않는다
            jdbcTemplate.update("update recruit_posts set status = 'RECRUITING', expired_at = null where id = ?", Long.parseLong(r("r1")));
            roomService.create(r("r1"), u("host"));

            runConcurrently(101, i -> {
                if (i == 50)
                {
                    roomMemberService.leave(r("r1"), u("host"));
                }
                else
                {
                    roomMemberService.enter(r("r1"), u("u" + i));
                }
            });

            assertThat(host("r1")).isNull();
            assertThat(ownKeys()).as("round %d", round).isEmpty();
        }
    }

    @Test
    @DisplayName("같은 사람이 나가기를 동시에 100번 눌러도 한 번만 나가고 나머지는 NOT_IN_ROOM 이다")
    void sameUserLeavesOnce() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        Map<LeaveResult, AtomicInteger> counts = new ConcurrentHashMap<>();

        runConcurrently(100, i -> count(counts, roomMemberService.leave(r("r1"), u("u1"))));

        assertThat(counts.get(LeaveResult.LEFT)).hasValue(1);
        assertThat(counts.get(LeaveResult.NOT_IN_ROOM)).hasValue(99);
        assertThat(members("r1")).containsExactly("host");
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 이름표의 방 번호로 넣어 둔 글({@code RoomTestSupport#r})의 상태 */
    private String postStatus(String room)
    {
        return jdbcTemplate.queryForObject("select status from recruit_posts where id = ?", String.class, Long.parseLong(r(room)));
    }

    private static void count(Map<LeaveResult, AtomicInteger> counts, LeaveResult result)
    {
        counts.computeIfAbsent(result, r -> new AtomicInteger()).incrementAndGet();
    }

}
