package com.queuemate.matching.app;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.Acceptance;
import com.queuemate.matching.domain.BlockedPairProposalGuard;
import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.MatchingEvents;
import com.queuemate.matching.domain.PartyCreationPort;
import com.queuemate.matching.domain.ProposalMember;
import com.queuemate.matching.domain.ProposalParticipants;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.infra.AfterCommit;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 제안 수명주기 (docs/03 §8). 실시간과 예약이 같은 수락 모델을 공유한다.
 *
 * <p>INV-4: 전원이 수락하기 전에는 파티를 만들지 않는다.
 * INV-5: 끝난 제안은 되살아나지 않는다. 만료와 수락이 겹치면 먼저 도달한 종결이 이긴다.
 */
@Service
public class ProposalService implements BlockedPairProposalGuard {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final MatchProposalRepository proposals;
    private final ProposalMemberRepository proposalMembers;
    private final ProposalClaimRepository claims;
    private final GameModeConfigProvider modes;
    private final Map<ProposalSourceType, ProposalParticipants> participants;
    private final ObjectProvider<PartyCreationPort> partyCreation;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public ProposalService(MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                           ProposalClaimRepository claims, GameModeConfigProvider modes,
                           List<ProposalParticipants> participants,
                           ObjectProvider<PartyCreationPort> partyCreation,
                           ApplicationEventPublisher events) {
        this(proposals, proposalMembers, claims, modes, participants, partyCreation, events,
                Clock.systemUTC());
    }

    ProposalService(MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                    ProposalClaimRepository claims, GameModeConfigProvider modes,
                    List<ProposalParticipants> participants,
                    ObjectProvider<PartyCreationPort> partyCreation,
                    ApplicationEventPublisher events, Clock clock) {
        this.proposals = proposals;
        this.proposalMembers = proposalMembers;
        this.claims = claims;
        this.modes = modes;
        this.participants = new EnumMap<>(ProposalSourceType.class);
        participants.forEach(handler -> this.participants.put(handler.sourceType(), handler));
        this.partyCreation = partyCreation;
        this.events = events;
        this.clock = clock;
    }

    /** 참가자 한 명이 수락한다. 전원이 수락하면 그 자리에서 확정한다. */
    @Transactional
    public ProposalView accept(UUID userId, UUID proposalId) {
        MatchProposal proposal = loadForUpdate(proposalId);
        List<ProposalMember> members = membersOf(proposalId);
        ProposalMember me = memberOf(members, userId, proposalId);

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (proposal.getStatus() == ProposalStatus.PENDING && proposal.isExpiredAt(now)) {
            // 만료된 제안을 수락으로 되살리지 않는다 (INV-5).
            // 여기서 정리까지 하면 안 된다. 이 예외가 트랜잭션을 롤백시켜 DB는 그대로인데
            // Redis 잠금만 풀리고, 그 사용자가 다른 제안에 또 잡힐 수 있다.
            // 정리는 별도 트랜잭션인 sweep이 맡는다.
            throw new ConflictException("PROPOSAL_EXPIRED", "제안이 만료됐다");
        }
        requirePending(proposal);

        me.respond(Acceptance.ACCEPTED);
        UUID partyId = null;
        if (allAccepted(members)) {
            partyId = confirm(proposal, members, now);
        }
        return view(proposal, members, partyId);
    }

    /** 참가자 한 명이 거절하면 제안 전체가 끝난다. 나머지는 조건을 유지한 채 대기로 돌아간다. */
    @Transactional
    public void decline(UUID userId, UUID proposalId) {
        MatchProposal proposal = loadForUpdate(proposalId);
        List<ProposalMember> members = membersOf(proposalId);
        ProposalMember me = memberOf(members, userId, proposalId);
        requirePending(proposal);

        me.respond(Acceptance.DECLINED);
        proposal.decline();
        breakProposal(proposal, members);
        log.info("제안 거절 proposalId={} by={}", proposalId, userId);
    }

    /**
     * 차단이 생겨 함께 있을 수 없게 된 제안을 닫는다 (INV-6).
     *
     * <p>social이 block을 만드는 트랜잭션에서 부른다. 매칭 쪽에서 아무리 조심해도
     * "후보를 고른 뒤 차단이 생기는" 창은 남기 때문에, 차단을 만드는 쪽에서도 막는다.
     */
    @Override
    @Transactional
    public int cancelSharedPendingProposals(UUID userId, UUID otherUserId) {
        if (userId == null || otherUserId == null || userId.equals(otherUserId)) {
            return 0;
        }
        List<UUID> shared = proposalMembers.findPendingProposalsSharedBy(
                List.of(userId, otherUserId));
        int closed = 0;
        for (UUID proposalId : shared) {
            if (cancelForWithdrawnSource(proposalId)) {
                closed++;
            }
        }
        if (closed > 0) {
            log.info("차단으로 제안 파기 count={} users=[{}, {}]", closed, userId, otherUserId);
        }
        return closed;
    }

