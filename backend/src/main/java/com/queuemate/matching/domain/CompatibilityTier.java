package com.queuemate.matching.domain;

/**
 * 호환 등급 (docs/03 §5).
 *
 * <p>숫자 가중치를 임의로 박지 않는다. 등급은 "무엇이 최적에서 벗어났는가"로만 설명한다.
 * hard 조건은 어떤 등급에서도 완화되지 않는다. 여기 등장하는 것은 전부 이미 hard를 통과한 조합이다.
 */
public enum CompatibilityTier {

    /** key condition, 음성, 플레이 목적이 모두 최적으로 맞는다. */
    BEST,

    /** 셋 중 하나가 최적에서 벗어났다. */
    GOOD,

    /** 둘 이상이 최적에서 벗어났다. 그래도 hard 조건은 모두 통과했다. */
    ACCEPTABLE;

    public boolean isBetterThan(CompatibilityTier other) {
        return ordinal() < other.ordinal();
    }

    /** 파티 전체 등급은 가장 나쁜 쌍이 결정한다. */
    public CompatibilityTier worseOf(CompatibilityTier other) {
        return ordinal() >= other.ordinal() ? this : other;
    }

    static CompatibilityTier fromMisses(int misses) {
        return switch (misses) {
            case 0 -> BEST;
            case 1 -> GOOD;
            default -> ACCEPTABLE;
        };
    }
}
