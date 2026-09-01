package com.queuemate.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * docs/07의 qm:rate:{scope}:{identity}:{window} 고정 창 카운터.
 *
 * 창 번호를 키에 넣어 창이 바뀌면 키도 바뀐다. 창 경계에서는 최대 두 배가 통과할 수 있는데,
 * 남용을 눌러 주는 것이 목적이므로 그 정도는 감수한다. 정확한 상한이 필요한 자리가 아니다.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    /**
     * INCR과 만료 설정이 한 번에 끝나야 한다. 둘로 나누면 INCR 직후 프로세스가 죽었을 때
     * 만료 없는 키가 남아 그 사용자가 창이 바뀌어도 계속 막힌다.
     */
    private static final RedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String scope, String identity, int limit, Duration window) {
        long windowMillis = window.toMillis();
        String key = "qm:rate:%s:%s:%d".formatted(
                scope, identity, System.currentTimeMillis() / windowMillis);
        try {
            Long count = redis.execute(INCREMENT, List.of(key), String.valueOf(windowMillis));
            return count == null || count <= limit;
        } catch (DataAccessException e) {
            // fail-open이다. INV-10의 fail-closed는 매칭 정합성에 대한 규칙이고,
            // 여기서 막으면 Redis 장애가 통화 중인 사용자의 연결까지 끊는다.
            // 남용 방어를 잠깐 잃는 쪽이 서비스를 잃는 쪽보다 낫다.
            log.warn("rate limit 확인 실패, 통과시킨다 scope={}", scope);
            return true;
        }
    }
}