    /**
     * 참가자 한 명이 원본(예약)을 철회해 제안을 더 진행할 수 없다.
     *
     * <p>철회한 사람의 원본은 호출자가 정리한다. 여기서는 제안을 닫고 나머지 참가자를
     * 원래 상태로 돌려놓는다. 제안 행을 잠그므로 동시에 들어온 수락과 직렬화된다.
     *
     * @return 이번 호출로 제안을 닫았으면 true, 이미 끝나 있었으면 false
     */
    @Transactional
    public boolean cancelForWithdrawnSource(UUID proposalId) {
        MatchProposal proposal = loadForUpdate(proposalId);
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            return false;
        }
        List<ProposalMember> members = membersOf(proposalId);
        proposal.cancel();
        breakProposal(proposal, members);
        log.info("원본 철회로 제안 파기 proposalId={}", proposalId);
        return true;
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID userId, UUID proposalId) {
        MatchProposal proposal = load(proposalId);
        List<ProposalMember> members = membersOf(proposalId);
        memberOf(members, userId, proposalId); // 참가자가 아니면 존재를 알리지 않는다
        return view(proposal, members, partyIdOf(proposal));
    }

    /**
     * 기한이 지난 제안을 정리한다. Redis key expiry에 의존하지 않는다 (docs/07 §6).
     *
     * @return 정리한 제안 수
     */
    @Transactional
    public int expireOverdue() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<MatchProposal> overdue =
                proposals.findAllByStatusAndExpiresAtLessThanEqual(ProposalStatus.PENDING, now);
        for (MatchProposal candidate : overdue) {
            // 수락이 동시에 들어오고 있을 수 있다. 잠근 뒤 상태를 다시 본다.
            MatchProposal proposal = loadForUpdate(candidate.getId());
            if (proposal.getStatus() != ProposalStatus.PENDING) {
                continue;
            }
            expire(proposal, membersOf(proposal.getId()));
        }
        if (!overdue.isEmpty()) {
            log.info("만료된 제안 정리 count={}", overdue.size());
        }
        return overdue.size();
    }

    private UUID confirm(MatchProposal proposal, List<ProposalMember> members, OffsetDateTime now) {
        proposal.confirm(now);

        ProposalParticipants handler = handlerFor(proposal);
        List<UUID> sourceIds = members.stream().map(ProposalMember::getSourceRequestId).toList();
        List<UUID> userIds = members.stream().map(ProposalMember::getUserId).toList();

        ProposalParticipants.PartyPlan plan = handler.planFor(sourceIds)
                .orElseThrow(() -> new IllegalStateException(
                        "확정할 제안의 원본을 찾을 수 없다: " + proposal.getId()));
        GameModeConfig config = modes.findActive(plan.game(), plan.modeKey())
                .orElseThrow(() -> new IllegalStateException(
                        "확정 시점에 모드 설정이 사라졌다: " + plan.game() + "/" + plan.modeKey()));

        // 파티 생성 구현은 확정 사실을 DB에서 직접 읽어 다시 확인한다(INV-4, INV-5).
        // 여기까지의 변경은 영속성 컨텍스트에만 있고, 그 사이 조회들은 다른 테이블을 봐서
        // Hibernate가 match_proposals를 flush하지 않는다. 밀어 넣고 부른다.
        proposals.saveAndFlush(proposal);
        UUID partyId = createParty(proposal, config, userIds, plan.scheduledStart());
        handler.onConfirmed(sourceIds);
        UUID proposalId = proposal.getId();
        AfterCommit.run(() -> claims.releaseClaims(proposalId, userIds));
        publishSettled(proposal, userIds);
        log.info("제안 확정 proposalId={} partyId={} size={}", proposalId, partyId, members.size());
        return partyId;
    }

    /**
     * 파티 생성은 party 패키지 소유다 (Member 3). 구현이 아직 붙지 않은 동안에도
     * 매칭 자체는 검증할 수 있어야 하므로 optional로 둔다. 계약상 partyId는 nullable이다.
     */
    private UUID createParty(MatchProposal proposal, GameModeConfig config, List<UUID> userIds,
                             OffsetDateTime scheduledStart) {
        PartyCreationPort port = partyCreation.getIfAvailable();
        if (port == null) {
            log.warn("PartyCreationPort 구현이 없어 파티를 만들지 않았다 proposalId={}", proposal.getId());
            return null;
        }
        return port.createParty(new PartyCreationPort.PartyCreationCommand(
                proposal.getId(), config.game(), config.modeKey(),
                config.targetPartySize(), userIds, scheduledStart));
    }

    private void expire(MatchProposal proposal, List<ProposalMember> members) {
        proposal.expire();
        breakProposal(proposal, members);
    }

    /** 제안이 깨졌다. 원본을 되돌리고 잠금을 푼다. */
    private void breakProposal(MatchProposal proposal, List<ProposalMember> members) {
        handlerFor(proposal).onBroken(
                members.stream().map(ProposalMember::getSourceRequestId).toList());
        UUID proposalId = proposal.getId();
        List<UUID> userIds = members.stream().map(ProposalMember::getUserId).toList();
        AfterCommit.run(() -> claims.releaseClaims(proposalId, userIds));
        publishSettled(proposal, userIds);
    }

    /**
     * 제안이 끝났음을 알린다. 구독자는 AFTER_COMMIT에서 받는다.
     * 대기 화면이 이 이벤트를 못 받으면 사용자는 끝난 제안을 계속 보고 있게 된다.
     */
    private void publishSettled(MatchProposal proposal, List<UUID> userIds) {
        events.publishEvent(new MatchingEvents.ProposalSettled(
                proposal.getId(), proposal.getSourceType(), proposal.getStatus(), userIds));
    }

    private ProposalParticipants handlerFor(MatchProposal proposal) {
        ProposalParticipants handler = participants.get(proposal.getSourceType());
        if (handler == null) {
            throw new IllegalStateException(
                    "제안 원본을 다룰 구현이 없다: " + proposal.getSourceType());
        }
        return handler;
    }

    private static boolean allAccepted(List<ProposalMember> members) {
        return members.stream().allMatch(member -> member.getAcceptance() == Acceptance.ACCEPTED);
    }

    /** 응답을 처리하기 전에 제안 행을 잠근다. 같은 제안에 대한 동시 응답을 직렬화한다. */
    private MatchProposal loadForUpdate(UUID proposalId) {
        return proposals.findByIdForUpdate(proposalId)
                .orElseThrow(() -> new NotFoundException("PROPOSAL_NOT_FOUND",
                        "제안을 찾을 수 없다: " + proposalId));
    }

    private MatchProposal load(UUID proposalId) {
        return proposals.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("PROPOSAL_NOT_FOUND",
                        "제안을 찾을 수 없다: " + proposalId));
    }

    private List<ProposalMember> membersOf(UUID proposalId) {
        return proposalMembers.findAllByIdProposalId(proposalId);
    }

    private static ProposalMember memberOf(List<ProposalMember> members, UUID userId, UUID proposalId) {
        return members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("PROPOSAL_NOT_FOUND",
                        "제안을 찾을 수 없다: " + proposalId));
    }

    private static void requirePending(MatchProposal proposal) {
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ConflictException("PROPOSAL_NOT_PENDING",
                    "이미 끝난 제안이다: " + proposal.getStatus());
        }
    }

    /**
     * 이미 확정된 제안을 다시 조회하는 경우다. 확정 응답에는 방금 만든 id를 그대로 쓰지만
     * 조회에는 들고 있는 것이 없어 파티 쪽에 물어본다.
     */
    private UUID partyIdOf(MatchProposal proposal) {
        if (proposal.getStatus() != ProposalStatus.CONFIRMED) {
            return null;
        }
        PartyCreationPort port = partyCreation.getIfAvailable();
        return port == null ? null : port.findPartyIdOf(proposal.getId()).orElse(null);
    }

    private static ProposalView view(MatchProposal proposal, List<ProposalMember> members,
                                     UUID partyId) {
        return new ProposalView(
                proposal.getId(), proposal.getStatus(), proposal.getExpiresAt(),
                members.stream()
                        .map(member -> new ProposalView.Member(
                                member.getUserId(), null, member.getAcceptance()))
                        .toList(),
                partyId);
    }

    /** 계약(openapi ProposalView)에 맞춘 조회 결과. */
    public record ProposalView(
            UUID id,
            ProposalStatus status,
            OffsetDateTime expiresAt,
            List<Member> members,
            UUID partyId
    ) {
        /** nickname은 화면용이라 조회 시점에 ProposalViewAssembler가 채운다. */
        public record Member(UUID userId, String nickname, Acceptance acceptance) {
        }
    }
}
