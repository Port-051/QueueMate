package com.queuemate.auth.service;

import com.queuemate.common.security.AuthProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Redis SET에 사용자별 유효 refresh token을 담는다.
 *
 * INV-10과 같은 원칙으로 Redis 장애 시 fail-closed 한다. 재발급을 막을 뿐
 * 기존 access token은 만료까지 살아 있으므로 전면 로그아웃으로 번지지 않는다.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public RedisRefreshTokenStore(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void store(UUID userId, String refreshToken) {
        try {
            String key = key(userId);
            redis.opsForSet().add(key, refreshToken);
            // 세트 전체 TTL을 갱신한다. 개별 토큰 만료는 JWT exp가 담당한다.
            redis.expire(key, properties.refreshTokenTtl());
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException("refresh token 저장 실패", e);
        }
    }

    @Override
    public boolean consume(UUID userId, String refreshToken) {
        try {
            Long removed = redis.opsForSet().remove(key(userId), refreshToken);
            return removed != null && removed > 0;
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException("refresh token 조회 실패", e);
        }
    }

    @Override
    public void revokeAll(UUID userId) {
        try {
            redis.delete(key(userId));
        } catch (DataAccessException e) {
            throw new RefreshTokenStoreUnavailableException("refresh token 폐기 실패", e);
        }
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    Set<String> keysForTest(UUID userId) {
        return redis.opsForSet().members(key(userId));
    }
}
