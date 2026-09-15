package com.queuemate.matching.infra;

import com.queuemate.matching.domain.BucketDepth;
import com.queuemate.matching.domain.BucketScanPlan;
import com.queuemate.matching.domain.MatchBucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 실시간 매칭 대기열과 활성 요청 guard를 다룬다 (INV-1, docs/07 §3·§4).
 *
 * <p>Redis 장애 시 예외는 그대로 올린다. DB fallback으로 새 매칭을 받지 않는다 (INV-10).
 */
@Repository
public class MatchQueueRepository {

    private static final Logger log = LoggerFactory.getLogger(MatchQueueRepository.class);

    private final StringRedisTemplate redis;
    private final RedisScript<Long> acquireScript;
    private final RedisScript<Long> releaseScript;
    private final RedisScript<List> depthsScript;
    private final RedisScript<List> sliceScript;
    private final RedisScript<Long> countScript;

    public MatchQueueRepository(StringRedisTemplate redis) {
        this.redis = redis;
        this.acquireScript = LuaScripts.load("redis/acquire-active-request.lua", Long.class);
        this.releaseScript = LuaScripts.load("redis/release-active-request.lua", Long.class);
        this.depthsScript = LuaScripts.load("redis/queue-bucket-depths.lua", List.class);
        this.sliceScript = LuaScripts.load("redis/queue-bucket-slice.lua", List.class);
        this.countScript = LuaScripts.load("redis/queue-bucket-count.lua", Long.class);
    }

    /**
     * 활성 요청 guard를 잡고 대기열에 등록한다.
     *
     * @param queuedAt 최초 대기 시작 시각. proposal을 거절하고 큐로 돌아올 때는 처음 값을
     *                 그대로 넘겨야 aging을 잃지 않는다 (docs/03 §8)
     * @return 등록되면 true, 이미 활성 요청이 있으면 false (호출자는 409로 응답한다)
     */
    public boolean acquire(UUID userId, UUID requestId, String bucketKey, Instant queuedAt) {
        return Long.valueOf(1L).equals(redis.execute(
                acquireScript,
                List.of(MatchingRedisKeys.activeRequest(userId), bucketKey),
                requestId.toString(), String.valueOf(queuedAt.toEpochMilli())));
    }

    /**
     * 활성 요청 guard를 해제하고 대기열에서 뺀다.
     * 값이 일치할 때만 지우므로, 취소 직후 새로 등록한 요청을 늦게 도착한 취소가 지우지 못한다.
     *
     * @return 해제되면 true, 이미 없거나 다른 요청이 자리를 차지했으면 false
     */
    public boolean release(UUID userId, UUID requestId, String bucketKey) {
        return Long.valueOf(1L).equals(redis.execute(
                releaseScript,
                List.of(MatchingRedisKeys.activeRequest(userId), bucketKey),
                requestId.toString()));
    }

    /**
     * 제안이 깨진 요청을 대기열로 되돌린다. guard는 계속 살아 있으므로 다시 잡지 않는다.
     * 최초 대기 시각을 그대로 넣어 오래 기다린 사람이 앞자리를 유지하게 한다 (docs/03 §8).
     */
    public void requeue(String bucketKey, UUID requestId, Instant queuedAt) {
        redis.opsForZSet().add(bucketKey, requestId.toString(), queuedAt.toEpochMilli());
    }

    /**
     * 대기열에 남았지만 DB에서는 이미 끝난 요청을 지운다.
     *
     * <p>지우지 않으면 scan 창(앞에서부터 N개) 앞자리를 이런 항목이 차지해,
     * 실제 대기자가 영원히 스캔되지 않는다.
     *
     * <p>한 번의 스캔에서 나온 것들을 한 번에 지운다. 항목마다 왕복하면 큐가 상해 있을수록
     * 느려져, 가장 급한 상황에서 가장 오래 걸린다.
     */
    public void removeStale(MatchBucket bucket, Collection<UUID> requestIds) {
        if (requestIds.isEmpty()) {
            return;
        }
        log.debug("대기열에서 끝난 요청을 제거한다 bucket={} count={}",
                bucket.suffix(), requestIds.size());
        redis.opsForZSet().remove(MatchingRedisKeys.queue(bucket),
                requestIds.stream().map(UUID::toString).toArray(Object[]::new));
    }

