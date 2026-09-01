package com.queuemate.realtime.presence;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.common.metrics.QueueMateMetrics;
import com.queuemate.party.service.PartyDepartureService;
import com.queuemate.realtime.session.ClusterPresence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 파티 멤버와 접속 여부를 대조해서 빠진 이탈 예약을 채운다.
 *
 * 정상 경로는 연결이 끊길 때 예약을 잡는다. 그런데 프로세스가 강제로 죽으면 close 콜백이
 * 돌지 않아 예약 자체가 안 걸린다. 접속 여부는 노드 생존 키로 정확히 오프라인이 되지만,
 * 대기 목록에 없으니 아무도 그 사용자를 꺼내 보지 않는다.
 * 떠난 사람이 파티에 영원히 남고, 남은 사람은 대기열로 돌아가지 못한다.
 *
 * 왜 오프라인인지는 따지지 않는다. 서버가 죽었든, Redis가 잠깐 끊겨 예약이 실패했든,
 * 결과가 같으므로 같은 방법으로 고친다. 죽은 노드를 찾아 그 노드의 사용자를 꺼내는 방법도
 * 있지만, 그러려면 노드별 사용자 목록을 새로 유지해야 한다. 지금 고치려는 것이
 * 장부가 실패한 경우인데 또 다른 장부에 기대면 같은 방식으로 샌다.
 *
 * 여기서 무언가 잡혔다면 정상 경로가 실패했다는 뜻이라 조용히 넘기지 않는다.
 */
@Component
public class PresenceReconciler {

    private static final Logger log = LoggerFactory.getLogger(PresenceReconciler.class);

    /**
     * 한 주기에 볼 인원의 상한.
     *
     * 오래된 파티부터 보므로 잘리더라도 방치된 쪽이 먼저 잡힌다. 다만 이 값에 계속 닿는다면
     * 전수 대조가 감당되지 않는 규모라는 뜻이고, 그때는 값을 올릴 것이 아니라
     * 죽은 노드를 신호로 삼는 방식으로 바꿔야 한다.
     */
    private static final int BATCH = 5_000;

    private final PartyDepartureService departures;
    private final ClusterPresence presence;
    private final DeparturePendingStore pending;
    private final DepartureGracePolicy grace;
    private final QueueMateMetrics metrics;

    public PresenceReconciler(PartyDepartureService departures, ClusterPresence presence,
                              DeparturePendingStore pending, DepartureGracePolicy grace,
                              QueueMateMetrics metrics) {
        this.departures = departures;
        this.presence = presence;
        this.pending = pending;
        this.grace = grace;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${queuemate.presence.reconcile-ms}")
    public void reconcile() {
        List<UUID> members = departures.activeMembersOfOpenParties(BATCH);
        if (members.isEmpty()) {
            return;
        }
        if (members.size() == BATCH) {
            log.warn("대조 대상이 상한에 닿았다. 이번 주기는 일부만 본다 limit={}", BATCH);
        }

        Set<UUID> offline = presence.offlineAmong(members);
        int scheduled = 0;
        for (UUID userId : offline) {
            // 이미 예약이 있으면 건드리지 않는다. 덮어쓰면 만료 시각이 매 주기 뒤로 밀려
            // 정말 떠난 사용자가 영원히 정리되지 않는다.
            if (pending.scheduleIfAbsent(userId, grace.graceFor(userId))) {
                scheduled++;
                metrics.reconcileFound();
                MDC.put(MdcKeys.USER_ID, userId.toString());
                log.warn("예약이 빠진 이탈을 대조로 찾았다. 연결이 비정상 종료됐을 수 있다");
                MDC.remove(MdcKeys.USER_ID);
            }
        }
        if (scheduled > 0) {
            log.warn("대조로 채운 이탈 예약 users={} 대상={}", scheduled, members.size());
        }
    }
}
