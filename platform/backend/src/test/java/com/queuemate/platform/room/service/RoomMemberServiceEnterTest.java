package com.queuemate.platform.room.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.EnterResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 입장({@code lua/enter-room.lua})의 여섯 갈래와 동시성, 그리고 그 앞의 <b>글의 검사</b>({@code party.service.PostEntryGate} — 2026-09-25 2단계).
 * 방은 방장이 먼저 만들어 둔다({@link RoomService#create}). 그 번호의 글은 {@link RoomTestSupport#r} 가 넣어 둔다.
 *
 * <p>테스트용 PostgreSQL(5433) · Redis(6380)가 떠 있어야 한다 — 돌리는 법과 5432 · 6379 를 피하는 이유는 {@code ApiTestSupport} 와 같다.
 * 사용자 · 방의 이름표와 "이 테스트의 키만 본다"는 {@link RoomTestSupport} 에 있다 — 건너뛴 것을 통과로 읽지 마라.
 */
class RoomMemberServiceEnterTest extends RoomTestSupport {

    // ── 글의 검사(스크립트 앞) ──────────────────────────────────────────

    @Test
    @DisplayName("입장은 스크립트보다 글을 먼저 본다 — 끝난 글이면 방이 살아 있어도 409 POST_NOT_RECRUITING 이고 아무 키도 쓰지 않는다")
    void gateRunsBeforeTheScript()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        jdbcTemplate.update("update recruit_posts set status = 'EXPIRED', expired_at = now() where id = ?", Long.parseLong(r("r1")));

        assertThatThrownBy(() -> roomMemberService.enter(r("r1"), u("u1")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("POST_NOT_RECRUITING"));

        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("u1")).isNull();
    }

    @Test
    @DisplayName("글이 없는 roomId(글 번호가 아닌 값 포함)는 404 POST_NOT_FOUND 다 — 스크립트의 ROOM_NOT_FOUND 에 닿기 전이다")
    void noPostNoEntry()
    {
        assertThatThrownBy(() -> roomMemberService.enter("9123456789012345678", u("u1")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("POST_NOT_FOUND"));
        assertThatThrownBy(() -> roomMemberService.enter("not-a-number", u("u1")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("POST_NOT_FOUND"));

        assertThat(marker("u1")).isNull();
    }

    // ── 여섯 갈래 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("입장하면 방 멤버와 입장 표시 키가 함께 쓰인다. 입장 표시 키의 값은 roomId 다")
    void enterWritesMemberAndMarker()
    {
        roomService.create(r("r1"), u("host"), Set.of());

        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ENTERED);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(marker("u1")).isEqualTo("r1");
    }

    @Test
    @DisplayName("입장 표시 키에 수명이 걸린다. 방의 수명은 입장이 건드리지 않는다 — 그것은 방장의 접속 확인만 늘린다")
    void markerHasATtl() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        Thread.sleep(1100);
        long hostTtlBefore = redisTemplate.getExpire(key("qm:room:r1:host"), TimeUnit.SECONDS);

        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(redisTemplate.getExpire(key("qm:user:active-room:u1"), TimeUnit.SECONDS)).isBetween(1L, 600L);
        assertThat(redisTemplate.getExpire(key("qm:room:r1:host"), TimeUnit.SECONDS)).isLessThanOrEqualTo(hostTtlBefore);
        assertThat(redisTemplate.getExpire(key("qm:room:r1:members"), TimeUnit.SECONDS)).isBetween(1L, hostTtlBefore);
    }

    @Test
    @DisplayName("없는 방에는 ROOM_NOT_FOUND 다. 입장이 방을 만들지 않는다")
    void roomNotFound()
    {
        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ROOM_NOT_FOUND);

        assertThat(ownKeys()).isEmpty();
    }

    @Test
    @DisplayName("같은 방에 다시 입장하면 ALREADY_ENTERED 이고 아무것도 바뀌지 않는다")
    void reenterSameRoom()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ALREADY_ENTERED);
        // 방장은 만들면서 이미 들어와 있다
        assertThat(roomMemberService.enter(r("r1"), u("host"))).isEqualTo(EnterResult.ALREADY_ENTERED);

        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
        assertThat(marker("u1")).isEqualTo("r1");
    }

    @Test
    @DisplayName("다른 방에 들어가 있으면 IN_OTHER_ROOM 이고 새 방에도 입장 표시 키에도 쓰지 않는다")
    void inOtherRoom()
    {
        roomService.create(r("r1"), u("host1"), Set.of());
        roomService.create(r("r2"), u("host2"), Set.of());
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomMemberService.enter(r("r2"), u("u1"))).isEqualTo(EnterResult.IN_OTHER_ROOM);

        assertThat(members("r2")).containsExactly("host2");
        assertThat(marker("u1")).isEqualTo("r1");
    }

    @Test
    @DisplayName("D-11 9번: 매칭 중이면 ACTIVE_REQUEST_EXISTS 이고, matching 의 활성 요청 키를 건드리지 않는다")
    void activeRequestExists()
    {
        // matching 이 쓰는 키다. 이 앱은 있는지만 본다 (docs/11 D-19)
        String activeRequestKey = key("qm:user:active-request:u1");
        redisTemplate.opsForHash().putAll(activeRequestKey, Map.of("requestId", "req-1", "game", "LOL"));
        redisTemplate.expire(activeRequestKey, Duration.ofSeconds(60));
        roomService.create(r("r1"), u("host"), Set.of());

        assertThat(roomMemberService.enter(r("r1"), u("u1"))).isEqualTo(EnterResult.ACTIVE_REQUEST_EXISTS);

        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("u1")).isNull();
        // 남의 키다 — 값도 수명도 그대로여야 한다
        assertThat(redisTemplate.<String, String>opsForHash().entries(activeRequestKey))
                .containsOnly(Map.entry("requestId", "req-1"), Map.entry("game", "LOL"));
        assertThat(redisTemplate.getExpire(activeRequestKey, TimeUnit.SECONDS)).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("D-11 10번: 정원 5명에 방장이 포함된다. 마지막 입장에 자동 확정되고 여섯 번째는 거절된다., 거절된 사용자에게 입장 표시 키가 남지 않는다")
    void sixthIsFull()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        for (int i = 1; i <= 4; i++)
        {
            assertThat(roomMemberService.enter(r("r1"), u("u" + i))).isEqualTo(i == 4 ? EnterResult.ENTERED_AND_CONFIRMED : EnterResult.ENTERED);
        }

        assertThat(enterOutcome(r("r1"), u("u5"))).isEqualTo(EnterResult.ROOM_CONFIRMED);

        assertThat(members("r1")).hasSize(5).doesNotContain("u5");
        // 남으면 그 사용자는 방에도 없으면서 다른 방 입장도 매칭도 못 하게 된다
        assertThat(marker("u5")).isNull();
    }

    @Test
    @DisplayName("가득 찬 방에 이미 있는 사람이 다시 입장하면 FULL 이 아니라 ALREADY_ENTERED 다")
    void memberOfFullRoomReenters()
    {
        roomService.create(r("r1"), u("host"), Set.of());
        for (int i = 1; i <= 4; i++)
        {
            roomMemberService.enter(r("r1"), u("u" + i));
        }

        assertThat(roomMemberService.enter(r("r1"), u("u3"))).isEqualTo(EnterResult.ALREADY_ENTERED);
    }

    // ── 동시성 — Lua 하나로 묶은 이유다 ─────────────────────────────────────

    @Test
    @DisplayName("D-11 10번: 서로 다른 100명이 동시에 눌러도 방장을 뺀 4명만 들어온다")
    void capacityHoldsUnderContention() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        Map<EnterResult, AtomicInteger> counts = new ConcurrentHashMap<>();
        users("u", 100);

        runConcurrently(100, i -> count(counts, enterOutcome(r("r1"), u("u" + i))));

        assertThat(counts.get(EnterResult.ENTERED)).hasValue(3);
        assertThat(counts.get(EnterResult.ROOM_CONFIRMED)).hasValue(96);
        assertThat(counts.get(EnterResult.ENTERED_AND_CONFIRMED)).hasValue(1);
        assertThat(members("r1")).hasSize(5);
        // 방 멤버와 입장 표시 키가 어긋나지 않는다 — 들어온 사람에게만, 그 사람 전원에게 있다
        assertThat(ownKeys("qm:user:active-room:")).hasSize(5);
        for (String userId : members("r1"))
        {
            assertThat(marker(userId)).isEqualTo("r1");
        }
    }

    @Test
    @DisplayName("같은 사람이 같은 방을 동시에 100번 눌러도 한 번만 들어오고 나머지는 ALREADY_ENTERED 다")
    void sameUserSameRoom() throws InterruptedException
    {
        roomService.create(r("r1"), u("host"), Set.of());
        Map<EnterResult, AtomicInteger> counts = new ConcurrentHashMap<>();
        u("u1");

        runConcurrently(100, i -> count(counts, roomMemberService.enter(r("r1"), u("u1"))));

        assertThat(counts.get(EnterResult.ENTERED)).hasValue(1);
        assertThat(counts.get(EnterResult.ALREADY_ENTERED)).hasValue(99);
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
    }

    @Test
    @DisplayName("같은 사람이 서로 다른 방 50개를 동시에 눌러도 한 방에만 들어간다")
    void sameUserManyRooms() throws InterruptedException
    {
        for (int i = 0; i < 50; i++)
        {
            roomService.create(r("r" + i), u("host" + i), Set.of());
        }
        Map<EnterResult, AtomicInteger> counts = new ConcurrentHashMap<>();
        u("u1");

        runConcurrently(50, i -> count(counts, roomMemberService.enter(r("r" + i), u("u1"))));

        assertThat(counts.get(EnterResult.ENTERED)).hasValue(1);
        assertThat(counts.get(EnterResult.IN_OTHER_ROOM)).hasValue(49);
        // 입장 표시 키가 가리키는 방이 실제로 들어간 그 방이고, 나머지 49개 방에는 방장뿐이다
        assertThat(members(marker("u1"))).contains("u1").hasSize(2);
        long roomsWithU1 = ownKeys("qm:room:").stream()
                .filter(k -> k.endsWith(":members"))
                .filter(k -> Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key(k), u("u1"))))
                .count();
        assertThat(roomsWithU1).isEqualTo(1);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static void count(Map<EnterResult, AtomicInteger> counts, EnterResult result)
    {
        counts.computeIfAbsent(result, r -> new AtomicInteger()).incrementAndGet();
    }

}
