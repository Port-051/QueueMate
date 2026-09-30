package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.RoomMembersResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 둘 — 방 안 사람 목록({@code lua/members-room.lua})과 내 방 찾기.
 * 알림을 놓친 클라이언트가 지금 상태를 다시 맞추는 수단이라, <b>알림이 말하는 것과 같은 답</b>을 해야 한다.
 *
 * <p>돌리는 법과 6379 를 피하는 이유는 {@link RoomMemberServiceEnterTest} 와 같다 —
 * {@code ./gradlew test --tests '*RoomLookupTest'}
 */
class RoomLookupTest extends RoomTestSupport {

    // ── 방 안 사람 목록 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("방 안의 사람은 방장과 멤버 전원을 본다. 멤버에는 방장도 들어 있다")
    void membersSeeEveryone()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        for (String asker : new String[]{"host", "u1", "u2"})
        {
            RoomMembersResult result = roomMemberService.members(r("r1"), u(asker));

            assertThat(result.status()).isEqualTo(RoomMembersResult.Status.FOUND);
            assertThat(labelOf(result.hostId())).isEqualTo("host");
            assertThat(result.members().stream().map(this::labelOf).toList()).containsExactlyInAnyOrder("host", "u1", "u2");
        }
    }

    @Test
    @DisplayName("나간 사람은 목록에서 빠지고, 나간 본인은 더 이상 목록을 볼 수 없다")
    void afterSomeoneLeft()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));
        roomMemberService.leave(r("r1"), u("u1"));

        assertThat(roomMemberService.members(r("r1"), u("host")).members().stream().map(this::labelOf).toList()).containsExactlyInAnyOrder("host", "u2");
        assertThat(roomMemberService.members(r("r1"), u("u1")).status()).isEqualTo(RoomMembersResult.Status.NOT_IN_ROOM);
    }

    @Test
    @DisplayName("방 밖의 사람 — 아무 방에도 없는 사람, 다른 방에 있는 사람 — 은 NOT_IN_ROOM 이고 아무것도 보지 못한다")
    void outsidersSeeNothing()
    {
        roomService.create(r("r1"), u("host1"));
        roomService.create(r("r2"), u("host2"));

        for (String outsider : new String[]{"stranger", "host2"})
        {
            RoomMembersResult result = roomMemberService.members(r("r1"), u(outsider));

            assertThat(result.status()).isEqualTo(RoomMembersResult.Status.NOT_IN_ROOM);
            assertThat(labelOf(result.hostId())).isNull();
            assertThat(result.members().stream().map(this::labelOf).toList()).isEmpty();
        }
    }

    @Test
    @DisplayName("없는 방과 방장이 나가 없어진 방은 NOT_IN_ROOM 이다 — 방 밖의 사람에게는 방이 있는지도 알려 주지 않는다")
    void noRoom()
    {
        assertThat(roomMemberService.members(r("r9"), u("u1")).status()).isEqualTo(RoomMembersResult.Status.NOT_IN_ROOM);

        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.leave(r("r1"), u("host"));

        assertThat(roomMemberService.members(r("r1"), u("u1")).status()).isEqualTo(RoomMembersResult.Status.NOT_IN_ROOM);
    }

    @Test
    @DisplayName("조회는 아무것도 쓰지 않는다")
    void readOnly()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        var before = ownKeys();

        roomMemberService.members(r("r1"), u("host"));
        roomMemberService.members(r("r1"), u("stranger"));
        roomMemberService.members(r("r9"), u("stranger"));
        roomService.myRoom(u("stranger"));

        assertThat(ownKeys()).containsExactlyInAnyOrderElementsOf(before);
    }

    // ── 내 방 찾기 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("방에 있으면 그 방의 roomId 를, 아무 방에도 없으면 null 을 돌려준다. 방장도 자기 방에 있는 사람이다")
    void myRoom()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(labelOf(roomService.myRoom(u("host")))).isEqualTo("r1");
        assertThat(labelOf(roomService.myRoom(u("u1")))).isEqualTo("r1");
        assertThat(labelOf(roomService.myRoom(u("stranger")))).isNull();
    }

    @Test
    @DisplayName("나간 뒤, 그리고 방장이 나가 방이 없어진 뒤에는 null 이다 — 없는 방에 갇힌 사람이 없다")
    void myRoomAfterLeaving()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"));

        roomMemberService.leave(r("r1"), u("u1"));
        assertThat(labelOf(roomService.myRoom(u("u1")))).isNull();

        roomMemberService.leave(r("r1"), u("host"));
        assertThat(labelOf(roomService.myRoom(u("u2")))).isNull();
        assertThat(labelOf(roomService.myRoom(u("host")))).isNull();
    }
}
