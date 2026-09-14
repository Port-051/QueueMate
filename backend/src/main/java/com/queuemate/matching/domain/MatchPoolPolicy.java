package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;
import java.util.UUID;

/** 공개 모집의 선택 조건을 자동/직접 매칭에도 같은 방식으로 적용한다. */
public interface MatchPoolPolicy {
    default void prepare(java.util.Collection<UUID> sourceIds) {}
    default void lock(GameKey game, String mode) {}
    default boolean automatic(UUID sourceId) { return true; }
    default boolean compatible(UUID a, MatchCondition ca, UUID b, MatchCondition cb) { return true; }
    MatchPoolPolicy UNRESTRICTED = new MatchPoolPolicy() {};
}
