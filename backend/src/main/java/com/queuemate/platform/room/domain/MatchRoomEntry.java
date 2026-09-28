package com.queuemate.platform.room.domain;

import java.util.List;

/**
 * 자동 매칭 파티 방에 들어가려 한 결과 — 코드와, <b>들어갔을 때({@link MatchRoomResult#ENTERED}) 그 순간 방에 먼저 있던 사람들</b>.
 *
 * <p>먼저 있던 사람 목록을 밖으로 내는 이유는 하나다 — 최근 함께한 사람({@code recent_players})을 <b>들어올 때 그 순간 방에 있던 사람과만</b>
 * 적기 위해서다(2026-09-28 소유자 결정). {@code party_members} 는 나간 사람도 남아 있어 그것으로 짝을 지으면 서로 마주친 적 없는
 * 두 사람도 "함께한 사람"이 된다. 방에 누가 있었는지는 스크립트가 {@code SMEMBERS} 로 읽어 돌려준 것이 원본이다.
 *
 * @param result       스크립트의 판정
 * @param priorMembers {@code ENTERED} 일 때 나를 뺀, 그 순간 방에 있던 사람들의 {@code userId}(십진 문자열). 그 밖에는 빈 목록
 */
public record MatchRoomEntry(MatchRoomResult result, List<String> priorMembers) {

    public MatchRoomEntry {
        priorMembers = List.copyOf(priorMembers);
    }

    public static MatchRoomEntry of(MatchRoomResult result) {
        return new MatchRoomEntry(result, List.of());
    }
}
