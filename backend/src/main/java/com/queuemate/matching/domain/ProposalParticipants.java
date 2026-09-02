package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 제안 참가자의 원본을 다루는 창구.
 *
 * <p>실시간 제안의 원본은 match_request, 예약 제안의 원본은 reservation이다. 수락/거절/만료
 * 흐름은 같지만 원본을 되돌리는 방법이 다르므로 그 부분만 갈라 놓는다 (docs/04 §8).
 */
public interface ProposalParticipants {

    ProposalSourceType sourceType();

    /**
     * 파티를 만드는 데 필요한 정보. 원본이 이미 사라졌으면 empty.
     *
     * @param sourceIds proposal_members.source_request_id 목록
     */
    Optional<PartyPlan> planFor(List<UUID> sourceIds);

    /** 전원이 수락했다. 원본을 매칭 완료로 옮기고 대기 상태에서 뺀다. */
    void onConfirmed(List<UUID> sourceIds);

    /** 거절이나 만료로 제안이 깨졌다. 아직 매칭을 원하는 원본은 대기 상태로 되돌린다. */
    void onBroken(List<UUID> sourceIds);

    /** @param scheduledStart 예약이면 약속 시각, 실시간이면 null */
    record PartyPlan(GameKey game, String modeKey, OffsetDateTime scheduledStart) {
    }
}
