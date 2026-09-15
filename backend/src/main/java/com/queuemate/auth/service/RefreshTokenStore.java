package com.queuemate.auth.service;

import java.util.UUID;

/**
 * refresh token rotation 저장소 (docs/11 결정 16번).
 * 발급된 토큰만 재사용 가능하고, 한 번 쓰면 폐기된다.
 */
public interface RefreshTokenStore {

    void store(UUID userId, String refreshToken);

    /**
     * 저장돼 있으면 폐기하고 true를 돌려준다.
     * 이미 쓰였거나 없는 토큰이면 false다. 즉 재사용 탐지 지점이다.
     */
    boolean consume(UUID userId, String refreshToken);

    void revokeAll(UUID userId);
}
