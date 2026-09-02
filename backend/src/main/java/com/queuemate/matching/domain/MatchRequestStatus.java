package com.queuemate.matching.domain;

import java.util.Set;

/**
 * 실시간 매칭 요청 상태 (docs/03 §2).
 *
 * <p>전이를 enum이 직접 들고 있는 이유는, 서비스 곳곳에서 if로 흩어지면
 * "어디선가 한 군데만 빠뜨린" 전이가 생기기 때문이다.
 */
public enum MatchRequestStatus {

    QUEUED,
    PROPOSED,
    MATCHED,
    CANCELLED,
    EXPIRED;

    private static final Set<MatchRequestStatus> FROM_QUEUED =
            Set.of(PROPOSED, CANCELLED, EXPIRED);

    // 거절/만료 후 조건이 유지되면 큐로 돌아온다 (docs/03 §8).
    private static final Set<MatchRequestStatus> FROM_PROPOSED =
            Set.of(QUEUED, MATCHED, CANCELLED);

    /** 아직 매칭 대상인 상태. Redis guard와 DB partial unique index가 이 범위를 지킨다. */
    public boolean isActive() {
        return this == QUEUED || this == PROPOSED;
    }

    public boolean canTransitionTo(MatchRequestStatus next) {
        return switch (this) {
            case QUEUED -> FROM_QUEUED.contains(next);
            case PROPOSED -> FROM_PROPOSED.contains(next);
            case MATCHED, CANCELLED, EXPIRED -> false;
        };
    }
}
