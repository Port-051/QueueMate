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

    @Autowired
    public RealtimeMatcher(MatchQueueRepository queue, MatchRequestRepository requests,
                           MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                           ProposalClaimRepository claims, GameModeConfigProvider modes,
                           MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                           @Value("${queuemate.proposal.ttl-seconds:20}") long proposalTtlSeconds,
                           @Value("${queuemate.matching.scan-size:50}") int scanSize) {
        this(queue, requests, proposals, proposalMembers, claims, modes, codec, blocks, random,
                Clock.systemUTC(), Duration.ofSeconds(proposalTtlSeconds), scanSize);
    }

    RealtimeMatcher(MatchQueueRepository queue, MatchRequestRepository requests,
                    MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                    ProposalClaimRepository claims, GameModeConfigProvider modes,
                    MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                    Clock clock, Duration proposalTtl, int scanSize) {
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

        // 가장 오래 기다린 사람을 기준으로 파티를 짠다. starvation 방지 (docs/03 §6).
        Candidate seed = waiting.get(0);
        List<Candidate> pool = new ArrayList<>(waiting.subList(1, waiting.size()));
        Optional<List<Candidate>> party = buildParty(seed, pool, config);
        if (party.isEmpty()) {
            return Optional.empty();
        }
        return claimAndPropose(party.get(), config, queueKey);
    }

    /** 대기열 순서대로 요청 상세를 읽는다. Redis에는 있는데 DB에서 이미 끝난 요청은 버린다. */
    private List<Candidate> loadWaiting(String queueKey) {
        List<UUID> requestIds = queue.waitingOldestFirst(queueKey, scanSize);
        if (requestIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, MatchRequest> byId = new HashMap<>();
        for (MatchRequest request :
                requests.findAllByIdInAndStatus(requestIds, MatchRequestStatus.QUEUED)) {
            byId.put(request.getId(), request);
        }
        List<Candidate> candidates = new ArrayList<>(byId.size());
        for (UUID requestId : requestIds) {
            MatchRequest request = byId.get(requestId);
            if (request != null) {
                candidates.add(new Candidate(request, codec.fromJson(request.getConditionJson())));
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
        releaseClaimsIfRolledBack(proposalId, userIds);

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
     * 제안 저장이 실패하면 Redis 잠금도 푼다.
     * TTL이 있어 언젠가는 풀리지만, 그동안 참가자들은 아무 제안도 못 받은 채 묶여 있게 된다.
     */
    private void releaseClaimsIfRolledBack(UUID proposalId, List<UUID> userIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    claims.releaseClaims(proposalId, userIds);
                }
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
