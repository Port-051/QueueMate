package com.queuemate.common.matching;

import java.util.Collection;
import java.util.UUID;

/**
 * 파티가 깨졌을 때 남은 사람을 대기열로 되돌려 달라고 요청한다. (구현: Member 2)
 *
 * 방향이 PartyCreationPort와 반대다. 저쪽은 matching이 party 생성을 요청하고,
 * 이쪽은 party가 matching 복귀를 요청한다.
 *
 * 나간 사람은 대상이 아니다. 스스로 그만둔 사람을 다시 넣으면 통제권을 뺏는 것이다.
 * 남은 사람은 조건을 걸고 기다렸고 매칭까지 성사됐는데 본인 잘못 없이 깨진 경우다.
 */
public interface MatchRequeuePort {

    /**
     * @param userIds 파티에 남아 있던 사람들. 나간 사람은 포함하지 않는다.
     * @param partyId 어느 파티가 깨져서 돌아가는지. 직전 조건을 찾는 실마리다.
     */
    void requeueAfterPartyClosed(Collection<UUID> userIds, UUID partyId);
}
