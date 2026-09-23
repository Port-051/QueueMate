package com.queuemate.platform.party.room;

import java.util.Collection;
import java.util.Map;

/**
 * {@code room} 의 방 키를 읽는 창구. <b>읽기만 한다</b> — 이 앱이 남의 Redis 키를 만지는 유일한 예외이고(CLAUDE.md §3.3),
 * {@code room} 의 HTTP API 는 부르지 않는다(D-20).
 *
 * <p>인터페이스로 둔 이유 — "Redis 가 죽었을 때"를 테스트에서 실패하는 구현으로 갈아 끼워 확인한다.
 */
public interface RoomStateReader {

    /**
     * 여러 방의 상태를 <b>Redis 왕복 한 번</b>으로 읽는다. 돌려주는 맵에는 물어본 방이 전부 들어 있다(없는 방은 "방장 키 없음 · 멤버 없음"이다).
     *
     * @throws RoomStateUnavailableException 읽지 못했다. <b>"방이 없다"가 아니다</b>
     */
    Map<Long, RoomState> read(Collection<Long> roomIds);
}
