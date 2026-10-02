package com.queuemate.platform.room.dto;

import java.util.List;

/**
 * 방 안 사람 목록의 응답 본문. {@code members} 에는 방장도 들어 있다 — 방장은 {@code hostId} 로 구분한다. 순서에 뜻이 없다.
 */
public record RoomMembersResponse(String roomId, String hostId, List<Member> members) {

    /**
     * 방 안의 한 사람(2026-10-01 소유자 결정 — 그 전에는 {@code members} 가 사용자 번호 문자열의 목록이었다).
     *
     * @param userId   사용자 번호의 십진 문자열({@code hostId} 와 같은 모양)
     * @param position 참가할 때 고른 포지션 — 방장은 방장 포지션이다. 고르지 않았으면(포지션이 없는 방 · 자동 매칭 파티의 방) {@code null}
     */
    public record Member(String userId, String position) {
    }
}
