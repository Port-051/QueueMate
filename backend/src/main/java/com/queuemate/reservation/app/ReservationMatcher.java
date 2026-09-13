package com.queuemate.reservation.app;

import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.ActiveProposalClaim;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.PartyAssembler;
import com.queuemate.matching.domain.PartyCandidate;
import com.queuemate.matching.domain.ProposalMember;
import com.queuemate.matching.domain.MatchingEvents;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.domain.RandomSource;
import com.queuemate.matching.infra.ActiveProposalClaimRepository;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import com.queuemate.reservation.domain.Reservation;
import com.queuemate.reservation.domain.ReservationStatus;
import com.queuemate.reservation.domain.TimeSlots;
import com.queuemate.reservation.infra.ReservationRepository;
import com.queuemate.reservation.infra.ReservationSlotIndex;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 예약 매칭 (docs/04 §4~§8).
 *
 * <p>실시간과 같은 hard filter, 같은 등급, 같은 수락 모델을 쓴다. 다른 것은 두 가지뿐이다.
 * 시간대가 겹쳐야 하고, 플레이할 양이 같아야 한다.
 */
@Service
public class ReservationMatcher {

    private static final Logger log = LoggerFactory.getLogger(ReservationMatcher.class);

    private final ReservationRepository reservations;
    private final ReservationSlotIndex slots;
    private final MatchProposalRepository proposals;
    private final ProposalMemberRepository proposalMembers;
    private final ProposalClaimRepository claims;
    private final ActiveProposalClaimRepository claimRows;
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final BlockLookupPort blocks;
    private final RandomSource random;
    private final ApplicationEventPublisher events;
    private com.queuemate.matching.domain.MatchPoolPolicy poolPolicy = com.queuemate.matching.domain.MatchPoolPolicy.UNRESTRICTED;

    @Autowired(required = false)
    public void setPoolPolicy(com.queuemate.matching.domain.MatchPoolPolicy policy) { this.poolPolicy = policy; }

    private final Clock clock;
    private final Duration proposalTtl;

    @Autowired
    public ReservationMatcher(ReservationRepository reservations, ReservationSlotIndex slots,
                              MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                              ProposalClaimRepository claims,
                              ActiveProposalClaimRepository claimRows, GameModeConfigProvider modes,
                              MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                              ApplicationEventPublisher events,
                              @Value("${queuemate.proposal.ttl-seconds:20}") long proposalTtlSeconds) {
        this(reservations, slots, proposals, proposalMembers, claims, claimRows, modes, codec,
                blocks, random, events, Clock.systemUTC(), Duration.ofSeconds(proposalTtlSeconds));
    }

    ReservationMatcher(ReservationRepository reservations, ReservationSlotIndex slots,
                       MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                       ProposalClaimRepository claims,
                       ActiveProposalClaimRepository claimRows, GameModeConfigProvider modes,
                       MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                       ApplicationEventPublisher events, Clock clock, Duration proposalTtl) {
        this.reservations = reservations;
        this.slots = slots;
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
    }

    /**
     * 이 예약을 기준으로 같이 할 사람들을 찾아 제안을 만든다.
     *
     * @return 제안이 만들어졌으면 그 id, 조합이 없으면 empty
     */
    @Transactional
    public Optional<UUID> tryMatchFor(UUID reservationId) {
        Optional<Reservation> found = reservations.findById(reservationId)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Candidate seed = toCandidate(found.get());
        poolPolicy.lock(seed.condition().game(), seed.condition().modeKey());
        if (!poolPolicy.automatic(reservationId)) return Optional.empty();
        Optional<GameModeConfig> config = modes.findActive(
                seed.condition().game(), seed.condition().modeKey());
        if (config.isEmpty()) {
            return Optional.empty();
        }
        List<Candidate> pool = loadCandidates(seed);
        if (pool.size() < config.get().targetPartySize() - 1) {
            return Optional.empty();
        }
        // seed를 0번에 놓고 그 자리에서만 조립한다. 예약은 기준 예약이 정해진 매칭이다.
        List<Candidate> all = new ArrayList<>(pool.size() + 1);
        all.add(seed);
        all.addAll(pool);
        return PartyAssembler.over(all, config.get(), blocks, random)
                .assembleFrom(0, (party, candidate) -> commonSlotOf(party, candidate).isPresent()
                        && party.stream().allMatch(member -> poolPolicy.compatible(member.reservation().getId(),
                        member.condition(), candidate.reservation().getId(), candidate.condition())))
                .flatMap(party -> claimAndPropose(party, config.get()));
    }

