package com.queuemate.matching.infra;

import com.queuemate.matching.domain.ClaimCandidate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 사용자별 Redis 분산 락 아래에서 Java로 참가자를 검증하고 전원을 선점한다.
 *
 * <p>WATCH는 검증 후 락 임대 만료·요청 교체를 감지하고, MULTI/EXEC은 참가자 기록과 큐
 * 제거를 함께 적용한다. Sentinel 기록 유실에 대한 최종 방어는 DB의 활성 참여 PK다.
 * Redis 오류는 호출자에게 전파해 새 매칭을 중단한다 (INV-10).
 */
@Repository
public class ProposalClaimRepository {
    private static final Logger log = LoggerFactory.getLogger(ProposalClaimRepository.class);
    private static final int MIN_PARTY_SIZE = 2;
    private final StringRedisTemplate redis;
    private final RedisClaimLock lock;
    private final MeterRegistry metrics;
    private final RedisScript<Long> releaseScript;

    @Autowired
    public ProposalClaimRepository(StringRedisTemplate redis, MeterRegistry metrics,
                                   @Value("${queuemate.matching.claim-lock-lease-ms:10000}") long leaseMs) {
        this.redis = redis;
        this.metrics = metrics;
        this.lock = new RedisClaimLock(redis, Duration.ofMillis(leaseMs), metrics);
        this.releaseScript = LuaScripts.load("redis/release-proposal-claim.lua", Long.class);
    }

    /** Redis 저장소 단독 테스트에서 사용하는 기본 구성. */
    public ProposalClaimRepository(StringRedisTemplate redis) {
        this(redis, new SimpleMeterRegistry(), 10_000);
    }

    /** 참가자 모두 가능하면 선점 기록을 남기고 각자의 큐에서 제거한다. */
    public boolean claimAll(UUID proposalId, Duration ttl, List<ClaimCandidate> candidates) {
        if (candidates == null || candidates.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("후보는 필수다");
        }
        List<UUID> userIds = candidates.stream().map(ClaimCandidate::userId).toList();
        validate(proposalId, ttl, userIds);
        return claim(proposalId, ttl, userIds, candidates, "realtime");
    }

    /** 예약도 같은 사용자별 mutex와 active-proposal 기록을 사용한다 (INV-2). */
    public boolean claimAllForReservation(UUID proposalId, Duration ttl, List<UUID> userIds) {
        validate(proposalId, ttl, userIds);
        return claim(proposalId, ttl, userIds, List.of(), "reservation");
    }

