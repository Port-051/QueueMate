package com.queuemate.matching.domain;

import java.util.Set;

/**
 * 매칭 제안 상태 (docs/03 §3).
 *
 * <p>INV-5: 한 번 끝난 제안은 되살아나지 않는다. 만료된 제안이 뒤늦게 도착한 accept로
 * 확정되는 순간 두 사람이 서로 다른 파티를 보게 된다.
 */
public enum ProposalStatus {

    PENDING,
    CONFIRMED,
    DECLINED,
    EXPIRED,
    CANCELLED;

    private static final Set<ProposalStatus> FROM_PENDING =
            Set.of(CONFIRMED, DECLINED, EXPIRED, CANCELLED);

    public boolean isTerminal() {
        return this != PENDING;
    }

    public boolean canTransitionTo(ProposalStatus next) {
        return this == PENDING && FROM_PENDING.contains(next);
    }
}