    /** 현재 활성 요청. 없으면 empty. */
    public Optional<UUID> activeRequestOf(UUID userId) {
        String value = redis.opsForValue().get(MatchingRedisKeys.activeRequest(userId));
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    /**
     * 비어 있지 않은 bucket과 그 깊이를 읽는다 (docs/07 §3.2).
     *
     * <p>후보를 읽기 전에 어느 bucket을 볼지 정하기 위한 자료다. bucket 수는 모드당 유한하고
     * 작으므로 한 번에 훑는다.
     */
    public List<BucketDepth> bucketDepths(List<MatchBucket> buckets) {
        if (buckets.isEmpty()) {
            return List.of();
        }
        List<?> raw = redis.execute(depthsScript, MatchingRedisKeys.queues(buckets));
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<BucketDepth> depths = new ArrayList<>(raw.size() / 3);
        for (int i = 0; i + 2 < raw.size(); i += 3) {
            int index = Integer.parseInt(String.valueOf(raw.get(i)));
            // Lua는 score를 실수 문자열로 준다. epoch millis라 소수부는 언제나 0이다.
            long oldest = (long) Double.parseDouble(String.valueOf(raw.get(i + 1)));
            int waiting = Integer.parseInt(String.valueOf(raw.get(i + 2)));
            depths.add(new BucketDepth(buckets.get(index - 1), oldest, waiting));
        }
        return depths;
    }

    /**
     * 계획대로 bucket마다 오래 기다린 순으로 꺼낸다 (docs/07 §3.2).
     *
     * <p>어느 requestId를 어느 bucket에서 읽었는지 함께 돌려준다. DB에서 이미 끝난 요청은
     * 조건을 다시 읽을 수 없어, 이 정보가 없으면 어느 bucket에서 지울지 알 수 없다 (docs/07 §3.3).
     */
    public List<BucketSlice> waitingOldestFirst(BucketScanPlan plan) {
        if (plan.isEmpty()) {
            return List.of();
        }
        Object[] quotas = plan.quotas().stream().map(String::valueOf).toArray();
        List<?> raw = redis.execute(
                sliceScript, MatchingRedisKeys.queues(plan.buckets()), quotas);
        if (raw == null) {
            return List.of();
        }
        List<BucketSlice> slices = new ArrayList<>(plan.buckets().size());
        int cursor = 0;
        for (MatchBucket bucket : plan.buckets()) {
            int count = Integer.parseInt(String.valueOf(raw.get(cursor++)));
            List<UUID> ids = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String value = String.valueOf(raw.get(cursor++));
                try {
                    ids.add(UUID.fromString(value));
                } catch (IllegalArgumentException e) {
                    // 손상된 항목 하나가 그 큐의 매칭 전체를 멈추게 두지 않는다.
                    // 운영 도구나 예전 형식이 남긴 값일 수 있으므로 지우고 계속 간다.
                    log.warn("대기열에 requestId가 아닌 항목이 있어 제거한다 bucket={} value={}",
                            bucket.suffix(), value);
                    redis.opsForZSet().remove(MatchingRedisKeys.queue(bucket), value);
                }
            }
            if (!ids.isEmpty()) {
                slices.add(new BucketSlice(bucket, ids));
            }
        }
        return slices;
    }

    /** 한 bucket에서 읽어 온 대기자들. 어디서 읽었는지를 잃지 않기 위한 묶음이다. */
    public record BucketSlice(MatchBucket bucket, List<UUID> requestIds) {
    }

    /**
     * 대기열 키를 모르는 상태에서 guard만 푼다.
     *
     * <p>reconciliation은 이미 끝난 요청의 조건을 다시 읽을 수 없는 경우가 있어
     * 큐 키를 특정하지 못한다. 대기열 항목은 stale 제거가 따로 걷어 간다.
     *
     * @return 실제로 풀었으면 true
     */
    public boolean releaseAnywhere(UUID userId, UUID requestId) {
        String key = MatchingRedisKeys.activeRequest(userId);
        String current = redis.opsForValue().get(key);
        if (!requestId.toString().equals(current)) {
            return false;
        }
        return Boolean.TRUE.equals(redis.delete(key));
    }

    /**
     * 살아 있는 활성 요청 guard를 모두 훑는다 (userId -> requestId).
     *
     * <p>reconciliation 전용이다. DB에는 없는데 Redis에만 남은 guard를 찾으려면
     * Redis 쪽에서 출발해야 한다. 운영 주기 작업이므로 SCAN으로 조금씩 읽는다.
     */
    public Map<UUID, UUID> scanActiveRequests() {
        Map<UUID, UUID> found = new HashMap<>();
        String prefix = MatchingRedisKeys.activeRequestPrefix();
        ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String value = redis.opsForValue().get(key);
                if (value == null) {
                    continue;
                }
                try {
                    found.put(UUID.fromString(key.substring(prefix.length())), UUID.fromString(value));
                } catch (IllegalArgumentException e) {
                    log.warn("guard 키를 해석하지 못해 건너뛴다 key={} value={}", key, value);
                }
            }
        }
        return found;
    }

    /** 모드 전체의 대기 인원. bucket으로 나뉘어 있어 합계를 구한다. 대기 화면과 metric에 쓴다. */
    public long waitingCount(List<MatchBucket> buckets) {
        if (buckets.isEmpty()) {
            return 0L;
        }
        Long total = redis.execute(countScript, MatchingRedisKeys.queues(buckets));
        return total == null ? 0L : total;
    }
}
