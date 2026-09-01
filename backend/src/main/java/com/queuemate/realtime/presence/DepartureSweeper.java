package com.queuemate.realtime.presence;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.party.service.PartyDepartureService;
import com.queuemate.realtime.session.ClusterPresence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 유예 시간이 지난 사용자를 파티에서 내보낸다.
 *
 * 연결이 끊길 때 바로 처리하지 않는 이유는, 끊김이 곧 이탈이 아니기 때문이다.
 * 새로고침과 짧은 네트워크 전환은 몇 초 만에 돌아온다.
 */
@Component
public class DepartureSweeper {

    private static final Logger log = LoggerFactory.getLogger(DepartureSweeper.class);

    private final DeparturePendingStore pending;
    private final ClusterPresence presence;
    private final PartyDepartureService departures;

    public DepartureSweeper(DeparturePendingStore pending, ClusterPresence presence,
                            PartyDepartureService departures) {
        this.pending = pending;
        this.presence = presence;
        this.departures = departures;
    }

    @Scheduled(fixedDelayString = "${queuemate.presence.sweep-ms}")
    public void sweep() {
        for (UUID userId : pending.pollDue()) {
            // 예약 취소가 실패했거나 목록을 꺼낸 직후에 돌아왔을 수 있다.
            // 다른 서버에 붙어 있을 수도 있어 이 프로세스만 봐서는 안 된다.
            if (presence.isOnline(userId)) {
                continue;
            }
            try {
                MDC.put(MdcKeys.USER_ID, userId.toString());
                int left = 0;
                for (UUID partyId : departures.openPartyIdsOf(userId)) {
                    // 파티마다 트랜잭션을 나눈다. 하나가 실패해도 나머지는 정리된다.
                    left += departures.leave(partyId, userId) ? 1 : 0;
                }
                if (left > 0) {
                    log.info("연결이 돌아오지 않아 파티에서 내보냈다 parties={}", left);
                }
            } catch (RuntimeException e) {
                // 한 사용자의 실패가 나머지 처리를 막지 않는다.
                log.error("이탈 처리 실패 userId={}", userId, e);
            } finally {
                MDC.remove(MdcKeys.USER_ID);
            }
        }
    }
}
