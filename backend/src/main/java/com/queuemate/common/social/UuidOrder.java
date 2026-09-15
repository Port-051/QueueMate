package com.queuemate.common.social;

import java.util.Comparator;
import java.util.UUID;

/**
 * PostgreSQL의 uuid 비교 순서(바이트 순)를 그대로 재현한다.
 *
 * Java UUID.compareTo는 long 두 개를 부호 있는 값으로 비교해서 최상위 비트가 선
 * UUID에서 결과가 뒤집힌다. friendships의 CHECK(user_low_id < user_high_id)는
 * DB 순서를 쓰므로 정규화에 UUID.compareTo를 쓰면 절반의 쌍이 제약에 걸린다.
 */
public final class UuidOrder {

    public static final Comparator<UUID> DATABASE_ORDER = UuidOrder::compare;

    private UuidOrder() {
    }

    public static int compare(UUID left, UUID right) {
        int high = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0
                ? high
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    public static UUID lower(UUID a, UUID b) {
        return compare(a, b) <= 0 ? a : b;
    }

    public static UUID higher(UUID a, UUID b) {
        return compare(a, b) <= 0 ? b : a;
    }
}
