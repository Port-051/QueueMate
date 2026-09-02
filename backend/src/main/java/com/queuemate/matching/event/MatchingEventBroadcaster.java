package com.queuemate.matching.event;

import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.domain.MatchingEvents;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 매칭이 발행한 사건을 WebSocket 이벤트로 옮긴다.
 *
 * <p>matching은 무슨 일이 일어났는지만 발행하고 전달은 realtime 소유다
 * ({@link MatchingEvents} 주석). 그 둘을 잇는 것이 이 클래스다. 발행하는 쪽과
 * 내보내는 쪽이 각각 자기 패키지 안에서 완결되어 있었고 사이가 비어 있었다.
 * 제안은 만들어지는데 아무에게도 닿지 않아, 사용자는 대기 화면에 머문 채
 * 20초마다 제안이 생겼다 만료되기를 반복했다.
 *
 * <p>AFTER_COMMIT에서 받는다. 커밋 전에 알리면 클라이언트가 곧바로 조회했을 때
 * 아직 없는 제안을 읽는다. 롤백되면 존재한 적 없는 제안을 수락하려 든다.
 *
 * <p>payload는 REST의 ProposalView와 같은 모양이다. 클라이언트가 이벤트로 받은 것과
 * 조회로 받은 것을 다르게 다뤄야 하면 화면마다 분기가 생긴다.
 */
@Component
public class MatchingEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MatchingEventBroadcaster.class);

    private final ProposalService proposals;
    private final RealtimeEventPublisher realtime;

    public MatchingEventBroadcaster(ProposalService proposals, RealtimeEventPublisher realtime) {
        this.proposals = proposals;
        this.realtime = realtime;
    }

    /**
     * 제안이 만들어졌다. 참가자 전원에게 동시에 보낸다 (docs/03 §8).
     *
     * <p>이 이벤트가 늦으면 사용자는 TTL을 그냥 흘려보낸다. 그래서 전달 실패를
     * 조용히 넘기지 않고 남긴다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProposalCreated(MatchingEvents.ProposalCreated event) {
        EventType type = event.sourceType() == ProposalSourceType.RESERVATION
                ? EventType.RESERVATION_PROPOSAL_CREATED
                : EventType.MATCH_PROPOSAL_CREATED;

        for (UUID userId : event.userIds()) {
            // 참가자마다 따로 만든다. 조회가 참가자 본인에게만 제안을 보여 주기 때문이다.
            ProposalService.ProposalView view = proposals.get(userId, event.proposalId());
            realtime.publish(List.of(userId),
                    ServerEvent.of(type, Map.of("proposal", view)));
        }
        log.debug("제안 이벤트 전달 proposalId={} type={} 수신자={}",
                event.proposalId(), type, event.userIds().size());
    }

    /**
     * 제안이 끝났다. 확정/거절/만료/취소가 한 사건으로 들어와 이벤트 이름만 갈린다.
     *
     * <p>이걸 못 받으면 사용자는 끝난 제안을 계속 보고 있게 된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProposalSettled(MatchingEvents.ProposalSettled event) {
        EventType type = settledTypeOf(event.status());
        if (type == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("proposalId", event.proposalId());
        if (type == EventType.MATCH_CONFIRMED) {
            // 클라이언트가 이 값으로 파티룸에 들어간다. 없으면 갈 곳을 모른다.
            payload.put("partyId", partyIdOf(event));
        }
        realtime.publish(event.userIds(), ServerEvent.of(type, payload));
        log.debug("제안 종료 이벤트 전달 proposalId={} type={}", event.proposalId(), type);
    }

    private EventType settledTypeOf(ProposalStatus status) {
        return switch (status) {
            case CONFIRMED -> EventType.MATCH_CONFIRMED;
            case EXPIRED -> EventType.MATCH_PROPOSAL_EXPIRED;
            case DECLINED, CANCELLED -> EventType.MATCH_CANCELLED;
            // 끝나지 않은 제안은 종료 이벤트의 대상이 아니다.
            case PENDING -> null;
        };
    }

    /**
     * 확정된 제안에서 파티 id를 얻는다.
     *
     * <p>참가자 아무나로 조회해도 같은 값이다. 조회가 참가자 본인만 허용하므로
     * 명단의 첫 사람을 쓴다.
     */
    private Object partyIdOf(MatchingEvents.ProposalSettled event) {
        UUID anyMember = event.userIds().get(0);
        return proposals.get(anyMember, event.proposalId()).partyId();
    }
}
