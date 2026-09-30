package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.domain.CreateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 방 만들기({@code lua/create-room.lua})의 다섯 갈래와 동시성.
 *
 * <p>돌리는 법과 6379 를 피하는 이유는 {@link RoomMemberServiceEnterTest} 와 같다 —
 * {@code ./gradlew test --tests '*RoomServiceCreateTest'}
 */
class RoomServiceCreateTest extends RoomTestSupport {

    @Test
    @DisplayName("방을 만들면 방장 키가 쓰이고, 방장은 곧바로 그 방에 들어와 있다")
    void createWritesHostAndEntersTheHost()
    {
        assertThat(roomService.create(r("r1"), u("host"))).isEqualTo(CreateResult.CREATED);

        assertThat(host("r1")).isEqualTo("host");
        assertThat(members("r1")).containsExactly("host");
        assertThat(marker("host")).isEqualTo("r1");
    }

    @Test
    @DisplayName("세 키 모두 수명이 걸린다 — 수명 없는 키는 앱이 죽었을 때 그 사용자를 영원히 가둔다")
    void everyKeyHasATtl()
    {
        roomService.create(r("r1"), u("host"));

        for (String key : new String[]{key("qm:room:r1:host"), key("qm:room:r1:members"), key("qm:user:active-room:host")})
        {
            // -1 이면 수명이 없는 것이고 -2 면 키가 없는 것이다
            assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).as(key).isBetween(1L, 600L);
        }
        // 값과 수명의 자리가 바뀌지 않았다 (SETEX 는 인자 순서가 키 · 초 · 값이다)
        assertThat(host("r1")).isEqualTo("host");
        assertThat(marker("host")).isEqualTo("r1");
    }

    @Test
    @DisplayName("userId 와 roomId 가 숫자여도 값과 수명이 뒤바뀌지 않는다")
    void numericIdsAreNotMistakenForTtl()
    {
        assertThat(roomService.create(r("42"), u("123"))).isEqualTo(CreateResult.CREATED);

        assertThat(host("42")).isEqualTo("123");
        assertThat(marker("123")).isEqualTo("42");
        assertThat(redisTemplate.getExpire(key("qm:room:42:host"), TimeUnit.SECONDS)).isGreaterThan(123L);
    }

    @Test
    @DisplayName("내가 만든 방을 다시 만들면 ALREADY_CREATED 이고 아무것도 바뀌지 않는다")
    void createAgainByTheHost()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomService.create(r("r1"), u("host"))).isEqualTo(CreateResult.ALREADY_CREATED);

        assertThat(host("r1")).isEqualTo("host");
        assertThat(members("r1")).containsExactlyInAnyOrder("host", "u1");
    }

    @Test
    @DisplayName("남이 만든 방은 ROOM_EXISTS 다. 방장이 바뀌지 않고, 그 방에 들어와 있던 사람이 불러도 마찬가지다")
    void roomExists()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomService.create(r("r1"), u("other"))).isEqualTo(CreateResult.ROOM_EXISTS);
        assertThat(roomService.create(r("r1"), u("u1"))).isEqualTo(CreateResult.ROOM_EXISTS);

        assertThat(host("r1")).isEqualTo("host");
        assertThat(marker("other")).isNull();
    }

    @Test
    @DisplayName("다른 방에 들어가 있으면 IN_OTHER_ROOM 이고 새 방은 생기지 않는다")
    void inOtherRoom()
    {
        roomService.create(r("r1"), u("host"));
        roomMemberService.enter(r("r1"), u("u1"));

        assertThat(roomService.create(r("r2"), u("u1"))).isEqualTo(CreateResult.IN_OTHER_ROOM);
        // 방장도 자기 방에 들어가 있는 사람이다
        assertThat(roomService.create(r("r2"), u("host"))).isEqualTo(CreateResult.IN_OTHER_ROOM);

        assertThat(host("r2")).isNull();
        assertThat(members("r2")).isEmpty();
        assertThat(marker("u1")).isEqualTo("r1");
    }

    @Test
    @DisplayName("D-11 9번: 매칭 중이면 ACTIVE_REQUEST_EXISTS 이고 방이 생기지 않는다")
    void activeRequestExists()
    {
        // matching 이 쓰는 키다. 이 앱은 있는지만 본다 (docs/11 D-19)
        redisTemplate.opsForHash().putAll(key("qm:user:active-request:host"), Map.of("requestId", "req-1"));

        assertThat(roomService.create(r("r1"), u("host"))).isEqualTo(CreateResult.ACTIVE_REQUEST_EXISTS);

        assertThat(ownKeys("qm:room:")).isEmpty();
        assertThat(marker("host")).isNull();
        assertThat(redisTemplate.<String, String>opsForHash().entries(key("qm:user:active-request:host")))
                .containsOnly(Map.entry("requestId", "req-1"));
    }

    @Test
    @DisplayName("서로 다른 100명이 같은 방을 동시에 만들어도 방장은 한 명이고, 진 사람에게 아무것도 남지 않는다")
    void onlyOneHostUnderContention() throws InterruptedException
    {
        Map<CreateResult, AtomicInteger> counts = new ConcurrentHashMap<>();
        users("u", 100);

        runConcurrently(100, i -> count(counts, roomService.create(r("r1"), u("u" + i))));

        assertThat(counts.get(CreateResult.CREATED)).hasValue(1);
        assertThat(counts.get(CreateResult.ROOM_EXISTS)).hasValue(99);
        assertThat(members("r1")).containsExactly(host("r1"));
        assertThat(ownKeys("qm:user:active-room:")).containsExactly("qm:user:active-room:" + host("r1"));
    }

    @Test
    @DisplayName("같은 사람이 방 50개를 동시에 만들어도 한 방만 생긴다")
    void sameUserManyRooms() throws InterruptedException
    {
        Map<CreateResult, AtomicInteger> counts = new ConcurrentHashMap<>();
        u("host");
        for (int i = 0; i < 50; i++)
        {
            r("r" + i);
        }

        runConcurrently(50, i -> count(counts, roomService.create(r("r" + i), u("host"))));

        assertThat(counts.get(CreateResult.CREATED)).hasValue(1);
        assertThat(counts.get(CreateResult.IN_OTHER_ROOM)).hasValue(49);
        assertThat(ownKeys("qm:room:")).filteredOn(k -> k.endsWith(":host")).hasSize(1);
        assertThat(host(marker("host"))).isEqualTo("host");
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    private static void count(Map<CreateResult, AtomicInteger> counts, CreateResult result)
    {
        counts.computeIfAbsent(result, r -> new AtomicInteger()).incrementAndGet();
    }

}
