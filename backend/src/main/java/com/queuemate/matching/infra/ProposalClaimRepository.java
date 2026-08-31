package com.queuemate.matching.infra;

import com.queuemate.matching.domain.ClaimCandidate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    public ProposalClaimRepository(StringRedisTemplate redis) {
        this.redis = redis;
        this.claimScript = LuaScripts.load("redis/atomic-proposal-claim.lua", Long.class);
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