    /**
     * 등록 직후 놓친 조합을 다시 훑는다 (docs/04 §6).
     *
     * @return 이번에 만든 제안 수
     */
    @Transactional
    public int sweep() {
        int created = 0;
        for (Reservation reservation :
                reservations.findAllByStatusOrderByAvailableFromAsc(ReservationStatus.ACTIVE)) {
            // 앞선 매칭으로 이미 상태가 바뀌었을 수 있다.
            if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                continue;
            }
            if (tryMatchFor(reservation.getId()).isPresent()) {
                created++;
            }
        }
        return created;
    }

    /** 슬롯이 겹치는 ACTIVE 예약 중 같은 모드, 같은 플레이 양인 것만 남긴다. */
    private List<Candidate> loadCandidates(Candidate seed) {
        Set<UUID> ids = slots.candidatesOverlapping(
                seed.condition().game(), seed.condition().modeKey(),
                seed.reservation().getAvailableFrom(), seed.reservation().getAvailableTo(),
                seed.reservation().getId());
        if (ids.isEmpty()) {
            return List.of();
        }
        var sourceIds = new java.util.HashSet<>(ids); sourceIds.add(seed.reservation().getId());
        poolPolicy.prepare(sourceIds);
        List<Candidate> candidates = new ArrayList<>();
        for (Reservation reservation :
                reservations.findAllByIdInAndStatus(ids, ReservationStatus.ACTIVE)) {
            if (reservation.getPlayAmount() != seed.reservation().getPlayAmount()) {
                continue;
            }
            if (reservation.getUserId().equals(seed.userId())) {
                continue;
            }
            if (!reservation.window().overlaps(seed.reservation().window())) {
                continue;
            }
            if (poolPolicy.automatic(reservation.getId())) candidates.add(toCandidate(reservation));
        }
        return candidates;
    }

    @Transactional
    public Optional<UUID> proposeSelected(List<UUID> ids) {
        if (ids.isEmpty() || ids.stream().distinct().count() != ids.size()) return Optional.empty();
        var first = reservations.findById(ids.getFirst()).orElse(null);
        if (first == null) return Optional.empty();
        var condition = codec.fromJson(first.getConditionJson());
        poolPolicy.lock(condition.game(), condition.modeKey());
        var config = modes.findActive(condition.game(), condition.modeKey()).orElse(null);
        if (config == null || ids.size() != config.targetPartySize()) return Optional.empty();
        poolPolicy.prepare(ids);
        var party = reservations.lockAllActive(ids).stream().map(this::toCandidate).toList();
        if (party.size() != ids.size() || party.stream().anyMatch(c -> c.reservation().getPlayAmount() != first.getPlayAmount()))
            return Optional.empty();
        return PartyAssembler.over(party, config, blocks, random)
                .assembleFrom(0, (members, candidate) -> commonSlotOf(members, candidate).isPresent()
                        && members.stream().allMatch(member -> poolPolicy.compatible(member.reservation().getId(),
                        member.condition(), candidate.reservation().getId(), candidate.condition())))
                .flatMap(selected -> claimAndPropose(selected, config));
    }

    private Optional<UUID> claimAndPropose(List<Candidate> party, GameModeConfig config) {
        List<UUID> userIds = party.stream().map(Candidate::userId).toList();
        if (blocks.anyBlockBetween(userIds)) {
            log.info("차단 관계가 확인되어 예약 제안을 만들지 않는다 users={}", userIds);
            return Optional.empty();
        }

        // Redis가 claim을 잃었을 수 있다. 영속 진실을 직접 본다 (INV-2).
        if (claimRows.existsByUserIdIn(userIds)) {
            log.warn("Redis에는 없지만 DB에 살아 있는 claim이 있다. 제안을 만들지 않는다 users={}",
                    userIds);
            return Optional.empty();
        }
        Optional<OffsetDateTime> scheduledStart = earliestCommonSlot(party);
        if (scheduledStart.isEmpty()) {
            return Optional.empty();
        }

        UUID proposalId = UUID.randomUUID();
        if (!claims.claimAllForReservation(proposalId, proposalTtl, userIds)) {
            return Optional.empty();
        }
        releaseClaimsIfRolledBack(proposalId, userIds);

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plus(proposalTtl);
        proposals.saveAndFlush(MatchProposal.pending(
                proposalId, ProposalSourceType.RESERVATION, expiresAt));
        // INV-2의 영속 방어선. 예약은 원본 행을 잠그지 않으므로 여기가 유일한 DB 방어선이다.
        holdClaims(proposalId, userIds, expiresAt);
        for (Candidate candidate : party) {
            proposalMembers.save(ProposalMember.pending(
                    proposalId, candidate.userId(), candidate.reservation().getId()));
            candidate.reservation().attachToProposal(proposalId, scheduledStart.get());
        }
        events.publishEvent(new MatchingEvents.ProposalCreated(
                proposalId, ProposalSourceType.RESERVATION, userIds,
                expiresAt, scheduledStart.get()));
        log.info("예약 제안 생성 proposalId={} game={} mode={} start={} size={}",
                proposalId, config.game(), config.modeKey(), scheduledStart.get(), party.size());
        return Optional.of(proposalId);
    }

    /**
     * INV-2를 DB에도 새긴다.
     *
     * <p>실시간은 match_request 행을 {@code FOR UPDATE}로 잠가 같은 사람이 두 번 뽑히는 것을
     * 막지만, 예약은 원본을 잠그지 않는다. 그래서 예약 쪽 INV-2는 지금까지 Redis claim
     * 하나에만 걸려 있었다. user_id PK가 그 자리를 메운다.
     */
    private void holdClaims(UUID proposalId, List<UUID> userIds, OffsetDateTime expiresAt) {
        try {
            for (UUID userId : userIds) {
                claimRows.save(ActiveProposalClaim.held(userId, proposalId, expiresAt));
            }
            claimRows.flush();
        } catch (DataIntegrityViolationException e) {
            log.error("INV-2 방어선이 걸렸다. Redis claim과 DB가 어긋나 있다 proposalId={} users={}",
                    proposalId, userIds);
            throw e;
        }
    }

    private Optional<OffsetDateTime> commonSlotOf(List<Candidate> party, Candidate candidate) {
        List<TimeSlots.Window> windows = new ArrayList<>(party.size() + 1);
        party.forEach(member -> windows.add(member.reservation().window()));
        windows.add(candidate.reservation().window());
        return TimeSlots.earliestCommonSlot(windows, OffsetDateTime.now(clock));
    }

    private Optional<OffsetDateTime> earliestCommonSlot(List<Candidate> party) {
        // 이미 시작한 시간대를 약속 시각으로 주지 않는다.
        return TimeSlots.earliestCommonSlot(
                party.stream().map(candidate -> candidate.reservation().window()).toList(),
                OffsetDateTime.now(clock));
    }

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

    private Candidate toCandidate(Reservation reservation) {
        return new Candidate(reservation, codec.fromJson(reservation.getConditionJson()));
    }

    private record Candidate(Reservation reservation, MatchCondition condition)
            implements PartyCandidate {
        @Override
        public UUID userId() {
            return reservation.getUserId();
        }
    }
}
