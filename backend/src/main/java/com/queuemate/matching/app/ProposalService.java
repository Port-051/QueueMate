package com.queuemate.matching.app;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.Acceptance;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchProposal;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.PartyCreationPort;
import com.queuemate.matching.domain.ProposalMember;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import com.queuemate.matching.infra.ProposalClaimRepository;
import com.queuemate.matching.infra.ProposalMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 제안 수명주기 (docs/03 §8).
 *
 * <p>INV-4: 전원이 수락하기 전에는 파티를 만들지 않는다.
 * INV-5: 끝난 제안은 되살아나지 않는다. 만료와 수락이 겹치면 먼저 도달한 종결이 이긴다.
 */
@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

    private final MatchProposalRepository proposals;
    private final ProposalMemberRepository proposalMembers;
    private final MatchRequestRepository requests;
    private final MatchQueueRepository queue;
    private final ProposalClaimRepository claims;
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final ObjectProvider<PartyCreationPort> partyCreation;
    private final Clock clock;

    @Autowired
    public ProposalService(MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                           MatchRequestRepository requests, MatchQueueRepository queue,
                           ProposalClaimRepository claims, GameModeConfigProvider modes,
                           MatchConditionCodec codec, ObjectProvider<PartyCreationPort> partyCreation) {
        this(proposals, proposalMembers, requests, queue, claims, modes, codec, partyCreation,
                Clock.systemUTC());
    }

    ProposalService(MatchProposalRepository proposals, ProposalMemberRepository proposalMembers,
                    MatchRequestRepository requests, MatchQueueRepository queue,
                    ProposalClaimRepository claims, GameModeConfigProvider modes,
                    MatchConditionCodec codec, ObjectProvider<PartyCreationPort> partyCreation,
                    Clock clock) {
        this.proposals = proposals;
        this.proposalMembers = proposalMembers;
        this.requests = requests;
        this.queue = queue;
        this.claims = claims;
        this.modes = modes;
        this.codec = codec;
        this.partyCreation = partyCreation;
        this.clock = clock;
    }

    /** 참가자 한 명이 수락한다. 전원이 수락하면 그 자리에서 확정한다. */
    @Transactional
    public ProposalView accept(UUID userId, UUID proposalId) {
        MatchProposal proposal = load(proposalId);
        List<ProposalMember> members = membersOf(proposalId);
        ProposalMember me = memberOf(members, userId, proposalId);

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (proposal.getStatus() == ProposalStatus.PENDING && proposal.isExpiredAt(now)) {
            // 만료된 제안을 수락으로 되살리지 않는다 (INV-5).
            expire(proposal, members);
            throw new ConflictException("PROPOSAL_EXPIRED", "제안이 만료됐다");
        }
        requirePending(proposal);

        me.respond(Acceptance.ACCEPTED);
        if (allAccepted(members)) {
            confirm(proposal, members, now);
        }
        return view(proposal, members);
    }

    /** 참가자 한 명이 거절하면 제안 전체가 끝난다. 나머지는 조건을 유지한 채 대기열로 돌아간다. */
    @Transactional
    public void decline(UUID userId, UUID proposalId) {
        MatchProposal proposal = load(proposalId);
        List<ProposalMember> members = membersOf(proposalId);
        ProposalMember me = memberOf(members, userId, proposalId);
        requirePending(proposal);

        me.respond(Acceptance.DECLINED);
        proposal.decline();
        returnEveryoneToQueue(proposal, members);
        log.info("제안 거절 proposalId={} by={}", proposalId, userId);
    }

    @Transactional(readOnly = true)
    public ProposalView get(UUID userId, UUID proposalId) {
        MatchProposal proposal = load(proposalId);
        List<ProposalMember> members = membersOf(proposalId);
        memberOf(members, userId, proposalId); // 참가자가 아니면 존재를 알리지 않는다
        return view(proposal, members);
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
        for (MatchProposal proposal : overdue) {
            expire(proposal, membersOf(proposal.getId()));
        }
        if (!overdue.isEmpty()) {
            log.info("만료된 제안 정리 count={}", overdue.size());
        }
        return overdue.size();
    }

    private void confirm(MatchProposal proposal, List<ProposalMember> members, OffsetDateTime now) {
        proposal.confirm(now);

        Map<UUID, MatchRequest> byRequestId = requestsOf(members);
        List<UUID> userIds = new ArrayList<>(members.size());
        MatchCondition sample = null;
        for (ProposalMember member : members) {
            MatchRequest request = byRequestId.get(member.getSourceRequestId());
            userIds.add(member.getUserId());
            if (request == null) {
                continue;
            }
            request.markMatched();
            if (sample == null) {
                sample = codec.fromJson(request.getConditionJson());
            }
        }
        if (sample == null) {
            throw new IllegalStateException("확정할 제안의 요청을 찾을 수 없다: " + proposal.getId());
        }

        MatchCondition condition = sample;
        GameModeConfig config = modes.findActive(condition.game(), condition.modeKey())
                .orElseThrow(() -> new IllegalStateException(
                        "확정 시점에 모드 설정이 사라졌다: " + condition.game() + "/" + condition.modeKey()));

        createParty(proposal, config, userIds);
        releaseGuards(proposal, members, byRequestId, condition);
        log.info("제안 확정 proposalId={} size={}", proposal.getId(), members.size());
    }

    /**
     * 파티 생성은 party 패키지 소유다 (Member 3). 구현이 아직 붙지 않은 동안에도
     * 매칭 자체는 검증할 수 있어야 하므로 optional로 둔다. 계약상 partyId는 nullable이다.
     */
    private void createParty(MatchProposal proposal, GameModeConfig config, List<UUID> userIds) {
        PartyCreationPort port = partyCreation.getIfAvailable();
        if (port == null) {
            log.warn("PartyCreationPort 구현이 없어 파티를 만들지 않았다 proposalId={}", proposal.getId());
            return;
        }
        port.createParty(new PartyCreationPort.PartyCreationCommand(
                proposal.getId(), config.game(), config.modeKey(),
                config.targetPartySize(), userIds, null));
    }

    /** 확정된 참가자는 대기열에서 완전히 빠진다. */
    private void releaseGuards(MatchProposal proposal, List<ProposalMember> members,
                               Map<UUID, MatchRequest> byRequestId, MatchCondition sample) {
        String queueKey = MatchingRedisKeys.queue(sample.game(), sample.modeKey());
        for (ProposalMember member : members) {
            MatchRequest request = byRequestId.get(member.getSourceRequestId());
            if (request != null) {
                queue.release(member.getUserId(), request.getId(), queueKey);
            }
        }
        claims.releaseClaims(proposal.getId(),
                members.stream().map(ProposalMember::getUserId).toList());
    }

    private void expire(MatchProposal proposal, List<ProposalMember> members) {
        proposal.expire();
        returnEveryoneToQueue(proposal, members);
    }

    /**
     * 제안이 깨졌다. 아직 매칭을 원하는 참가자는 최초 대기 시각을 유지한 채 대기열로 돌아간다
     * (docs/03 §8). 매칭을 그만두려면 요청 자체를 취소해야 한다.
     */
    private void returnEveryoneToQueue(MatchProposal proposal, List<ProposalMember> members) {
        Map<UUID, MatchRequest> byRequestId = requestsOf(members);
        for (ProposalMember member : members) {
            MatchRequest request = byRequestId.get(member.getSourceRequestId());
            if (request == null || request.getStatus() != MatchRequestStatus.PROPOSED) {
                continue;
            }
            request.returnToQueue();
            MatchCondition condition = codec.fromJson(request.getConditionJson());
            queue.requeue(MatchingRedisKeys.queue(condition.game(), condition.modeKey()),
                    request.getId(), request.getQueuedAt().toInstant());
        }
        claims.releaseClaims(proposal.getId(),
                members.stream().map(ProposalMember::getUserId).toList());
    }

    private Map<UUID, MatchRequest> requestsOf(List<ProposalMember> members) {
        List<UUID> requestIds = members.stream().map(ProposalMember::getSourceRequestId).toList();
        return requests.findAllById(requestIds).stream()
                .collect(Collectors.toMap(MatchRequest::getId, Function.identity()));
    }

    private static boolean allAccepted(List<ProposalMember> members) {
        return members.stream().allMatch(member -> member.getAcceptance() == Acceptance.ACCEPTED);
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

    private Optional<UUID> partyIdOf(MatchProposal proposal) {
        // 파티 조회는 party 패키지 소유다. 매칭은 확정 사실만 안다.
        return Optional.empty();
    }

    private ProposalView view(MatchProposal proposal, List<ProposalMember> members) {
        return new ProposalView(
                proposal.getId(), proposal.getStatus(), proposal.getExpiresAt(),
                members.stream()
                        .map(member -> new ProposalView.Member(member.getUserId(), member.getAcceptance()))
                        .toList(),
                partyIdOf(proposal).orElse(null));
    }

    /** 계약(openapi ProposalView)에 맞춘 조회 결과. */
    public record ProposalView(
            UUID id,
            ProposalStatus status,
            OffsetDateTime expiresAt,
            List<Member> members,
            UUID partyId
    ) {
        public record Member(UUID userId, Acceptance acceptance) {
        }
    }
}
