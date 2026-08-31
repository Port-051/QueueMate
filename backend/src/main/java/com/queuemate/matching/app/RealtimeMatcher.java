package com.queuemate.matching.app;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.ClaimCandidate;
import com.queuemate.matching.domain.CompatibilityTier;
import com.queuemate.matching.domain.ConditionCompatibility;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.ProposalMember;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.domain.RandomSource;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 실시간 매칭 한 판 (docs/03).
 *
 * <pre>
 * hard filtering -> compatibility tiering -> 같은 tier 내부 random -> atomic claim -> proposal
 * </pre>
 *
 * <p>추천 목록을 만들지 않는다. 호환되는 사람 중에서 무작위로 고른다.
 * 후보 탐색 결과는 언제나 낡았을 수 있다고 보고, 실제 잠금은 Lua가 다시 검증한다.
 */
@Service
public class RealtimeMatcher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMatcher.class);

    private final MatchQueueRepository queue;
    private final MatchRequestRepository requests;
    private final MatchProposalRepository proposals;
    private final ProposalMemberRepository proposalMembers;
    private final ProposalClaimRepository claims;
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final BlockLookupPort blocks;
    private final RandomSource random;
    private final Clock clock;
    private final Duration proposalTtl;
    private final int scanSize;
    /** 한 판에서 seed로 삼아 볼 후보 수. 앞사람이 막혀도 뒤를 시도하되 무한정 훑지는 않는다. */
    private final int seedAttempts;

    @Autowired
    public RealtimeMatcher(MatchQueueRepository queue, MatchRequestRepository requests,
                           MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                           ProposalClaimRepository claims, GameModeConfigProvider modes,
                           MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                           @Value("${queuemate.proposal.ttl-seconds:20}") long proposalTtlSeconds,
                           @Value("${queuemate.matching.scan-size:50}") int scanSize,
                           @Value("${queuemate.matching.seed-attempts:50}") int seedAttempts) {
        this(queue, requests, proposals, proposalMembers, claims, modes, codec, blocks, random,
                Clock.systemUTC(), Duration.ofSeconds(proposalTtlSeconds), scanSize, seedAttempts);
    }

    RealtimeMatcher(MatchQueueRepository queue, MatchRequestRepository requests,
                    MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                    ProposalClaimRepository claims, GameModeConfigProvider modes,
                    MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                    Clock clock, Duration proposalTtl, int scanSize, int seedAttempts) {
        this.queue = queue;
        this.requests = requests;
        this.proposals = proposals;
        this.proposalMembers = proposalMembers;
        this.claims = claims;
        this.modes = modes;
        this.codec = codec;
        this.blocks = blocks;
        this.random = random;
        this.clock = clock;
        this.proposalTtl = proposalTtl;
        this.scanSize = scanSize;
        this.seedAttempts = Math.max(1, seedAttempts);
    }

    /**
     * 대기열에서 파티 하나를 만들어 본다.
     *
     * @return 제안이 만들어졌으면 그 id, 만들 조합이 없으면 empty
     */
    @Transactional
    public Optional<UUID> tryMatch(GameKey game, String modeKey) {
        Optional<GameModeConfig> found = modes.findActive(game, modeKey);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        GameModeConfig config = found.get();
        String queueKey = MatchingRedisKeys.queue(game, modeKey);

        List<Candidate> waiting = loadWaiting(queueKey);
        if (waiting.size() < config.targetPartySize()) {
            return Optional.empty();
        }

        // 가장 오래 기다린 사람부터 seed로 삼는다 (docs/03 §6 aging).
        // 다만 맨 앞 사람이 아무와도 맞지 않는다고 뒤에 있는 조합까지 막히면 안 된다.
        // 그렇게 두면 조건이 까다로운 사용자 한 명이 그 게임/모드 전체를 멈춰 세운다.
        int lastSeedIndex = Math.min(waiting.size() - config.targetPartySize(), seedAttempts - 1);
        for (int i = 0; i <= lastSeedIndex; i++) {
            Candidate seed = waiting.get(i);
            List<Candidate> pool = new ArrayList<>(waiting);
            pool.remove(i);

            Optional<List<Candidate>> party = buildParty(seed, pool, config);
            if (party.isEmpty()) {
                continue;
            }
            Optional<UUID> proposalId = claimAndPropose(party.get(), config, queueKey);
            if (proposalId.isPresent()) {
                return proposalId;
            }
            // 차단이 뒤늦게 확인됐거나 다른 매처가 먼저 잡았다. 다음 seed로 넘어간다.
        }
        return Optional.empty();
    }

    /** 대기열 순서대로 요청 상세를 읽는다. Redis에는 있는데 DB에서 이미 끝난 요청은 버린다. */
    private List<Candidate> loadWaiting(String queueKey) {
        List<UUID> requestIds = queue.waitingOldestFirst(queueKey, scanSize);
        if (requestIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, MatchRequest> byId = new HashMap<>();
        // 매칭 대상 행을 잠근다. 후보를 읽는 사이 사용자가 취소하면 어느 쪽 변경이
        // 유실될지 알 수 없기 때문이다.
        for (MatchRequest request :
                requests.lockAllByIdInAndStatus(requestIds, MatchRequestStatus.QUEUED)) {
            byId.put(request.getId(), request);
        }
        List<Candidate> candidates = new ArrayList<>(byId.size());
        for (UUID requestId : requestIds) {
            MatchRequest request = byId.get(requestId);
            if (request != null) {
                candidates.add(new Candidate(request, codec.fromJson(request.getConditionJson())));
            } else {
                // DB에서 이미 끝난 요청이다. 대기열에 남겨 두면 scan 창을 잠식해
                // 뒤에 있는 진짜 대기자가 영원히 보이지 않는다.
                queue.removeStale(queueKey, requestId);
            }
        }
        return candidates;
    }

    /**
     * seed에 한 명씩 붙여 정원을 채운다. 매 단계에서 가장 좋은 등급의 후보군을 만들고
     * 그 안에서 무작위로 고른다. 등급이 같으면 누구를 고르든 제품 관점에서 동등하다.
     */
    private Optional<List<Candidate>> buildParty(Candidate seed, List<Candidate> pool, GameModeConfig config) {
        List<Candidate> party = new ArrayList<>(config.targetPartySize());
        party.add(seed);
        Map<UUID, Set<UUID>> blockCache = new HashMap<>();

        while (party.size() < config.targetPartySize()) {
            Map<CompatibilityTier, List<Candidate>> byTier = new EnumMap<>(CompatibilityTier.class);
            for (Candidate candidate : pool) {
                if (isBlockedAgainst(candidate, party, blockCache)) {
                    continue;
                }
                List<MatchCondition> conditions = new ArrayList<>(party.size() + 1);
                party.forEach(member -> conditions.add(member.condition()));
                conditions.add(candidate.condition());
                ConditionCompatibility.forParty(conditions, config)
                        .ifPresent(tier -> byTier.computeIfAbsent(tier, key -> new ArrayList<>())
                                .add(candidate));
            }
            if (byTier.isEmpty()) {
                return Optional.empty();
            }
            CompatibilityTier best = byTier.keySet().stream().min(Comparator.naturalOrder()).orElseThrow();
            Candidate chosen = random.pick(byTier.get(best));
            party.add(chosen);
            pool.remove(chosen);
        }
        return Optional.of(party);
    }

    /** 후보 필터용 차단 확인. 캐시를 타므로 조금 낡을 수 있고, 확정 직전에 다시 본다 (INV-6). */
    private boolean isBlockedAgainst(Candidate candidate, List<Candidate> party,
                                     Map<UUID, Set<UUID>> blockCache) {
        Set<UUID> blocked = blockCache.computeIfAbsent(
                candidate.userId(), blocks::blockedUserIds);
        for (Candidate member : party) {
            if (blocked.contains(member.userId())) {
                return true;
            }
            Set<UUID> memberBlocked = blockCache.computeIfAbsent(
                    member.userId(), blocks::blockedUserIds);
            if (memberBlocked.contains(candidate.userId())) {
                return true;
            }
        }
        return false;
    }

    private Optional<UUID> claimAndPropose(List<Candidate> party, GameModeConfig config, String queueKey) {
        List<UUID> userIds = party.stream().map(Candidate::userId).toList();

        // 후보를 고른 사이에 차단이 생겼을 수 있다. 잠그기 직전에 DB로 다시 본다 (INV-6).
        if (blocks.anyBlockBetween(userIds)) {
            log.info("차단 관계가 확인되어 제안을 만들지 않는다 users={}", userIds);
            return Optional.empty();
        }

        UUID proposalId = UUID.randomUUID();
        List<ClaimCandidate> claimTargets = party.stream()
                .map(candidate -> new ClaimCandidate(candidate.userId(), candidate.requestId()))
                .toList();

        if (!claims.claimAll(queueKey, proposalId, proposalTtl, claimTargets)) {
            // 다른 매처가 먼저 잡았다. 다음 판에서 다시 시도한다.
            return Optional.empty();
        }
        undoClaimIfRolledBack(proposalId, party, queueKey);

        OffsetDateTime now = OffsetDateTime.now(clock);
        proposals.save(MatchProposal.pending(
                proposalId, ProposalSourceType.REALTIME, now.plus(proposalTtl)));
        for (Candidate candidate : party) {
            proposalMembers.save(ProposalMember.pending(
                    proposalId, candidate.userId(), candidate.requestId()));
            candidate.request().attachToProposal(proposalId);
        }
        log.info("제안 생성 proposalId={} game={} mode={} size={}",
                proposalId, config.game(), config.modeKey(), party.size());
        return Optional.of(proposalId);
    }

    /**
     * 제안 저장이 실패하면 claim을 통째로 되돌린다.
     *
     * <p>잠금만 푸는 것으로는 부족하다. claim은 참가자를 대기열에서도 빼 갔기 때문에,
     * 대기열 항목을 되돌리지 않으면 사용자는 큐에서 사라진 채 guard만 남아
     * 새 요청도 못 하는 상태로 갇힌다.
     */
    private void undoClaimIfRolledBack(UUID proposalId, List<Candidate> party, String queueKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        List<UUID> userIds = party.stream().map(Candidate::userId).toList();
        List<Candidate> snapshot = List.copyOf(party);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                claims.releaseClaims(proposalId, userIds);
                for (Candidate candidate : snapshot) {
                    queue.requeue(queueKey, candidate.requestId(),
                            candidate.request().getQueuedAt().toInstant());
                }
                log.warn("제안 저장이 실패해 claim을 되돌렸다 proposalId={} users={}", proposalId, userIds);
            }
        });
    }

    private record Candidate(MatchRequest request, MatchCondition condition) {
        UUID userId() {
            return request.getUserId();
        }

        UUID requestId() {
            return request.getId();
        }
    }
}
