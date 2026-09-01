package com.queuemate.party.service;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.common.logging.MdcKeys;
import com.queuemate.common.party.PartyCreationConflictException;
import com.queuemate.common.party.PartyCreationPort;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.party.domain.Party;
import com.queuemate.party.domain.PartyMember;
import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.repository.ConfirmedProposalReader;
import com.queuemate.party.repository.ConfirmedProposalReader.ProposalSnapshot;
import com.queuemate.party.repository.PartyMemberRepository;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PartyService implements PartyCreationPort {

    private static final Logger log = LoggerFactory.getLogger(PartyService.class);

    private final PartyRepository parties;
    private final PartyMemberRepository partyMembers;
    private final ConfirmedProposalReader proposals;
    private final BlockLookupPort blockLookup;
    private final RealtimeEventPublisher events;

    public PartyService(PartyRepository parties, PartyMemberRepository partyMembers,
                        ConfirmedProposalReader proposals, BlockLookupPort blockLookup,
                        RealtimeEventPublisher events) {
        this.parties = parties;
        this.partyMembers = partyMembers;
        this.proposals = proposals;
        this.blockLookup = blockLookup;
        this.events = events;
    }

    /** 호출자의 확정 트랜잭션에 그대로 참여한다. 따로 트랜잭션을 열지 않는다. */
    @Override
    @Transactional
    public UUID createFromProposal(UUID proposalId, String game, String modeKey,
                                   int targetSize, OffsetDateTime scheduledStart) {
        // 같은 트랜잭션에서 재시도하거나 이미 만들어진 경우를 먼저 흡수한다.
        // 동시 생성까지 막지는 못한다. 그건 아래 unique 제약이 맡는다.
        var existing = parties.findByProposalId(proposalId);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        ProposalSnapshot snapshot = proposals.read(proposalId)
                .orElseThrow(() -> new NotFoundException("PROPOSAL_NOT_FOUND", "proposal을 찾을 수 없다"));
        if (!snapshot.confirmed()) {
            // INV-5: expired/declined/cancelled proposal은 party가 될 수 없다.
            throw new ConflictException("PROPOSAL_NOT_CONFIRMED", "확정되지 않은 proposal이다");
        }
        if (!snapshot.acceptedByEveryone()) {
            // INV-4: 호출자가 확정이라 해도 수락 기록이 부족하면 만들지 않는다.
            throw new ConflictException("PROPOSAL_NOT_FULLY_ACCEPTED", "전원이 수락하지 않았다");
        }

        List<UUID> members = snapshot.acceptedIds();
        if (members.size() != targetSize) {
            // INV-3: 정원은 mode config가 정한다. 넘치는 것도 모자란 것도 파티가 아니다.
            throw new ConflictException("PARTY_SIZE_MISMATCH",
                    "정원과 참가자 수가 다르다 target=" + targetSize + " accepted=" + members.size());
        }
        if (blockLookup.anyBlockBetween(members)) {
            // INV-6: proposal 생성 이후 차단이 생겼을 수 있어 캐시 없이 다시 본다.
            throw new ConflictException("BLOCKED_MEMBERS", "차단 관계인 참가자가 있다");
        }

        Party party = Party.of(proposalId, game, modeKey, targetSize, scheduledStart);
        try {
            parties.saveAndFlush(party);
            partyMembers.saveAllAndFlush(
                    members.stream().map(userId -> PartyMember.of(party.getId(), userId)).toList());
        } catch (DataIntegrityViolationException e) {
            // parties_proposal_unique. 다른 트랜잭션이 먼저 만들었다는 뜻이다.
            // 여기서 기존 party를 읽어 돌려주지 않는다. 이 트랜잭션은 이미 실패했고
            // 호출자의 proposal 확정까지 함께 롤백되어야 한다.
            log.warn("동시 party 생성 충돌 proposalId={}", proposalId);
            throw new PartyCreationConflictException("이미 이 proposal로 party가 만들어졌다");
        }

        MDC.put(MdcKeys.PARTY_ID, party.getId().toString());
        log.info("party 생성 proposalId={} game={} mode={} size={}",
                proposalId, game, modeKey, targetSize);
        return party.getId();
    }

    @Transactional(readOnly = true)
    public PartyDetail detail(UUID partyId, UUID viewerId) {
        Party party = parties.findById(partyId).orElseThrow(this::partyNotFound);
        List<PartyMember> members = partyMembers.findByIdPartyIdOrderByJoinedAtAsc(partyId);
        requireMember(members, viewerId);
        return new PartyDetail(party, members);
    }

    /**
     * 준비 상태를 바꾸고 파티 상태를 다시 계산한다.
     * party row를 먼저 잠근 뒤 멤버를 읽는다. 순서가 바뀌면 잠금이 의미를 잃는다.
     */
    @Transactional
    public PartyDetail changeReady(UUID partyId, UUID userId, boolean ready) {
        Party party = parties.findByIdForUpdate(partyId).orElseThrow(this::partyNotFound);
        List<PartyMember> members = partyMembers.findByIdPartyIdOrderByJoinedAtAsc(partyId);
        PartyMember me = requireMember(members, userId);

        if (party.getStatus() == PartyStatus.CLOSED) {
            throw new ConflictException("PARTY_CLOSED", "종료된 파티다");
        }

        me.changeReady(ready);
        boolean allReady = members.stream()
                .filter(PartyMember::countsForReadiness)
                .allMatch(PartyMember::isReady);
        party.refreshReadiness(allReady);

        MDC.put(MdcKeys.PARTY_ID, partyId.toString());
        log.info("파티 준비 변경 ready={} allReady={} status={}", ready, allReady, party.getStatus());

        // 커밋 후에 알린다. 이벤트를 받은 클라이언트가 곧바로 조회했을 때
        // 아직 반영되지 않은 상태를 읽으면 안 된다.
        events.publishAfterCommit(
                members.stream().map(PartyMember::getUserId).toList(),
                ServerEvent.of(EventType.PARTY_READY_CHANGED, Map.of(
                        "partyId", partyId,
                        "userId", userId,
                        "ready", ready,
                        "status", party.getStatus().name())));
        return new PartyDetail(party, members);
    }

    /**
     * 두 사람이 모두 이 파티에 남아 있는가. WebRTC signaling relay가 쓴다.
     * 파티 상태(OPEN/READY/PLAYING)는 보지 않는다. 준비 중에도 통화는 가능해야 한다.
     */
    @Transactional(readOnly = true)
    public boolean bothActiveMembers(UUID partyId, UUID one, UUID other) {
        if (one.equals(other)) {
            return false;
        }
        return partyMembers.countActiveMembers(partyId, Set.of(one, other)) == 2;
    }

    /** 멤버가 아니면 파티의 존재 자체를 알리지 않는다. */
    private PartyMember requireMember(List<PartyMember> members, UUID userId) {
        return members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(this::partyNotFound);
    }

    private NotFoundException partyNotFound() {
        return new NotFoundException("PARTY_NOT_FOUND", "파티를 찾을 수 없다");
    }

    public record PartyDetail(Party party, List<PartyMember> members) {
    }
}
