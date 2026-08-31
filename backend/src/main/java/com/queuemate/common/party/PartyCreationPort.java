package com.queuemate.common.party;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * proposal 확정을 party로 바꾸는 유일한 통로다. (owner: Member 3)
 *
 * party는 REST로 만들어지지 않는다. matching/reservation이 proposal을 CONFIRMED로
 * 옮기는 트랜잭션 안에서 이 포트를 부른다. 같은 트랜잭션이어야 확정과 party 생성이
 * 함께 성립하거나 함께 없던 일이 된다.
 *
 * 참가자 명단은 인자로 받지 않는다. proposal_members의 수락 기록에서 유도한다.
 * 호출자가 넘긴 명단을 믿으면 INV-4(전원 accept 전 party 확정 금지)를 호출자에게
 * 맡기게 된다.
 */
public interface PartyCreationPort {

    /**
     * @param proposalId    확정된 proposal
     * @param game          LOL/VALORANT/PUBG
     * @param modeKey       GameModeConfig의 mode key
     * @param targetSize    mode가 정한 정원. 클라이언트 값이 아니다 (docs/03 §9)
     * @param scheduledStart 예약 매칭이면 시작 시각, 실시간이면 null
     * @return 생성된 party id. 이미 만들어져 있으면 그 party id (멱등)
     * @throws PartyCreationConflictException 동시 확정으로 다른 트랜잭션이 먼저 만든 경우
     */
    UUID createFromProposal(UUID proposalId, String game, String modeKey,
                            int targetSize, OffsetDateTime scheduledStart);
}
