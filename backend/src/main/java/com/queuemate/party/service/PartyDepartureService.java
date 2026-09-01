package com.queuemate.party.service;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.common.matching.MatchRequeuePort;
import com.queuemate.party.domain.Party;
import com.queuemate.party.domain.PartyMember;
import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.repository.PartyMemberRepository;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 파티에서 사람이 빠졌을 때의 뒷정리.
 *
 * 나간 사실을 기록하는 것으로 끝나지 않는다. 남은 사람들의 파티 상태를 다시 계산해야 하고,
 * 혼자 남으면 파티를 닫아야 한다.
 */
@Service
public class PartyDepartureService {

    private static final Logger log = LoggerFactory.getLogger(PartyDepartureService.class);

    private final PartyRepository parties;
    private final PartyMemberRepository partyMembers;
    private final RealtimeEventPublisher events;
    /**
     * matching 모듈이 아직 구현하지 않았을 수 있다. 없으면 복귀 요청을 건너뛴다.
     * 필수 의존으로 두면 그쪽 작업 전에는 애플리케이션이 아예 뜨지 않는다.
     */
    private final ObjectProvider<MatchRequeuePort> requeue;

    public PartyDepartureService(PartyRepository parties, PartyMemberRepository partyMembers,
                                 RealtimeEventPublisher events,
                                 ObjectProvider<MatchRequeuePort> requeue) {
        this.parties = parties;
        this.partyMembers = partyMembers;
        this.events = events;
        this.requeue = requeue;
    }

    /**
     * 이 사용자가 속한, 끝나지 않은 파티들.
     *
     * 반복문을 여기 두지 않는다. 같은 객체의 leave를 직접 부르면 스프링 프록시를 거치지
     * 않아 @Transactional이 걸리지 않는다. 파티마다 트랜잭션을 나누려면 호출하는 쪽이
     * 프록시를 통해 leave를 불러야 한다.
     */
    @Transactional(readOnly = true)
    public List<UUID> openPartyIdsOf(UUID userId) {
        return partyMembers.findOpenPartyIdsOf(userId);
    }

    /** 끝나지 않은 파티들의 상태. 이탈 유예를 얼마나 줄지 정하는 데 쓴다. */
    @Transactional(readOnly = true)
    public List<PartyStatus> openPartyStatusesOf(UUID userId) {
        return partyMembers.findOpenPartyStatusesOf(userId);
    }

    /** 끝나지 않은 파티에 남아 있는 사람들. 접속 여부와 대조할 대상이다. */
    @Transactional(readOnly = true)
    public List<UUID> activeMembersOfOpenParties(int limit) {
        return partyMembers.findActiveMembersOfOpenParties(Limit.of(limit));
    }

    @Transactional
    public boolean leave(UUID partyId, UUID userId) {
        // 준비 상태 변경과 같은 순서로 잠근다. 순서가 갈리면 데드락이 난다.
        Party party = parties.findByIdForUpdate(partyId).orElse(null);
        if (party == null) {
            return false;
        }
        List<PartyMember> members = partyMembers.findByIdPartyIdOrderByJoinedAtAsc(partyId);
        PartyMember leaving = members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        if (leaving == null || !leaving.markLeft(OffsetDateTime.now())) {
            return false;
        }

        List<UUID> remaining = members.stream()
                .filter(PartyMember::countsForReadiness)
                .map(PartyMember::getUserId)
                .toList();

        MDC.put(MdcKeys.PARTY_ID, partyId.toString());
        MDC.put(MdcKeys.STATE_FROM, party.getStatus().name());

        if (remaining.size() < 2) {
            // 정원은 최소 2인이다. 혼자 남은 파티는 파티가 아니므로 닫는다.
            // 남은 사람이 계속 빈 파티룸을 보고 있게 두지 않는다.
            boolean played = party.hasPlayed();
            party.close(OffsetDateTime.now());
            MDC.put(MdcKeys.STATE_TO, party.getStatus().name());
            log.info("파티 종료 남은인원={}", remaining.size());
            // 나간 사람에게도 알린다. 잠깐 끊겼다 돌아왔을 때 파티가 없어진 이유를 알아야 한다.
            List<UUID> everyone = members.stream().map(PartyMember::getUserId).toList();
            events.publishAfterCommit(everyone,
                    ServerEvent.of(EventType.PARTY_CLOSED, Map.of(
                            "partyId", partyId,
                            "reason", "MEMBER_LEFT")));
            if (played) {
                // 게임이 끝나고 다들 자리를 뜨면서 닫힌 파티다. 대기열 복귀는 파티가
                // 게임 전에 깨졌을 때의 구제책이지, 한 판 끝낸 사람을 다시 매칭에
                // 밀어 넣으라는 뜻이 아니다. 다음 판은 본인이 결정한다.
                log.info("게임을 마친 파티라 대기열로 되돌리지 않는다 users={}", remaining.size());
            } else {
                requeueAfterCommit(remaining, partyId);
            }
        } else {
            // 노트 003에 예정된 버그로 적어둔 지점이다. 준비 안 한 사람이 나가면
            // 남은 전원이 준비 상태가 되므로 여기서 다시 계산해야 한다.
            boolean allReady = members.stream()
                    .filter(PartyMember::countsForReadiness)
                    .allMatch(PartyMember::isReady);
            party.refreshReadiness(allReady, OffsetDateTime.now());
            MDC.put(MdcKeys.STATE_TO, party.getStatus().name());
            log.info("파티원 이탈 남은인원={} allReady={}", remaining.size(), allReady);
            events.publishAfterCommit(remaining,
                    ServerEvent.of(EventType.PARTY_MEMBER_LEFT, Map.of(
                            "partyId", partyId,
                            "userId", userId,
                            "status", party.getStatus().name())));
        }
        MDC.remove(MdcKeys.STATE_FROM);
        MDC.remove(MdcKeys.STATE_TO);
        return true;
    }

    /**
     * 커밋 후에 요청한다. 같은 트랜잭션에 넣으면 복귀가 실패했을 때 파티 종료까지 롤백된다.
     * 파티가 닫힌 것은 되돌릴 일이 아니고, 복귀 실패는 사용자가 홈에서 다시 누르면 된다.
     */
    private void requeueAfterCommit(List<UUID> userIds, UUID partyId) {
        if (userIds.isEmpty()) {
            return;
        }
        MatchRequeuePort port = requeue.getIfAvailable();
        if (port == null) {
            log.info("대기열 복귀 포트 미구현. 건너뛴다 users={}", userIds.size());
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            port.requeueAfterPartyClosed(userIds, partyId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    port.requeueAfterPartyClosed(userIds, partyId);
                } catch (RuntimeException e) {
                    // 복귀 실패가 파티 종료를 되돌리지 않는다.
                    log.error("대기열 복귀 실패 partyId={}", partyId, e);
                }
            }
        });
    }
}
