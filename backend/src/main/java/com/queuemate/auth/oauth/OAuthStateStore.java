package com.queuemate.auth.oauth;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * CSRF 방지용 state 보관소.
 *
 * 서버가 만든 state만 통과시킨다. 이것이 없으면 공격자가 자기 계정의 인가 코드로 피해자의
 * 브라우저에서 콜백을 일으켜, 피해자를 공격자 계정에 로그인시킬 수 있다. 값에는 로그인 후
 * 돌아갈 앱 내부 경로를 함께 담는다.
 *
 * Redis 장애 시 fail-closed 한다. 검증할 수 없는 state를 통과시키면 방어가 없는 것과 같다
 * (INV-10과 같은 원칙).
 */
@Component
public class OAuthStateStore {

    private static final String KEY_PREFIX = "auth:oauth:state:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final OAuthProperties properties;

    public OAuthStateStore(StringRedisTemplate redis, OAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public String issue(String redirectPath) {
        String state = randomToken();
        try {
            redis.opsForValue().set(KEY_PREFIX + state, redirectPath, properties.stateTtl());
        } catch (DataAccessException e) {
            throw new OAuthStoreUnavailableException("state 저장 실패", e);
        }
        return state;
    }

    /** 한 번만 소비된다. 같은 state로 두 번째 콜백이 오면 비어 있다. */
    public Optional<String> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(redis.opsForValue().getAndDelete(KEY_PREFIX + state));
        } catch (DataAccessException e) {
            throw new OAuthStoreUnavailableException("state 검증 실패", e);
        }
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
