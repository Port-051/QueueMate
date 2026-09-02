package com.queuemate.matching.domain;

import java.util.UUID;

/**
 * atomic claim 대상 한 명. requestId를 함께 들고 다니는 이유는,
 * 후보를 고른 시점과 잠그는 시점 사이에 사용자가 요청을 취소하고 새로 넣었을 수 있기 때문이다.
 * 그 경우 claim은 실패해야 한다 (docs/03 §7).
 */
public record ClaimCandidate(UUID userId, UUID requestId) {

    public ClaimCandidate {
        if (userId == null || requestId == null) {
            throw new IllegalArgumentException("userId와 requestId는 필수다");
        }
    }
}
