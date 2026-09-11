package com.queuemate.matching.app;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.ActiveProposalClaim;
import com.queuemate.matching.domain.BucketDepth;
import com.queuemate.matching.domain.BucketScanPlan;
import com.queuemate.matching.domain.ClaimCandidate;
import com.queuemate.matching.domain.MatchBucket;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.ProposalMember;
import com.queuemate.matching.domain.MatchingEvents;
import com.queuemate.matching.domain.PartyAssembler;
import com.queuemate.matching.domain.PartyCandidate;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.domain.RandomSource;
import com.queuemate.matching.infra.ActiveProposalClaimRepository;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *
 * <p>입구는 둘이다. {@link #tryMatchAround}는 요청이 들어온 자리를 알고 부르는 평소 경로고,
 * {@link #tryMatch}는 모드 전체를 훑는 안전망 경로다. 고르는 규칙은 둘이 같다.
 * 언제 도는지는 {@link MatchTrigger}가 정한다.
 */
@Service
public class RealtimeMatcher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeMatcher.class);

    private final MatchQueueRepository queue;
    private final MatchRequestRepository requests;
    private final MatchProposalRepository proposals;
    private final ProposalMemberRepository proposalMembers;
    private final ProposalClaimRepository claims;
    private final ActiveProposalClaimRepository claimRows;
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final BlockLookupPort blocks;
    private final RandomSource random;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration proposalTtl;
    private final int scanSize;
    /** 한 판에서 seed로 삼아 볼 후보 수. 앞사람이 막혀도 뒤를 시도하되 무한정 훑지는 않는다. */
    private final int seedAttempts;

    @Autowired
    public RealtimeMatcher(MatchQueueRepository queue, MatchRequestRepository requests,
                           MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                           ProposalClaimRepository claims, ActiveProposalClaimRepository claimRows,
                           GameModeConfigProvider modes,
                           MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                           ApplicationEventPublisher events,
                           @Value("${queuemate.proposal.ttl-seconds:20}") long proposalTtlSeconds,
                           @Value("${queuemate.matching.scan-size:50}") int scanSize,
                           @Value("${queuemate.matching.seed-attempts:50}") int seedAttempts) {
        this(queue, requests, proposals, proposalMembers, claims, claimRows, modes, codec, blocks,
                random, events, Clock.systemUTC(), Duration.ofSeconds(proposalTtlSeconds),
                scanSize, seedAttempts);
    }

    RealtimeMatcher(MatchQueueRepository queue, MatchRequestRepository requests,
                    MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                    ProposalClaimRepository claims, ActiveProposalClaimRepository claimRows,
                    GameModeConfigProvider modes,
                    MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                    ApplicationEventPublisher events,
                    Clock clock, Duration proposalTtl, int scanSize, int seedAttempts) {
        this.queue = queue;
        this.requests = requests;
        this.proposals = proposals;
        this.proposalMembers = proposalMembers;
        this.claims = claims;
        this.claimRows = claimRows;
        this.modes = modes;
        this.codec = codec;
        this.blocks = blocks;
        this.random = random;
        this.events = events;
        this.clock = clock;
        this.proposalTtl = proposalTtl;
        this.scanSize = scanSize;
        this.seedAttempts = Math.max(1, seedAttempts);
    }

    /**
     * 요청이 들어온 bucket 주변에서 파티 하나를 만들어 본다.
     *
     * <p>평소 매칭은 전부 이 입구로 들어온다. 누가 대기열에 들어왔는지 알고 부르는 것이므로
     * 모드 전체를 훑지 않고 그 자리와 상대가 될 수 있는 bucket만 본다.
     *
     * @return 제안이 만들어졌으면 그 id, 만들 조합이 없으면 empty
     */
    @Transactional
    public Optional<UUID> tryMatchAround(MatchBucket anchor) {
        Optional<GameModeConfig> found = modes.findActive(anchor.game(), anchor.modeKey());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        GameModeConfig config = found.get();
        List<BucketDepth> depths =
                queue.bucketDepths(MatchBucket.compatibleWith(anchor, config));
        return match(config, BucketScanPlan.around(anchor, depths, config, scanSize));
    }

    /**
     * 모드 전체를 훑어 파티 하나를 만들어 본다.
     *
     * <p>어디를 볼지 모르는 채로 부르는 입구다. trigger가 유실됐을 때 멈춰 선 큐를 푸는
     * 안전망(MatchingScheduler)과, 매칭 한 판을 통째로 검증하는 테스트가 쓴다.
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
        List<BucketDepth> depths = queue.bucketDepths(MatchBucket.allFor(config));
        return match(config, BucketScanPlan.of(depths, config, scanSize));
    }

    private Optional<UUID> match(GameModeConfig config, BucketScanPlan plan) {
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        List<Candidate> waiting = loadWaiting(plan);
        if (waiting.size() < config.targetPartySize()) {
            return Optional.empty();
        }

        // 가장 오래 기다린 사람부터 seed로 삼는다 (docs/03 §6 aging).
        // 다만 맨 앞 사람이 아무와도 맞지 않는다고 뒤에 있는 조합까지 막히면 안 된다.
        // 그렇게 두면 조건이 까다로운 사용자 한 명이 그 게임/모드 전체를 멈춰 세운다.
        //
        // 차단 조회와 쌍 등급은 이 후보 집합 위에서 한 번만 구한다. seed를 바꿔도 값이 같다.
        PartyAssembler<Candidate> assembler =
                PartyAssembler.over(waiting, config, blocks, random);

        int lastSeedIndex = Math.min(waiting.size() - config.targetPartySize(), seedAttempts - 1);
        for (int i = 0; i <= lastSeedIndex; i++) {
            Optional<List<Candidate>> party = assembler.assembleFrom(i);
            if (party.isEmpty()) {
                continue;
            }
            Optional<UUID> proposalId = claimAndPropose(party.get(), config);
            if (proposalId.isPresent()) {
                return proposalId;
            }
            // 차단이 뒤늦게 확인됐거나 다른 매처가 먼저 잡았다. 다음 seed로 넘어간다.
        }
        return Optional.empty();
    }

    /**
     * 계획대로 이번 판의 후보를 읽는다 (docs/07 §3.2).
     *
     * <p>Redis에는 있는데 DB에서 이미 끝난 요청은 읽어 온 bucket에서 지운다.
     */
    private List<Candidate> loadWaiting(BucketScanPlan plan) {
        List<MatchQueueRepository.BucketSlice> slices = queue.waitingOldestFirst(plan);
        Map<UUID, MatchBucket> bucketOf = new LinkedHashMap<>();
        for (MatchQueueRepository.BucketSlice slice : slices) {
            for (UUID requestId : slice.requestIds()) {
                bucketOf.put(requestId, slice.bucket());
            }
        }
        if (bucketOf.isEmpty()) {
            return List.of();
        }

        // 매칭 대상 행을 잠근다. 후보를 읽는 사이 사용자가 취소하면 어느 쪽 변경이
        // 유실될지 알 수 없기 때문이다. 쿼리가 queuedAt 순으로 돌려주므로 aging이 유지된다.
        List<Candidate> candidates = new ArrayList<>(bucketOf.size());
        Set<UUID> alive = new HashSet<>();
        for (MatchRequest request :
                requests.lockAllByIdInAndStatus(bucketOf.keySet(), MatchRequestStatus.QUEUED)) {
            alive.add(request.getId());
            candidates.add(new Candidate(request, codec.fromJson(request.getConditionJson()),
                    bucketOf.get(request.getId())));
        }

        // DB에서 이미 끝난 요청이다. 대기열에 남겨 두면 그 bucket의 앞자리를 잠식해
        // 뒤에 있는 진짜 대기자가 영원히 보이지 않는다.
        Map<MatchBucket, List<UUID>> stale = new LinkedHashMap<>();
        bucketOf.forEach((requestId, bucket) -> {
            if (!alive.contains(requestId)) {
                stale.computeIfAbsent(bucket, key -> new ArrayList<>()).add(requestId);
            }
        });
        stale.forEach(queue::removeStale);
        return candidates;
    }

    private Optional<UUID> claimAndPropose(List<Candidate> party, GameModeConfig config) {
        List<UUID> userIds = party.stream().map(Candidate::userId).toList();

        // 후보를 고른 사이에 차단이 생겼을 수 있다. 잠그기 직전에 DB로 다시 본다 (INV-6).
        if (blocks.anyBlockBetween(userIds)) {
            log.info("차단 관계가 확인되어 제안을 만들지 않는다 users={}", userIds);
            return Optional.empty();
        }

        // Redis가 claim을 잃었을 수 있다. 영속 진실을 직접 본다 (INV-2).
        if (claimRows.existsByUserIdIn(userIds)) {
            log.warn("Redis에는 없지만 DB에 살아 있는 claim이 있다. 제안을 만들지 않는다 users={}",
                    userIds);
            return Optional.empty();
        }

        UUID proposalId = UUID.randomUUID();
        List<ClaimCandidate> claimTargets = party.stream()
                .map(candidate -> new ClaimCandidate(
                        candidate.userId(), candidate.requestId(), candidate.bucket()))
                .toList();

        if (!claims.claimAll(proposalId, proposalTtl, claimTargets)) {
            // 다른 매처가 먼저 잡았다. 다음 판에서 다시 시도한다.
            return Optional.empty();
        }
        undoClaimIfRolledBack(proposalId, party);

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plus(proposalTtl);
        proposals.saveAndFlush(MatchProposal.pending(
                proposalId, ProposalSourceType.REALTIME, expiresAt));
        // INV-2의 영속 방어선. Redis가 claim을 잃어도 두 번째 제안은 여기서 막힌다.
        holdClaims(proposalId, userIds, expiresAt);
        for (Candidate candidate : party) {
            proposalMembers.save(ProposalMember.pending(
                    proposalId, candidate.userId(), candidate.requestId()));
            candidate.request().attachToProposal(proposalId);
        }
        events.publishEvent(new MatchingEvents.ProposalCreated(
                proposalId, ProposalSourceType.REALTIME, userIds, expiresAt, null));
        log.info("제안 생성 proposalId={} game={} mode={} size={}",
                proposalId, config.game(), config.modeKey(), party.size());
        return Optional.of(proposalId);
    }

    /**
     * INV-2를 DB에도 새긴다.
     *
     * <p>user_id가 PK이므로 같은 사람이 두 제안에 묶이면 저장이 거부된다. 그 상황은
     * Redis와 DB가 어긋났다는 뜻이라 조용히 넘어가지 않는다. 트랜잭션은 되돌아가고
     * {@link #undoClaimIfRolledBack}이 Redis claim과 대기열을 함께 복구한다.
     */
    private void holdClaims(UUID proposalId, List<UUID> userIds, OffsetDateTime expiresAt) {
        try {
            for (UUID userId : userIds) {
                claimRows.save(ActiveProposalClaim.held(userId, proposalId, expiresAt));
            }
            claimRows.flush();
        } catch (DataIntegrityViolationException e) {
            // active_proposal_claims PK. Redis는 비어 있다고 했는데 DB에는 살아 있는 claim이 있다.
            log.error("INV-2 방어선이 걸렸다. Redis claim과 DB가 어긋나 있다 proposalId={} users={}",
                    proposalId, userIds);
            throw e;
        }
    }

    /**
     * 제안 저장이 실패하면 claim을 통째로 되돌린다.
     *
     * <p>잠금만 푸는 것으로는 부족하다. claim은 참가자를 대기열에서도 빼 갔기 때문에,
     * 대기열 항목을 되돌리지 않으면 사용자는 큐에서 사라진 채 guard만 남아
     * 새 요청도 못 하는 상태로 갇힌다.
     */
    private void undoClaimIfRolledBack(UUID proposalId, List<Candidate> party) {
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
                    queue.requeue(MatchingRedisKeys.queue(candidate.bucket()),
                            candidate.requestId(), candidate.request().getQueuedAt().toInstant());
                }
                log.warn("제안 저장이 실패해 claim을 되돌렸다 proposalId={} users={}", proposalId, userIds);
            }
        });
    }

    private record Candidate(MatchRequest request, MatchCondition condition, MatchBucket bucket)
            implements PartyCandidate {
        @Override
        public UUID userId() {
            return request.getUserId();
        }

        UUID requestId() {
            return request.getId();
        }
    }
}
