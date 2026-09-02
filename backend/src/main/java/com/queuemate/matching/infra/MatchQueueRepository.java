package com.queuemate.matching.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    public MatchQueueRepository(StringRedisTemplate redis) {
        this.redis = redis;
        this.acquireScript = LuaScripts.load("redis/acquire-active-request.lua", Long.class);
        this.releaseScript = LuaScripts.load("redis/release-active-request.lua", Long.class);
    }

    /**
     * 활성 요청 guard를 잡고 대기열에 등록한다.
     *
     * @param queuedAt 최초 대기 시작 시각. proposal을 거절하고 큐로 돌아올 때는 처음 값을
     *                 그대로 넘겨야 aging을 잃지 않는다 (docs/03 §8)
     * @return 등록되면 true, 이미 활성 요청이 있으면 false (호출자는 409로 응답한다)
     */
    public boolean acquire(UUID userId, UUID requestId, String queueKey, Instant queuedAt) {
        return Long.valueOf(1L).equals(redis.execute(
                acquireScript,
                List.of(MatchingRedisKeys.activeRequest(userId), queueKey),
                requestId.toString(), String.valueOf(queuedAt.toEpochMilli())));
    }

    /**
     * 활성 요청 guard를 해제하고 대기열에서 뺀다.
     * 값이 일치할 때만 지우므로, 취소 직후 새로 등록한 요청을 늦게 도착한 취소가 지우지 못한다.
     *
     * @return 해제되면 true, 이미 없거나 다른 요청이 자리를 차지했으면 false
     */
    public boolean release(UUID userId, UUID requestId, String queueKey) {
        return Long.valueOf(1L).equals(redis.execute(
                releaseScript,
                List.of(MatchingRedisKeys.activeRequest(userId), queueKey),
                requestId.toString()));
    }

    /**
     * 제안이 깨진 요청을 대기열로 되돌린다. guard는 계속 살아 있으므로 다시 잡지 않는다.
     * 최초 대기 시각을 그대로 넣어 오래 기다린 사람이 앞자리를 유지하게 한다 (docs/03 §8).
     */
    public void requeue(String queueKey, UUID requestId, Instant queuedAt) {
        redis.opsForZSet().add(queueKey, requestId.toString(), queuedAt.toEpochMilli());
    }

    /**
     * 대기열에 남았지만 DB에서는 이미 끝난 요청을 지운다.
     *
     * <p>지우지 않으면 scan 창(앞에서부터 N개) 앞자리를 이런 항목이 차지해,
     * 실제 대기자가 영원히 스캔되지 않는다.
     */
    public void removeStale(String queueKey, UUID requestId) {
        log.debug("대기열에서 끝난 요청을 제거한다 queueKey={} requestId={}", queueKey, requestId);
        redis.opsForZSet().remove(queueKey, requestId.toString());
    }

    /** 현재 활성 요청. 없으면 empty. */
    public Optional<UUID> activeRequestOf(UUID userId) {
        String value = redis.opsForValue().get(MatchingRedisKeys.activeRequest(userId));
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    /**
     * 오래 기다린 순으로 후보 requestId를 꺼낸다. starvation을 막기 위한 aging 순서다 (docs/03 §6).
     * 여기서 나온 결과는 이미 낡았을 수 있으므로, 실제 잠금은 atomic claim이 다시 검증한다.
     */
    public List<UUID> waitingOldestFirst(String queueKey, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit은 양수여야 한다");
        }
        Set<String> ids = redis.opsForZSet().range(queueKey, 0, limit - 1L);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            try {
                result.add(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                // 손상된 항목 하나가 그 큐의 매칭 전체를 멈추게 두지 않는다.
                // 운영 도구나 예전 형식이 남긴 값일 수 있으므로 지우고 계속 간다.
                log.warn("대기열에 requestId가 아닌 항목이 있어 제거한다 queueKey={} value={}", queueKey, id);
                redis.opsForZSet().remove(queueKey, id);
            }
        }
        return result;
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

    /** 대기열 길이. 대기 화면과 metric에 쓴다. */
    public long waitingCount(String queueKey) {
        Long size = redis.opsForZSet().size(queueKey);
        return size == null ? 0L : size;
    }
}
