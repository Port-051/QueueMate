package com.queuemate.platform.room.domain;

import java.util.Map;
import java.util.Set;

/**
 * 어느 한 순간 방 키에서 읽은 것({@code RoomService#states}). 게시판({@code party})이 목록 · 단건 · 입장 검사 · 게시판 방 먼저 합류에서 쓴다.
 *
 * @param hostKeyExists 방장 키가 있는가 — <b>있다 = 방이 있다.</b> 2026-09-25 2단계부터 글 쓰기가 방을 같이 만들므로 모집 중인 글에 방장 키가 없으면
 *                      <b>곧 사라진 방이다</b>(그 전에는 "아직 안 만들어진 방" 일 수 있어 {@code room_seen_at} 으로 갈랐다)
 * @param members       멤버 HASH 의 사용자 번호들(방장 포함 — 필드). 방이 없으면 비어 있다. <b>이 앱에 없는 번호가 섞여 있을 수 있다</b> —
 *                      방에 들어온 뒤 사라진 계정이나 손으로 넣은 값이다. 숫자가 아닌 값은 사용자 번호일 수 없어 읽을 때 걸러졌다({@link RoomMemberIds})
 * @param confirmed     확정 표시 키가 있는가 — 있다 = 방장이 확정한 방이다
 * @param positions     사람마다 참가할 때 고른 포지션(2026-09-30 — P-44. 멤버 HASH 의 값). 고르지 않은 사람(방장 · 포지션이 없는 방)은 {@code ""} 다.
 *                      키는 {@code members} 와 같다. 게시판의 카드가 모집 중인 글의 사람마다 싣고({@code PostService} — 2026-10-01 소유자 결정 · {@code ""} 는 {@code null}),
 *                      게시판 방 먼저 합류가 "내 포지션이 찬 방" 을 거르는 데 쓴다
 */
public record RoomState(boolean hostKeyExists, Set<Long> members, boolean confirmed, Map<Long, String> positions) {

    public RoomState
    {
        members = (members == null) ? Set.of() : Set.copyOf(members);
        positions = (positions == null) ? Map.of() : Map.copyOf(positions);
    }

    /** 그 포지션을 방 안의 누가 이미 골랐는가. 빈 값({@code ""} · {@code null})은 "고르지 않았다" 라 찬 것이 아니다 */
    public boolean positionTaken(String position)
    {
        return position != null && !position.isEmpty() && positions.containsValue(position);
    }
}
