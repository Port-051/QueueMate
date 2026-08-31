package com.queuemate.matching.infra;

import com.queuemate.matching.domain.ClaimCandidate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 후보 전원을 하나의 proposal에 원자적으로 잠근다 (INV-2, docs/03 §7).
 *
 * <p>후보 탐색과 claim은 분리된 단계다. 탐색 결과는 이미 낡았을 수 있으므로,
 * 실제 잠금은 Lua script 안에서 전원 검증과 함께 끝낸다.
 * GET → 애플리케이션 판단 → SET 으로 나누면 그 사이에 다른 matcher가 끼어든다.
 *
 * <p>Redis 장애 시 예외는 그대로 올린다. DB fallback으로 비원자적 매칭을 시도하지 않는다 (INV-10).
 */
@Repository
public class ProposalClaimRepository {

    private static final int MIN_PARTY_SIZE = 2;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> claimScript;
    private final RedisScript<Long> reservationClaimScript;
    private final RedisScript<Long> releaseScript;

    public ProposalClaimRepository(StringRedisTemplate redis) {
        this.redis = redis;
        this.claimScript = LuaScripts.load("redis/atomic-proposal-claim.lua", Long.class);
        this.reservationClaimScript =
                LuaScripts.load("redis/atomic-reservation-claim.lua", Long.class);
        this.releaseScript = LuaScripts.load("redis/release-proposal-claim.lua", Long.class);
    }

    /**
     * 후보 전원을 잠근다. 한 명이라도 다른 proposal에 묶여 있거나 활성 요청이 바뀌었으면
     * 아무것도 바꾸지 않고 실패한다.
     *
     * <p>성공하면 참가자의 requestId가 대기열에서 함께 제거된다.
     *
     * @param queueKey   후보를 꺼낸 대기열 키. {@link MatchingRedisKeys#queue}로 만든다
     * @param proposalId 새로 만들 proposal의 id
     * @param ttl        proposal 수락 제한 시간
     * @param candidates 잠글 후보. 최소 2명이고 중복될 수 없다
     * @return 전원 잠금에 성공하면 true, 한 명이라도 충돌하면 false
     */
    public boolean claimAll(String queueKey, UUID proposalId, Duration ttl, List<ClaimCandidate> candidates) {
        validate(queueKey, proposalId, ttl, candidates);

        List<String> keys = new ArrayList<>(2 + candidates.size() * 2);
        keys.add(queueKey);
        keys.add(MatchingRedisKeys.proposalMembers(proposalId));
        for (ClaimCandidate candidate : candidates) {
            keys.add(MatchingRedisKeys.activeProposal(candidate.userId()));
            keys.add(MatchingRedisKeys.activeRequest(candidate.userId()));
        }

        Object[] args = new Object[2 + candidates.size() * 2];
        args[0] = proposalId.toString();
        args[1] = String.valueOf(ttl.toSeconds());
        int i = 2;
        for (ClaimCandidate candidate : candidates) {
            args[i++] = candidate.requestId().toString();
            args[i++] = candidate.userId().toString();
        }

        return Long.valueOf(1L).equals(redis.execute(claimScript, keys, args));
    }

    /**
     * 예약 제안용 잠금. 실시간과 같은 active-proposal 키를 써서,
     * 한 사람이 실시간 제안과 예약 제안을 동시에 들고 있지 않게 한다 (INV-2).
     *
     * @param userIds 잠글 사용자. 최소 2명이고 중복될 수 없다
     * @return 전원 잠금에 성공하면 true
     */
    public boolean claimAllForReservation(UUID proposalId, Duration ttl, List<UUID> userIds) {
        if (proposalId == null) {
            throw new IllegalArgumentException("proposalId는 필수다");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl은 양수여야 한다. 영구 잠금을 만들지 않는다");
        }
        if (userIds == null || userIds.size() < MIN_PARTY_SIZE) {
            throw new IllegalArgumentException("후보는 최소 " + MIN_PARTY_SIZE + "명이다");
        }
        if (userIds.size() != new HashSet<>(userIds).size()) {
            throw new IllegalArgumentException("같은 사용자가 두 번 들어왔다");
        }

        List<String> keys = new ArrayList<>(userIds.size() + 1);
        keys.add(MatchingRedisKeys.proposalMembers(proposalId));
        for (UUID userId : userIds) {
            keys.add(MatchingRedisKeys.activeProposal(userId));
        }

        Object[] args = new Object[2 + userIds.size()];
        args[0] = proposalId.toString();
        args[1] = String.valueOf(ttl.toSeconds());
        for (int i = 0; i < userIds.size(); i++) {
            args[2 + i] = userIds.get(i).toString();
        }
        return Long.valueOf(1L).equals(redis.execute(reservationClaimScript, keys, args));
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

    private void validate(String queueKey, UUID proposalId, Duration ttl, List<ClaimCandidate> candidates) {
        if (queueKey == null || queueKey.isBlank()) {
            throw new IllegalArgumentException("queueKey는 필수다");
        }
        if (proposalId == null) {
            throw new IllegalArgumentException("proposalId는 필수다");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl은 양수여야 한다. 영구 잠금을 만들지 않는다");
        }
        if (candidates == null || candidates.size() < MIN_PARTY_SIZE) {
            throw new IllegalArgumentException("후보는 최소 " + MIN_PARTY_SIZE + "명이다");
        }
        Set<UUID> userIds = new HashSet<>();
        for (ClaimCandidate candidate : candidates) {
            if (!userIds.add(candidate.userId())) {
                // INV-7. Lua도 막지만 여기서 걸러야 원인이 드러난다.
                throw new IllegalArgumentException("같은 사용자가 두 번 들어왔다: " + candidate.userId());
            }
        }
    }
}
