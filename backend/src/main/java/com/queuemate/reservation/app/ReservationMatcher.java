package com.queuemate.reservation.app;

import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.CompatibilityTier;
import com.queuemate.matching.domain.ConditionCompatibility;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.ProposalMember;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.domain.RandomSource;
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
import java.util.List;
import java.util.Map;
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
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final BlockLookupPort blocks;
    private final RandomSource random;
    private final Clock clock;
    private final Duration proposalTtl;

    @Autowired
    public ReservationMatcher(ReservationRepository reservations, ReservationSlotIndex slots,
                              MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                              ProposalClaimRepository claims, GameModeConfigProvider modes,
                              MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                              @Value("${queuemate.proposal.ttl-seconds:20}") long proposalTtlSeconds) {
        this(reservations, slots, proposals, proposalMembers, claims, modes, codec, blocks, random,
                Clock.systemUTC(), Duration.ofSeconds(proposalTtlSeconds));
    }

    ReservationMatcher(ReservationRepository reservations, ReservationSlotIndex slots,
                       MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                       ProposalClaimRepository claims, GameModeConfigProvider modes,
                       MatchConditionCodec codec, BlockLookupPort blocks, RandomSource random,
                       Clock clock, Duration proposalTtl) {
        this.reservations = reservations;
        this.slots = slots;
        this.proposals = proposals;
        this.proposalMembers = proposalMembers;
        this.claims = claims;
        this.modes = modes;
        this.codec = codec;
        this.blocks = blocks;
        this.random = random;
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
        Optional<GameModeConfig> config = modes.findActive(
                seed.condition().game(), seed.condition().modeKey());
        if (config.isEmpty()) {
            return Optional.empty();
        }
        List<Candidate> pool = loadCandidates(seed);
        if (pool.size() < config.get().targetPartySize() - 1) {
            return Optional.empty();
        }
        return buildParty(seed, pool, config.get())
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
            candidates.add(toCandidate(reservation));
        }
        return candidates;
    }

    private Optional<List<Candidate>> buildParty(Candidate seed, List<Candidate> pool,
                                                 GameModeConfig config) {
        List<Candidate> party = new ArrayList<>(config.targetPartySize());
        party.add(seed);
        Map<UUID, Set<UUID>> blockCache = new HashMap<>();

        while (party.size() < config.targetPartySize()) {
            Map<CompatibilityTier, List<Candidate>> byTier = new EnumMap<>(CompatibilityTier.class);
            for (Candidate candidate : pool) {
                if (isBlockedAgainst(candidate, party, blockCache)) {
                    continue;
                }
                // 전원의 시간이 여전히 겹쳐야 한다. 한 명만 겹치는 것으로는 부족하다.
                if (commonSlotOf(party, candidate).isEmpty()) {
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

    private Optional<UUID> claimAndPropose(List<Candidate> party, GameModeConfig config) {
        List<UUID> userIds = party.stream().map(Candidate::userId).toList();
        if (blocks.anyBlockBetween(userIds)) {
            log.info("차단 관계가 확인되어 예약 제안을 만들지 않는다 users={}", userIds);
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
        proposals.save(MatchProposal.pending(
                proposalId, ProposalSourceType.RESERVATION, now.plus(proposalTtl)));
        for (Candidate candidate : party) {
            proposalMembers.save(ProposalMember.pending(
                    proposalId, candidate.userId(), candidate.reservation().getId()));
            candidate.reservation().attachToProposal(proposalId, scheduledStart.get());
        }
        log.info("예약 제안 생성 proposalId={} game={} mode={} start={} size={}",
                proposalId, config.game(), config.modeKey(), scheduledStart.get(), party.size());
        return Optional.of(proposalId);
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

    private boolean isBlockedAgainst(Candidate candidate, List<Candidate> party,
                                     Map<UUID, Set<UUID>> blockCache) {
        Set<UUID> blocked = blockCache.computeIfAbsent(candidate.userId(), blocks::blockedUserIds);
        for (Candidate member : party) {
            if (blocked.contains(member.userId())) {
                return true;
            }
            if (blockCache.computeIfAbsent(member.userId(), blocks::blockedUserIds)
                    .contains(candidate.userId())) {
                return true;
            }
        }
        return false;
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

    private record Candidate(Reservation reservation, MatchCondition condition) {
        UUID userId() {
            return reservation.getUserId();
        }
    }
}
