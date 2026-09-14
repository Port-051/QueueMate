package com.queuemate.matching.infra;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** 사용자별 선점 작업의 짧은 임대. 제안 수락 대기 동안 유지하는 active-proposal과는 다르다. */
final class RedisClaimLock {
    private static final Logger log = LoggerFactory.getLogger(RedisClaimLock.class);
    private final StringRedisTemplate redis;
    private final Duration lease;
    private final MeterRegistry metrics;
    private final RedisScript<Long> unlock = LuaScripts.load("redis/release-claim-lock.lua", Long.class);

    RedisClaimLock(StringRedisTemplate redis, Duration lease, MeterRegistry metrics) {
        if (lease == null || lease.toMillis() < 1) throw new IllegalArgumentException("선점 락 임대는 1ms 이상이어야 한다");
        this.redis = redis;
        this.lease = lease;
        this.metrics = metrics;
    }

    record Lease(List<String> keys, String token) {}

    boolean execute(List<UUID> userIds, Function<Lease, Boolean> action) {
        String token = UUID.randomUUID().toString();
        List<String> attempted = new ArrayList<>();
        try {
            for (String key : userIds.stream().map(MatchingRedisKeys::claimLock).sorted().toList()) {
                // SET이 반영되고 응답만 유실될 수도 있으므로 호출 전에 정리 대상에 넣는다.
                attempted.add(key);
                if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, lease))) {
                    metrics.counter("qm.matching.claim.lock.conflict").increment();
                    return false;
                }
            }
            return action.apply(new Lease(List.copyOf(attempted), token));
        } finally {
            if (!attempted.isEmpty()) {
                try {
                    redis.execute(unlock, attempted, token);
                } catch (RuntimeException e) {
                    // 이미 완료된 선점을 해제 오류로 실패 처리하면 호출자의 DB 보상 등록 전에
                    // 예외가 난다. 선점 결과는 보존하고 mutex는 유한한 임대로 정리한다.
                    metrics.counter("qm.matching.claim.lock.release.failure").increment();
                    log.warn("선점 작업 락을 해제하지 못했다. 임대 만료로 정리한다 count={}", attempted.size(), e);
                }
            }
        }
    }
}
