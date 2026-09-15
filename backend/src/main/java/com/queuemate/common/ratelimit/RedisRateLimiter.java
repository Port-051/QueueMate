package com.queuemate.common.ratelimit;

import com.queuemate.common.metrics.QueueMateMetrics;
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
    private final QueueMateMetrics metrics;

    public RedisRateLimiter(StringRedisTemplate redis, QueueMateMetrics metrics) {
        this.redis = redis;
        this.metrics = metrics;
    }

    @Override
    public boolean tryAcquire(String scope, String identity, int limit, Duration window,
                              OnUnavailable onUnavailable) {
        try {
            Long count = redis.execute(INCREMENT, List.of(key(scope, identity, window)),
                    String.valueOf(window.toMillis()));
            boolean allowed = count == null || count <= limit;
            if (!allowed) {
                // scope별로 센다. identity를 태그로 넣으면 시계열이 사용자 수만큼 늘어난다.
                metrics.rateLimitRejected(scope);
            }
            return allowed;
        } catch (DataAccessException e) {
            return whenUnavailable(scope, onUnavailable, e);
        }
    }

    @Override
    public void reset(String scope, String identity, Duration window) {
        try {
            redis.delete(key(scope, identity, window));
        } catch (DataAccessException e) {
            // 못 지워도 창이 지나면 사라진다. 실패를 올려서 로그인 성공을 되돌릴 이유가 없다.
            log.warn("rate limit 초기화 실패 scope={}", scope);
        }
    }

    private boolean whenUnavailable(String scope, OnUnavailable policy, DataAccessException e) {
        // 장애가 어느 기능에 어떤 방향으로 닿았는지 남긴다. 같은 Redis 장애라도
        // 통과시킨 자리와 거절한 자리는 사후에 완전히 다르게 읽어야 한다.
        metrics.rateLimitUnavailable(scope, policy.name());
        if (policy == OnUnavailable.ALLOW) {
            log.warn("rate limit 확인 실패, 통과시킨다 scope={}", scope);
            return true;
        }
        // 통과시키면 방어가 통째로 사라지는 자리다. 서비스가 멈추더라도 막는다.
        log.error("rate limit 확인 실패, 거절한다 scope={}", scope);
        throw new RateLimitUnavailableException(scope, e);
    }

    private String key(String scope, String identity, Duration window) {
        long windowMillis = window.toMillis();
        return "qm:rate:%s:%s:%d".formatted(
                scope, identity, System.currentTimeMillis() / windowMillis);
    }
}
