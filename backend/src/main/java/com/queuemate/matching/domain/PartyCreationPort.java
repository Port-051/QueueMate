package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 확정된 제안을 실제 파티로 만드는 경계 (docs/10 Member 2 ↔ Member 3).
 *
 * <p>매칭은 "누구와 누가 함께한다"까지만 결정하고 파티 자체는 만들지 않는다.
 * 구현은 party 패키지가 제공하며, 저장 직전에 차단 관계를 DB에서 한 번 더 확인해야 한다 (INV-6).
 *
 * <p>구현은 멱등해야 한다. 같은 proposalId로 두 번 불려도 파티는 하나여야 한다 (INV-3).
 * parties.proposal_id UNIQUE 제약이 마지막 방어선이다.
 */
public interface PartyCreationPort {

    /**
     * 확정된 제안으로 파티를 만든다.
     *
     * @param command 파티 구성 정보
     * @return 만들어졌거나 이미 존재하던 파티의 id
     */
    UUID createParty(PartyCreationCommand command);

    /**
     * 이 제안으로 만들어진 파티의 id.
     *
     * <p>생성과 같은 관계를 반대로 묻는 것이라 같은 포트에 둔다. 매칭은 parties 테이블을
     * 직접 읽지 않는다. 확정 응답에는 방금 만든 id를 그대로 쓰지만, 이미 확정된 제안을
     * 다시 조회할 때는 물어볼 곳이 여기밖에 없다.
     *
     * @return 아직 파티가 없으면 비어 있다
     */
    Optional<UUID> findPartyIdOf(UUID proposalId);

    /**
     * @param proposalId     이 파티의 근거가 된 제안. 파티당 하나뿐이다
     * @param memberUserIds  파티 구성원. 제안 참가자와 정확히 같아야 한다
     * @param targetSize     모드 설정이 정한 정원. memberUserIds 크기와 같아야 한다
     * @param scheduledStart 예약 매칭이면 약속 시각, 실시간이면 null
     */
    record PartyCreationCommand(
            UUID proposalId,
            GameKey game,
            String modeKey,
            int targetSize,
            List<UUID> memberUserIds,
            OffsetDateTime scheduledStart
    ) {
        public PartyCreationCommand {
            if (proposalId == null || game == null || modeKey == null || memberUserIds == null) {
                throw new IllegalArgumentException("proposalId, game, modeKey, memberUserIds는 필수다");
            }
            if (memberUserIds.size() != targetSize) {
                throw new IllegalArgumentException(
                        "파티 인원이 정원과 다르다: " + memberUserIds.size() + " vs " + targetSize);
            }
            if (memberUserIds.size() != Set.copyOf(memberUserIds).size()) {
                throw new IllegalArgumentException("파티에 같은 사용자가 두 번 들어갔다");
            }
            memberUserIds = List.copyOf(memberUserIds);
        }
    }
}
