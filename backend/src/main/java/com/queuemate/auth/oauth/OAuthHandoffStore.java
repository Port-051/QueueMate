package com.queuemate.auth.oauth;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 콜백과 토큰 발급 사이를 잇는 일회용 코드.
 *
 * 토큰 자체를 리다이렉트 URL에 실으면 브라우저 기록, Referer 헤더, 중간 프록시 로그에 남는다.
 * 대신 수명이 짧고 한 번만 쓸 수 있는 코드를 넘기고 토큰은 POST 응답으로 준다.
 * 저장하는 값은 사용자 id뿐이다. 토큰은 교환 시점에 새로 발급한다.
 */
@Component
public class OAuthHandoffStore {

    private static final String KEY_PREFIX = "auth:oauth:handoff:";

    private final StringRedisTemplate redis;
    private final OAuthProperties properties;

    public OAuthHandoffStore(StringRedisTemplate redis, OAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String issue(UUID userId) {
        String code = OAuthStateStore.randomToken();
        try {
            redis.opsForValue().set(KEY_PREFIX + code, userId.toString(), properties.handoffTtl());
        } catch (DataAccessException e) {
            throw new OAuthStoreUnavailableException("교환 코드 저장 실패", e);
        }
        return code;
    }

    public Optional<UUID> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String userId;
        try {
            userId = redis.opsForValue().getAndDelete(KEY_PREFIX + code);
        } catch (DataAccessException e) {
            throw new OAuthStoreUnavailableException("교환 코드 검증 실패", e);
        }
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
