package com.queuemate.matching.domain;

import java.util.UUID;

/**
 * atomic claim 대상 한 명.
 *
 * <p>requestId를 함께 들고 다니는 이유는, 후보를 고른 시점과 잠그는 시점 사이에 사용자가
 * 요청을 취소하고 새로 넣었을 수 있기 때문이다. 그 경우 claim은 실패해야 한다 (docs/03 §7).
 *
 * <p>bucket을 들고 다니는 이유는, 대기열이 조건별로 나뉘어 있어 참가자마다 빠져나올 key가
 * 다르기 때문이다 (docs/07 §3.1).
 */
public record ClaimCandidate(UUID userId, UUID requestId, MatchBucket bucket) {

    public ClaimCandidate {
        if (userId == null || requestId == null || bucket == null) {
            throw new IllegalArgumentException("userId, requestId, bucket은 필수다");
        }
    }
}
