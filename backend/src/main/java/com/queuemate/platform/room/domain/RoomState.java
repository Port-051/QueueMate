package com.queuemate.platform.room.domain;

import java.util.Set;

/**
 * 어느 한 순간 방 키에서 읽은 것({@code RoomService#states}). 게시판({@code party})이 목록 · 단건 · 고치기 · 입장 검사에서 쓴다.
 *
 * @param hostKeyExists 방장 키가 있는가 — <b>있다 = 방이 있다.</b> 2026-09-25 2단계부터 글 쓰기가 방을 같이 만들므로 모집 중인 글에 방장 키가 없으면
 *                      <b>곧 사라진 방이다</b>(그 전에는 "아직 안 만들어진 방" 일 수 있어 {@code room_seen_at} 으로 갈랐다)
 * @param members       멤버 SET 의 사용자 번호들(방장 포함). 방이 없으면 비어 있다. <b>이 앱에 없는 번호가 섞여 있을 수 있다</b> —
 *                      방에 들어온 뒤 사라진 계정이나 손으로 넣은 값이다. 숫자가 아닌 값은 사용자 번호일 수 없어 읽을 때 걸러졌다({@link RoomMemberIds})
 * @param confirmed     확정 표시 키가 있는가 — 있다 = 방장이 확정한 방이다
 */
public record RoomState(boolean hostKeyExists, Set<Long> members, boolean confirmed) {

    public RoomState
    {
        members = (members == null) ? Set.of() : Set.copyOf(members);
    }
}
