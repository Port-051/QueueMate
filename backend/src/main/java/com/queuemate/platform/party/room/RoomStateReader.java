package com.queuemate.platform.party.room;

import java.util.Collection;
import java.util.Map;

/**
 * 게시판이 방 키를 읽는 창구. <b>읽기만 한다</b>(CLAUDE.md §3.3). 두 앱이던 때 {@code room} 의 Redis 를 읽던 모양 그대로다 —
 * 2026-09-25 에 {@code room} 을 이 앱의 패키지로 합쳤고, 이 창구를 {@code room} 의 서비스를 직접 부르는 것으로 바꾸는 일은 2단계에 남겨 두었다
 * ({@code contracts/platform-api.md} P-22).
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
