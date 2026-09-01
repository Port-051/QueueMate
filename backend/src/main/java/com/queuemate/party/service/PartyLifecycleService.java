package com.queuemate.party.service;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.party.domain.Party;
import com.queuemate.party.domain.PartyMember;
import com.queuemate.party.repository.PartyMemberRepository;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 사용자 행동 없이 시간만으로 일어나는 파티 상태 전이.
 *
 * 준비와 이탈은 누가 무엇을 눌러서 일어나지만, 게임 시작과 방치된 파티 정리는
 * 아무도 누르지 않는다. 서버가 시간을 보고 판단해야 한다.
 *
 * 반복문은 여기 두지 않는다. 같은 객체의 메서드를 직접 부르면 스프링 프록시를 거치지
 * 않아 @Transactional이 걸리지 않는다. 파티마다 트랜잭션을 나누려면 호출하는 쪽이
 * 프록시를 통해 불러야 한다.
 */
@Service
public class PartyLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PartyLifecycleService.class);

    /** 한 주기에 처리할 상한. 밀린 파티가 많아도 한 번의 sweep이 오래 걸리지 않게 한다. */
    private static final Limit BATCH = Limit.of(200);

    private final PartyRepository parties;
    private final PartyMemberRepository partyMembers;
    private final RealtimeEventPublisher events;

    public PartyLifecycleService(PartyRepository parties, PartyMemberRepository partyMembers,
                                 RealtimeEventPublisher events) {
        this.parties = parties;
        this.partyMembers = partyMembers;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<UUID> readySince(OffsetDateTime cutoff) {
        return parties.findReadyIdsBefore(cutoff, BATCH);
    }

    @Transactional(readOnly = true)
    public List<UUID> playingSince(OffsetDateTime cutoff) {
        return parties.findPlayingIdsBefore(cutoff, BATCH);
    }

    /**
     * 게임 시작으로 넘긴다.
     *
     * 후보 목록은 잠금 밖에서 읽었다. 그 사이 누가 준비를 풀었거나 나갔을 수 있으므로
     * 잠근 뒤에 조건을 다시 본다. cutoff를 인자로 받는 이유가 이것이다.
     * 목록을 읽을 때 통과했다는 사실은 여기서 아무것도 보장하지 않는다.
     */
    @Transactional
    public boolean startPlaying(UUID partyId, OffsetDateTime cutoff, OffsetDateTime now) {
        Party party = parties.findByIdForUpdate(partyId).orElse(null);
        if (party == null || party.getReadyAt() == null || party.getReadyAt().isAfter(cutoff)) {
            return false;
        }
        if (!party.startPlaying(now)) {
            return false;
        }

        List<UUID> members = activeMemberIds(partyId);
        MDC.put(MdcKeys.PARTY_ID, partyId.toString());
        MDC.put(MdcKeys.STATE_FROM, "READY");
        MDC.put(MdcKeys.STATE_TO, "PLAYING");
        log.info("게임 시작으로 판정 readyAt={} 인원={}", party.getReadyAt(), members.size());
        MDC.remove(MdcKeys.STATE_FROM);
        MDC.remove(MdcKeys.STATE_TO);

        events.publishAfterCommit(members, ServerEvent.of(EventType.PARTY_PLAYING, Map.of(
                "partyId", partyId,
                "status", party.getStatus().name())));
        return true;
    }

    /**
     * 게임 중으로 너무 오래 남아 있던 파티를 닫는다.
     *
     * 대기열 복귀는 하지 않는다. 이 파티는 게임에 들어간 적이 있고, 아무도 나가기를
     * 누르지 않은 채 방치된 상태다. 그런 사용자를 다시 매칭에 밀어 넣지 않는다.
     */
    @Transactional
    public boolean closeAbandoned(UUID partyId, OffsetDateTime cutoff, OffsetDateTime now) {
        Party party = parties.findByIdForUpdate(partyId).orElse(null);
        if (party == null || party.getPlayedAt() == null || party.getPlayedAt().isAfter(cutoff)) {
            return false;
        }
        if (!party.close(now)) {
            return false;
        }

        List<UUID> members = activeMemberIds(partyId);
        MDC.put(MdcKeys.PARTY_ID, partyId.toString());
        log.info("방치된 파티 종료 playedAt={} 인원={}", party.getPlayedAt(), members.size());

        events.publishAfterCommit(members, ServerEvent.of(EventType.PARTY_CLOSED, Map.of(
                "partyId", partyId,
                "reason", "PLAY_TIMEOUT")));
        return true;
    }

    private List<UUID> activeMemberIds(UUID partyId) {
        return partyMembers.findByIdPartyIdOrderByJoinedAtAsc(partyId).stream()
                .filter(PartyMember::countsForReadiness)
                .map(PartyMember::getUserId)
                .toList();
    }
}
