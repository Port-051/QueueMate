package com.queuemate.reservation.domain;

import java.util.Set;

/** 예약 상태 (docs/04 §3). */
public enum ReservationStatus {

    ACTIVE,
    PROPOSED,
    MATCHED,
    CANCELLED,
    EXPIRED,
    COMPLETED;

    private static final Set<ReservationStatus> FROM_ACTIVE = Set.of(PROPOSED, CANCELLED, EXPIRED);
    private static final Set<ReservationStatus> FROM_PROPOSED = Set.of(ACTIVE, MATCHED, CANCELLED);
    private static final Set<ReservationStatus> FROM_MATCHED = Set.of(COMPLETED, CANCELLED);

    /** 같은 시간대를 다시 잡을 수 없는 상태 (INV-9). */
    public boolean occupiesTime() {
        return this == ACTIVE || this == PROPOSED || this == MATCHED;
    }

    /** 조건을 고칠 수 있는 상태는 ACTIVE뿐이다 (docs/04 §10). */
    public boolean isEditable() {
        return this == ACTIVE;
    }

    public boolean canTransitionTo(ReservationStatus next) {
        return switch (this) {
            case ACTIVE -> FROM_ACTIVE.contains(next);
            case PROPOSED -> FROM_PROPOSED.contains(next);
            case MATCHED -> FROM_MATCHED.contains(next);
            case CANCELLED, EXPIRED, COMPLETED -> false;
        };
    }
}
