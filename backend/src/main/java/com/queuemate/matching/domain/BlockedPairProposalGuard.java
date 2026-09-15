package com.queuemate.matching.domain;

import java.util.UUID;

/**
 * 차단이 생겼을 때 이미 떠 있는 제안을 닫는 창구 (INV-6).
 *
 * <p>후보 탐색과 잠금 사이에 차단이 생기는 창은 Redis 스크립트만으로 없앨 수 없다.
 * Lua는 DB의 차단 관계를 볼 수 없기 때문이다. 그래서 반대 방향에서 막는다.
 * 차단을 만드는 쪽이 같은 트랜잭션에서 이 메서드를 불러, 두 사람이 함께 들어 있는
 * 진행 중 제안을 닫는다.
 *
 * <p>구현은 matching이 제공하고 호출은 social(block 생성)이 한다.
 *
 * @see com.queuemate.common.social.BlockLookupPort 반대 방향(매칭이 차단을 조회하는 쪽)
 */
public interface BlockedPairProposalGuard {

    /**
     * 두 사용자가 함께 참가 중인 PENDING 제안을 모두 닫는다.
     *
     * <p>닫힌 제안의 나머지 참가자는 원래 대기 상태로 돌아간다.
     * 차단이 없거나 함께 있는 제안이 없으면 아무 일도 하지 않는다.
     *
     * @return 닫은 제안 수
     */
    int cancelSharedPendingProposals(UUID userId, UUID otherUserId);
}
