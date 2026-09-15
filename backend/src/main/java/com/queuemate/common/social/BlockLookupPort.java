package com.queuemate.common.social;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * INV-6를 위해 matching/party 쪽에 공개하는 조회 계약이다. (owner: Member 3)
 *
 * 차단은 방향과 무관하게 적용된다. A가 B를 차단했든 B가 A를 차단했든
 * 두 사람은 같은 proposal/party에 들어갈 수 없다.
 */
public interface BlockLookupPort {

    /**
     * 후보 필터링용. 캐시를 쓸 수 있어 아주 짧은 시간 낡을 수 있다.
     * 최종 확정 직전에는 {@link #anyBlockBetween(Collection)}로 다시 확인한다.
     */
    Set<UUID> blockedUserIds(UUID userId);

    /**
     * 확정 직전 재검증용. 캐시를 거치지 않고 DB를 직접 읽는다 (docs/13 Blocking).
     * 주어진 사용자들 중 어느 한 쌍이라도 차단 관계면 true다.
     */
    boolean anyBlockBetween(Collection<UUID> userIds);
}