    private boolean claim(UUID proposalId, Duration ttl, List<UUID> users,
                          List<ClaimCandidate> candidates, String source) {
        Timer.Sample sample = Timer.start(metrics);
        String outcome = "error";
        try {
            boolean claimed = lock.execute(users, lease -> Boolean.TRUE.equals(redis.execute(new SessionCallback<Boolean>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> Boolean execute(RedisOperations<K, V> operations) {
                    return claimInSession((RedisOperations<String, String>) operations,
                            lease, proposalId, ttl, users, candidates);
                }
            })));
            outcome = claimed ? "success" : "conflict";
            return claimed;
        } catch (RuntimeException e) {
            // EXEC 응답이 유실되면 반영 여부를 단정할 수 없다. 성공으로 재시도하지 않는다.
            // DB는 롤백하며 유한한 제안 TTL과 기존 DB 기반 큐 복구가 남은 상태를 정리한다.
            log.warn("분산 락 선점 실패 source={} proposalId={}", source, proposalId, e);
            throw e;
        } finally {
            sample.stop(metrics.timer("qm.matching.claim.duration", "source", source, "outcome", outcome));
        }
    }

    private boolean claimInSession(RedisOperations<String, String> operations, RedisClaimLock.Lease lease,
                                   UUID proposalId, Duration ttl, List<UUID> users, List<ClaimCandidate> candidates) {
        List<String> proposalKeys = users.stream().map(MatchingRedisKeys::activeProposal).toList();
        List<String> requestKeys = candidates.stream().map(c -> MatchingRedisKeys.activeRequest(c.userId())).toList();
        String membersKey = MatchingRedisKeys.proposalMembers(proposalId);
        List<String> watched = new ArrayList<>(lease.keys());
        watched.addAll(proposalKeys);
        watched.addAll(requestKeys);
        watched.add(membersKey);
        boolean watching = false;
        boolean transaction = false;
        try {
            operations.watch(watched);
            watching = true;
            List<String> values = operations.opsForValue().multiGet(watched.subList(0, watched.size() - 1));
            if (values == null) throw new DataAccessResourceFailureException("선점 상태를 읽지 못했다");
            for (int i = 0; i < lease.keys().size(); i++) {
                if (!lease.token().equals(values.get(i))) return false;
            }
            int offset = lease.keys().size();
            for (int i = 0; i < users.size(); i++) {
                if (values.get(offset + i) != null) return false;
            }
            offset += users.size();
            for (int i = 0; i < candidates.size(); i++) {
                if (!candidates.get(i).requestId().toString().equals(values.get(offset + i))) return false;
            }
            // 이미 사용한 proposalId 재사용 및 잘못된 큐 타입을 쓰기 전에 거부한다.
            if (Boolean.TRUE.equals(operations.hasKey(membersKey))) return false;
            for (String bucket : candidates.stream().map(c -> MatchingRedisKeys.queue(c.bucket())).distinct().toList()) {
                DataType type = operations.type(bucket);
                if (type != DataType.NONE && type != DataType.ZSET) {
                    throw new DataAccessResourceFailureException("매칭 큐 타입이 올바르지 않다: " + bucket);
                }
            }
            transaction = true;
            operations.multi();
            for (String key : proposalKeys) operations.opsForValue().set(key, proposalId.toString(), ttl);
            operations.opsForSet().add(membersKey, users.stream().map(UUID::toString).toArray(String[]::new));
            operations.expire(membersKey, ttl);
            for (ClaimCandidate candidate : candidates) {
                operations.opsForZSet().remove(MatchingRedisKeys.queue(candidate.bucket()), candidate.requestId().toString());
            }
            List<Object> results = operations.exec();
            transaction = false;
            watching = false;
            if (results == null || results.isEmpty()) {
                metrics.counter("qm.matching.claim.transaction.conflict").increment();
                return false;
            }
            for (Object result : results) {
                if (result instanceof Throwable error) throw new DataAccessResourceFailureException("선점 저장 오류", error);
            }
            return true;
        } finally {
            try {
                if (transaction) operations.discard();
                else if (watching) operations.unwatch();
            } catch (RuntimeException cleanupError) {
                metrics.counter("qm.matching.claim.transaction.cleanup.failure").increment();
                log.warn("선점 Redis 세션 정리에 실패했다", cleanupError);
            }
        }
    }

    /**
     * 제안이 끝나 참가자 잠금을 푼다.
     *
     * <p>내 proposalId가 들어 있는 잠금만 지운다. 그냥 지우면, TTL이 먼저 끝나 다른 제안에
     * 다시 잡힌 사용자의 잠금을 뒤늦은 정리가 날려 버려 한 사람이 두 제안에 묶인다 (INV-2).
     *
     * @return 실제로 푼 잠금 수
     */
    public long releaseClaims(UUID proposalId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        List<String> keys = new ArrayList<>(userIds.size() + 1);
        for (UUID userId : userIds) {
            keys.add(MatchingRedisKeys.activeProposal(userId));
        }
        keys.add(MatchingRedisKeys.proposalMembers(proposalId));
        Long released = redis.execute(releaseScript, keys, proposalId.toString());
        return released == null ? 0 : released;
    }

    /** 현재 이 사용자를 잡고 있는 제안. 없으면 empty. */
    public Optional<UUID> activeProposalOf(UUID userId) {
        return Optional.ofNullable(redis.opsForValue().get(MatchingRedisKeys.activeProposal(userId)))
                .map(UUID::fromString);
    }

    private void validate(UUID proposalId, Duration ttl, List<UUID> userIds) {
        if (proposalId == null) throw new IllegalArgumentException("proposalId는 필수다");
        if (ttl == null || ttl.toMillis() < 1) throw new IllegalArgumentException("선점 TTL은 1ms 이상이어야 한다");
        if (userIds == null || userIds.size() < MIN_PARTY_SIZE) {
            throw new IllegalArgumentException("후보는 최소 " + MIN_PARTY_SIZE + "명이다");
        }
        if (userIds.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("사용자는 필수다");
        if (userIds.size() != new HashSet<>(userIds).size()) {
            throw new IllegalArgumentException("같은 사용자가 두 번 들어왔다");
        }
    }
}
