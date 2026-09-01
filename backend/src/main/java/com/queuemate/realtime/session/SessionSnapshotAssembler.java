package com.queuemate.realtime.session;

import com.queuemate.party.api.PartyViewAssembler;
import com.queuemate.party.service.PartyDepartureService;
import com.queuemate.party.service.PartyService;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 연결 직후 보낼 현재 상태를 모은다.
 *
 * 끊긴 동안의 이벤트를 보관했다가 재전송하는 대신 지금 상태를 다시 알린다.
 * 무엇이 바뀌었는지는 알려주지 못하지만, 보관 기간과 용량을 정할 필요가 없고
 * 몇 번을 보내도 결과가 같다.
 *
 * 지금은 party만 채운다. matching과 reservation은 각자 키를 추가하면 된다.
 */
@Component
public class SessionSnapshotAssembler {

    private static final Logger log = LoggerFactory.getLogger(SessionSnapshotAssembler.class);

    private final PartyService parties;
    private final PartyDepartureService departures;
    private final PartyViewAssembler partyViews;

    public SessionSnapshotAssembler(PartyService parties, PartyDepartureService departures,
                                    PartyViewAssembler partyViews) {
        this.parties = parties;
        this.departures = departures;
        this.partyViews = partyViews;
    }

    public ServerEvent snapshotOf(UUID userId) {
        List<Object> partyViews = new ArrayList<>();
        for (UUID partyId : departures.openPartyIdsOf(userId)) {
            try {
                partyViews.add(this.partyViews.toView(parties.detail(partyId, userId)));
            } catch (RuntimeException e) {
                // 한 파티를 못 읽어도 나머지는 보낸다. 스냅샷이 통째로 빠지면
                // 클라이언트는 파티가 없는 줄 안다.
                log.warn("스냅샷에서 파티 하나를 건너뛴다 partyId={}", partyId, e);
            }
        }
        return ServerEvent.of(EventType.SESSION_SNAPSHOT, Map.of("parties", partyViews));
    }
}
