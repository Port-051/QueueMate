package com.queuemate.platform.party.room;

import java.util.Set;

/**
 * 어느 한 순간 방 키에서 읽은 것(방 키는 {@code room} 패키지의 {@code redisKeys.RoomKeys}).
 *
 * @param hostKeyExists 방장 키가 있는가 — <b>있다 = 방이 있다.</b> 없다고 곧바로 "사라진 방"은 아니다 — 아직 안 만들어진 방일 수 있다
 *                      ({@code contracts/platform-api.md} 의 {@code room_seen_at} 규칙이 가른다)
 * @param members       멤버 SET 의 사용자 번호들(방장 포함). 방이 없으면 비어 있다. <b>이 앱에 없는 번호가 섞여 있을 수 있다</b> —
 *                      방에 들어온 뒤 사라진 계정이나 손으로 넣은 값이다(2026-09-25 에 {@code room} 을 합친 뒤로 방에는 로그인한 사용자만 들어온다).
 *                      숫자가 아닌 값은 사용자 번호일 수 없어 읽을 때 걸러졌다({@code RedisRoomStateReader})
 * @param confirmed     확정 표시 키가 있는가 — 있다 = 방장이 확정한 방이다
 */
public record RoomState(boolean hostKeyExists, Set<Long> members, boolean confirmed) {

    public RoomState
    {
        members = (members == null) ? Set.of() : Set.copyOf(members);
    }
}
