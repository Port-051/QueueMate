package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.HeartbeatResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>참가할 때 포지션을 고른다</b>(2026-09-30 소유자 결정 — {@code contracts/platform-api.md} P-44). 멤버 키는 HASH(필드 = {@code userId} · 값 = 고른 포지션)이고
 * 찾는 포지션 SET({@code qm:room:{roomId}:needs})은 <b>남은 찾는 포지션</b>이다 — 처음에는 글의 찾는 포지션이고, 들어오면 고른 것이 빠지고(SREM), 나가거나 강퇴되면 돌아온다.
 * 남이 이미 고른 포지션은 SET 에 없으므로 -6 {@code INVALID_POSITION} 이다 — -8 {@code POSITION_TAKEN} 은 스크립트가 더 이상 돌려주지 않는다(2026-09-30 — enum 만 남았다).
 * 입장 스크립트({@code lua/enter-room.lua})의 포지션 갈래와 순서 · 원자성, 나가기 · 강퇴 · 접속 확인의 유령 빼기({@code lua/leave-room.lua} · {@code lua/kick-room.lua} ·
 * {@code lua/heartbeat-room.lua})가 돌려놓는 것을 본다.
 * <b>포지션 방인가는 글이 정한다</b>(2026-10-01 소유자 결정 — 글에 찾는 포지션이 있나. 스크립트의 {@code ARGV[7]}) — 찾는 포지션 SET 이 있는지로 가르지 않는다.
 * 그래서 포지션이 없는 방에 포지션을 주면 거절되고, 다 골라 SET 이 지워진 포지션 방에 포지션 없이 · 아무 포지션으로 와도 거절된다.
 *
 * <p>포지션 방은 {@link #createPositionRoom} 으로 만든다 — 글({@link #r(String)} 이 넣는 모집 중인 글 · 정원이 비어 있어 5)에 찾는 포지션을 적고 방을 만든다.
 * 포지션이 없는 방은 {@code roomService.create} 에 빈 목록이다. HTTP 의 갈래(400 · 201)는 {@code party.PostRoomFlowTest} 가 본다.
 */
class RoomPositionTest extends RoomTestSupport {

    /** 5인 LoL 글의 찾는 포지션 — 방장(정글)을 뺀 넷. 정원 5 에 방장을 빼면 자리가 넷이라 자리마다 포지션이 하나다 */
    private static final Set<String> WANTED = Set.of("TOP", "MID", "ADC", "SUPPORT");

    private Set<String> needs(String roomLabel)
    {
        return redisTemplate.opsForSet().members(key("qm:room:" + roomLabel + ":needs"));
    }

    private boolean hasNeeds(String roomLabel)
    {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key("qm:room:" + roomLabel + ":needs")));
    }

    /** 찾는 포지션 SET 의 남은 수명(초). -1 이면 수명이 없고 -2 면 키가 없다 */
    private Long needsTtl(String roomLabel)
    {
        return redisTemplate.getExpire(key("qm:room:" + roomLabel + ":needs"), TimeUnit.SECONDS);
    }

    // ── 고른다 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("남은 찾는 포지션을 골라 들어오면 멤버 HASH 에 그 포지션이 적히고 남은 찾는 포지션 SET 에서 빠진다 — 방장 포지션 없이 만든 방의 방장 값은 \"\" 다")
    void entersWithAFreePosition()
    {
        createPositionRoom("r1", "host", WANTED);

        assertThat(roomMemberService.enter(r("r1"), u("u1"), "MID")).isEqualTo(EnterResult.ENTERED);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(position("r1", "u1")).isEqualTo("MID");
        assertThat(position("r1", "host")).isEmpty();
        assertThat(marker("u1")).isEqualTo("r1");
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
    }

    @Test
    @DisplayName("방장 포지션을 주고 만든 방은 멤버 HASH 의 방장 값이 그 포지션이다 — 찾는 포지션 SET 에는 들지 않는다")
    void hostValueIsTheHostPosition()
    {
        createPositionRoom("r1", "host", WANTED, "JUNGLE");

        assertThat(position("r1", "host")).isEqualTo("JUNGLE");
        assertThat(needs("r1")).containsExactlyInAnyOrderElementsOf(WANTED);
        // 방장 포지션은 남은 찾는 포지션이 아니다 — 그것을 골라 들어올 수 없다
        assertThat(roomMemberService.enter(r("r1"), u("u1"), "JUNGLE")).isEqualTo(EnterResult.INVALID_POSITION);
    }

    @Test
    @DisplayName("포지션을 골라 들어오는 방인데 안 골랐거나(null · 빈 값 · 공백) 찾는 포지션이 아니면 INVALID_POSITION 이고 아무것도 쓰지 않는다")
    void positionRoomRejectsMissingOrUnwantedPosition()
    {
        createPositionRoom("r1", "host", WANTED);

        for(String bad : new String[]{null, "", "   ", "JUNGLE", "mid", "DUELIST"})
        {
            assertThat(roomMemberService.enter(r("r1"), u("u1"), bad)).as(String.valueOf(bad)).isEqualTo(EnterResult.INVALID_POSITION);
        }

        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("u1")).isNull();
        assertThat(needs("r1")).containsExactlyInAnyOrderElementsOf(WANTED);
    }

    @Test
    @DisplayName("이미 누가 고른 포지션은 남은 찾는 포지션에 없어 INVALID_POSITION 이고 아무것도 쓰지 않는다 — 남은 다른 포지션으로는 들어온다")
    void takenPositionIsRejected()
    {
        createPositionRoom("r1", "host", WANTED);
        roomMemberService.enter(r("r1"), u("u1"), "MID");

        assertThat(roomMemberService.enter(r("r1"), u("u2"), "MID")).isEqualTo(EnterResult.INVALID_POSITION);
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(marker("u2")).isNull();
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");

        assertThat(roomMemberService.enter(r("r1"), u("u2"), "TOP")).isEqualTo(EnterResult.ENTERED);
        assertThat(position("r1", "u2")).isEqualTo("TOP");
        assertThat(needs("r1")).containsExactlyInAnyOrder("ADC", "SUPPORT");
    }

    @Test
    @DisplayName("이미 들어와 있는 사람의 재입장은 포지션을 보지 않는다 — 안 줘도 · 다른 것을 줘도 ALREADY_ENTERED 이고 고른 포지션은 바뀌지 않는다(들어온 뒤에는 못 바꾼다)")
    void reentryIgnoresPosition()
    {
        createPositionRoom("r1", "host", WANTED);
        roomMemberService.enter(r("r1"), u("u1"), "MID");

        assertThat(roomMemberService.enter(r("r1"), u("u1"), null)).isEqualTo(EnterResult.ALREADY_ENTERED);
        assertThat(roomMemberService.enter(r("r1"), u("u1"), "TOP")).isEqualTo(EnterResult.ALREADY_ENTERED);
        assertThat(roomMemberService.enter(r("r1"), u("host"), null)).isEqualTo(EnterResult.ALREADY_ENTERED);

        assertThat(position("r1", "u1")).isEqualTo("MID");
        assertThat(position("r1", "host")).isEmpty();
        // 탑은 여전히 남아 있다 — 재입장이 탑을 빼지 않았다
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
        assertThat(roomMemberService.enter(r("r1"), u("u2"), "TOP")).isEqualTo(EnterResult.ENTERED);
    }

    // ── 돌아온다 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("나가면 그 사람이 고른 포지션이 남은 찾는 포지션 SET 에 돌아온다 — 다른 사람이 그 포지션으로 들어온다")
    void leavingReturnsThePosition()
    {
        createPositionRoom("r1", "host", WANTED);
        roomMemberService.enter(r("r1"), u("u1"), "MID");

        assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.LEFT);
        assertThat(needs("r1")).containsExactlyInAnyOrderElementsOf(WANTED);

        assertThat(roomMemberService.enter(r("r1"), u("u2"), "MID")).isEqualTo(EnterResult.ENTERED);
        assertThat(position("r1", "u2")).isEqualTo("MID");
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
    }

    @Test
    @DisplayName("다 골라 Redis 가 지운 찾는 포지션 SET 도 나가면 다시 생기고 방과 같은 수명이 걸린다 — 수명 없는 키가 남지 않는다")
    void leavingRefillsAnEmptiedNeedsWithATtl()
    {
        createPositionRoom("r1", "host", Set.of("TOP", "MID", "ADC"));
        List<String> positions = List.of("TOP", "MID", "ADC");
        for(int i = 0; i < positions.size(); i++)
        {
            assertThat(roomMemberService.enter(r("r1"), u("u" + i), positions.get(i))).isEqualTo(EnterResult.ENTERED);
        }
        // 빈 SET 은 Redis 가 지운다
        assertThat(hasNeeds("r1")).isFalse();

        assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.LEFT);

        assertThat(needs("r1")).containsExactly("MID");
        assertThat(needsTtl("r1")).isBetween(1L, 600L);
        // 다시 포지션을 골라 들어오는 방이다 — 안 고르면 거절되고 미드로는 들어온다
        assertThat(roomMemberService.enter(r("r1"), u("late"), null)).isEqualTo(EnterResult.INVALID_POSITION);
        assertThat(roomMemberService.enter(r("r1"), u("late"), "MID")).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("강퇴되면 그 사람이 고른 포지션이 남은 찾는 포지션 SET 에 돌아온다 — 다 골라 지워졌던 SET 이면 다시 생기고 수명이 걸린다. 다른 사람이 그 포지션으로 들어온다")
    void kickReturnsThePosition()
    {
        createPositionRoom("r1", "host", WANTED);
        roomMemberService.enter(r("r1"), u("u1"), "MID");

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u1"))).isEqualTo(KickResult.KICKED);
        assertThat(needs("r1")).containsExactlyInAnyOrderElementsOf(WANTED);

        assertThat(roomMemberService.enter(r("r1"), u("u2"), "MID")).isEqualTo(EnterResult.ENTERED);
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u2");

        // 남은 셋도 다 골라 SET 이 지워진 뒤의 강퇴
        roomMemberService.enter(r("r1"), u("u3"), "TOP");
        roomMemberService.enter(r("r1"), u("u4"), "ADC");
        roomMemberService.enter(r("r1"), u("u5"), "SUPPORT");
        assertThat(hasNeeds("r1")).isFalse();

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u4"))).isEqualTo(KickResult.KICKED);
        assertThat(needs("r1")).containsExactly("ADC");
        assertThat(needsTtl("r1")).isBetween(1L, 600L);
    }

    @Test
    @DisplayName("이미 나간 사람을 강퇴하면(늦게 도착한 강퇴) 찾는 포지션 SET 을 건드리지 않는다")
    void lateKickLeavesTheNeeds()
    {
        createPositionRoom("r1", "host", WANTED);
        roomMemberService.enter(r("r1"), u("u1"), "MID");
        roomMemberService.leave(r("r1"), u("u1"));
        roomMemberService.enter(r("r1"), u("u2"), "MID");

        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u1"))).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);

        // u2 가 고른 미드가 되살아나지 않았다
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
    }

    @Test
    @DisplayName("방장의 접속 확인이 유령을 빼면 그 사람이 고른 포지션이 남은 찾는 포지션 SET 에 돌아온다 — 다 골라 지워졌던 SET 이면 다시 생기고 수명이 걸린다(2026-10-01)")
    void ghostRemovalReturnsThePosition()
    {
        createPositionRoom("r1", "host", Set.of("TOP", "MID", "ADC"));
        List<String> positions = List.of("TOP", "MID", "ADC");
        for(int i = 0; i < positions.size(); i++)
        {
            roomMemberService.enter(r("r1"), u("u" + i), positions.get(i));
        }
        assertThat(hasNeeds("r1")).isFalse();
        // u1(미드)이 말없이 사라졌다 — 입장 표시만 수명이 다했다
        redisTemplate.delete(key("qm:user:active-room:u1"));

        assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);

        assertThat(members("r1")).doesNotContain("u1");
        assertThat(needs("r1")).containsExactly("MID");
        assertThat(needsTtl("r1")).isBetween(1L, 600L);
        assertThat(roomMemberService.enter(r("r1"), u("late"), "MID")).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("포지션 없이 들어온 유령(포지션이 없는 방)을 빼도 찾는 포지션 SET 이 생기지 않는다")
    void noPositionGhostCreatesNoNeeds()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"), null);
        redisTemplate.delete(key("qm:user:active-room:u1"));

        assertThat(roomMemberService.heartbeat(r("r1"), u("host"))).isEqualTo(HeartbeatResult.ALIVE);

        assertThat(members("r1")).containsExactly("host");
        assertThat(hasNeeds("r1")).isFalse();
    }

    @Test
    @DisplayName("확정한 방의 유령은 포지션을 돌려놓지 않는다 — 방장이 사라져 넘겨받을 때 옛 방장이 유령으로 빠져도 방장 포지션이 찾는 포지션 SET 에 들지 않는다")
    void confirmedRoomGhostKeepsTheNeeds()
    {
        createPositionRoom("r1", "host", WANTED, "JUNGLE");
        roomMemberService.enter(r("r1"), u("u1"), "MID");
        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.CONFIRMED);
        // 방장이 말없이 사라졌다 — 방장 키와 입장 표시가 수명을 다했다
        redisTemplate.delete(List.of(key("qm:room:r1:host"), key("qm:user:active-room:host")));

        // u1 의 신호가 방장 자리를 넘겨받고 옛 방장을 유령으로 뺀다(D-23)
        assertThat(roomMemberService.heartbeat(r("r1"), u("u1"))).isEqualTo(HeartbeatResult.ALIVE);

        assertThat(host("r1")).isEqualTo("u1");
        assertThat(members("r1")).containsExactly("u1");
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
    }

    @Test
    @DisplayName("포지션 없이 들어온 사람(포지션이 없는 방)이 나가거나 강퇴돼도 찾는 포지션 SET 이 생기지 않는다 — 생기면 그 방에 아무도 못 들어온다")
    void noPositionMemberLeavingCreatesNoNeeds()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        assertThat(roomMemberService.enter(r("r1"), u("u1"), null)).isEqualTo(EnterResult.ENTERED);
        assertThat(roomMemberService.enter(r("r1"), u("u2"), "")).isEqualTo(EnterResult.ENTERED);
        assertThat(position("r1", "u1")).isEmpty();
        assertThat(position("r1", "u2")).isEmpty();

        assertThat(roomMemberService.leave(r("r1"), u("u1"))).isEqualTo(LeaveResult.LEFT);
        assertThat(roomMemberService.kick(r("r1"), u("host"), u("u2"))).isEqualTo(KickResult.KICKED);

        assertThat(hasNeeds("r1")).isFalse();
        assertThat(roomMemberService.enter(r("r1"), u("u3"), null)).isEqualTo(EnterResult.ENTERED);
    }

    @Test
    @DisplayName("확정한 방의 방장이 나가 승계될 때 방장 포지션은 찾는 포지션 SET 에 돌아가지 않는다")
    void confirmedHostSuccessionKeepsTheHostPositionOut()
    {
        createPositionRoom("r1", "host", WANTED, "JUNGLE");
        roomMemberService.enter(r("r1"), u("u1"), "MID");
        assertThat(roomService.confirm(r("r1"), u("host")).result()).isEqualTo(ConfirmResult.CONFIRMED);

        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.LEFT);

        assertThat(host("r1")).isEqualTo("u1");
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
    }

    @Test
    @DisplayName("방이 없어지면 찾는 포지션 SET 도 같이 지워진다 — 확정 전 방장이 나갈 때 · 확정한 방의 마지막 사람이 나갈 때")
    void closingTheRoomDeletesTheNeeds()
    {
        createPositionRoom("r1", "host", WANTED);
        roomMemberService.enter(r("r1"), u("u1"), "MID");
        assertThat(roomMemberService.leave(r("r1"), u("host"))).isEqualTo(LeaveResult.ROOM_CLOSED);
        assertThat(hasNeeds("r1")).isFalse();

        createPositionRoom("r2", "host2", WANTED);
        roomMemberService.enter(r("r2"), u("u2"), "TOP");
        assertThat(roomService.confirm(r("r2"), u("host2")).result()).isEqualTo(ConfirmResult.CONFIRMED);
        roomMemberService.leave(r("r2"), u("u2"));
        // 멤버가 나가면 고른 포지션이 돌아온다 — SET 은 방이 없어질 때만 지운다
        assertThat(needs("r2")).containsExactlyInAnyOrderElementsOf(WANTED);
        // 넘겨받을 사람이 없어 방이 없어진다
        assertThat(roomMemberService.leave(r("r2"), u("host2"))).isEqualTo(LeaveResult.ROOM_CLOSED);
        // 방 키 넷(방장 · 멤버 HASH · 찾는 포지션 SET · 확정 표시)이 전부 없다 — 사람의 키(u2 의 자동 합류 건너뛰기 목록)는 방과 무관하게 남는다
        assertThat(ownKeys("qm:room:r2:")).isEmpty();
    }

    // ── 순서 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정원 · 확정을 포지션보다 먼저 본다 — 정원이 차면 자동 확정돼 포지션과 무관하게 거절된다(포지션을 안 줬어도)")
    void fullAndConfirmedComeBeforePosition()
    {
        createPositionRoom("r1", "host", WANTED);
        List<String> positions = List.of("TOP", "MID", "ADC", "SUPPORT");
        for(int i = 0; i < positions.size(); i++)
        {
            assertThat(roomMemberService.enter(r("r1"), u("u" + i), positions.get(i))).isEqualTo(i == 3 ? EnterResult.ENTERED_AND_CONFIRMED : EnterResult.ENTERED);
        }
        assertThat(enterOutcome(r("r1"), u("late"), (String) null)).isEqualTo(EnterResult.ROOM_CONFIRMED);
        assertThat(enterOutcome(r("r1"), u("late"), "MID")).isEqualTo(EnterResult.ROOM_CONFIRMED);

        createPositionRoom("r2", "host2", WANTED);
        roomMemberService.enter(r("r2"), u("m1"), "MID");
        roomService.confirm(r("r2"), u("host2"));
        assertThat(roomMemberService.enter(r("r2"), u("m2"), null)).isEqualTo(EnterResult.ROOM_CONFIRMED);
        assertThat(roomMemberService.enter(r("r2"), u("m2"), "TOP")).isEqualTo(EnterResult.ROOM_CONFIRMED);
    }

    @Test
    @DisplayName("찾는 포지션을 다 골라 SET 이 지워진 방은 정원이 남아 있어도 포지션 없이 · 아무 포지션으로 와도 INVALID_POSITION 이다 — 포지션 방인가는 글이 정한다")
    void exhaustedNeedsStillRejects()
    {
        // 찾는 포지션 둘 · 정원 5 — 둘이 다 고르면 자리는 둘 남는데 고를 포지션이 없다
        createPositionRoom("r1", "host", Set.of("TOP", "MID"));
        roomMemberService.enter(r("r1"), u("u1"), "TOP");
        roomMemberService.enter(r("r1"), u("u2"), "MID");
        assertThat(hasNeeds("r1")).isFalse();

        for(String any : new String[]{null, "", "TOP", "MID", "ADC", "JUNGLE"})
        {
            assertThat(roomMemberService.enter(r("r1"), u("u3"), any)).as(String.valueOf(any)).isEqualTo(EnterResult.INVALID_POSITION);
        }
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1", "u2");
        assertThat(marker("u3")).isNull();
        assertThat(hasNeeds("r1")).isFalse();

        // 누가 나가 포지션이 돌아오면 그 포지션으로는 들어온다
        roomMemberService.leave(r("r1"), u("u1"));
        assertThat(roomMemberService.enter(r("r1"), u("u3"), "TOP")).isEqualTo(EnterResult.ENTERED);
    }

    // ── 포지션이 없는 방 ────────────────────────────────────────────────────

    @Test
    @DisplayName("찾는 포지션이 없는 방(포지션이 없는 모드 · 옛 글)은 포지션 없이 들어온다(값 \"\") — 포지션을 주면 조용히 버리지 않고 INVALID_POSITION 이고 아무것도 쓰지 않는다")
    void noPositionRoom()
    {
        roomService.create(r("r1"), u("host"), Set.of());

        for(String any : new String[]{"MID", "TOP", "DUELIST"})
        {
            assertThat(roomMemberService.enter(r("r1"), u("u1"), any)).as(any).isEqualTo(EnterResult.INVALID_POSITION);
        }
        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("u1")).isNull();
        // 거절이 찾는 포지션 SET 을 만들지 않았다 — 생기면 그 방이 포지션 방처럼 보인다
        assertThat(hasNeeds("r1")).isFalse();

        assertThat(roomMemberService.enter(r("r1"), u("u1"), null)).isEqualTo(EnterResult.ENTERED);
        assertThat(roomMemberService.enter(r("r1"), u("u2"), "")).isEqualTo(EnterResult.ENTERED);
        assertThat(position("r1", "u1")).isEmpty();
        assertThat(position("r1", "u2")).isEmpty();
        assertThat(redisTemplate.hasKey(key("qm:room:r1:needs"))).isFalse();
    }

    // ── 동시성 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("50명이 같은 포지션을 동시에 골라도 한 명만 들어온다 — 나머지는 INVALID_POSITION(이미 빠졌다)이고 입장 표시도 남지 않는다")
    void samePositionUnderContention() throws InterruptedException
    {
        createPositionRoom("r1", "host", WANTED);
        users("u", 50);
        Map<EnterResult, AtomicInteger> counts = new ConcurrentHashMap<>();

        runConcurrently(50, i -> count(counts, roomMemberService.enter(r("r1"), u("u" + i), "MID")));

        assertThat(counts.get(EnterResult.ENTERED)).hasValue(1);
        assertThat(counts.get(EnterResult.INVALID_POSITION)).hasValue(49);
        assertThat(members("r1")).hasSize(2);
        assertThat(needs("r1")).containsExactlyInAnyOrder("TOP", "ADC", "SUPPORT");
        long markers = ownKeys("qm:user:active-room:").size();
        // 방장 + 들어온 한 명
        assertThat(markers).isEqualTo(2);
    }

    @Test
    @DisplayName("100명이 포지션 넷 가운데 아무거나 동시에 골라도 포지션마다 한 명씩 넷만 들어온다 — 정원 5(방장 포함)를 넘지 않고 겹치는 포지션이 없다")
    void everyPositionOnceUnderContention() throws InterruptedException
    {
        createPositionRoom("r1", "host", WANTED);
        users("u", 100);
        List<String> positions = List.of("TOP", "MID", "ADC", "SUPPORT");
        Map<EnterResult, AtomicInteger> counts = new ConcurrentHashMap<>();

        runConcurrently(100, i -> count(counts, enterOutcome(r("r1"), u("u" + i), positions.get(i % positions.size()))));

        assertThat(counts.get(EnterResult.ENTERED)).hasValue(3);
        assertThat(counts.get(EnterResult.ENTERED_AND_CONFIRMED)).hasValue(1);
        assertThat(counts.values().stream().mapToInt(AtomicInteger::get).sum()).isEqualTo(100);
        assertThat(members("r1")).hasSize(5);
        Set<String> taken = new HashSet<>();
        for(String member : members("r1"))
        {
            if(!member.equals("host"))
            {
                taken.add(position("r1", member));
            }
        }
        assertThat(taken).containsExactlyInAnyOrderElementsOf(WANTED);
        // 넷을 다 골라 남은 찾는 포지션이 없다 — 빈 SET 은 Redis 가 지운다
        assertThat(hasNeeds("r1")).isFalse();
    }

    private static void count(Map<EnterResult, AtomicInteger> counts, EnterResult result)
    {
        counts.computeIfAbsent(result, r -> new AtomicInteger()).incrementAndGet();
    }
}
