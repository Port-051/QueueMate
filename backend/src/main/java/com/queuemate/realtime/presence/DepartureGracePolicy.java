package com.queuemate.realtime.presence;

import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.service.PartyDepartureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 연결이 끊긴 사용자를 얼마나 기다려 줄지 정한다.
 *
 * 파티 상태를 봐야 하므로 DB를 한 번 읽는다. 끊김은 세션당 한 번뿐이라 이 비용은
 * 감당할 수 있다. signaling 경로처럼 초당 수십 번 도는 자리가 아니다.
 *
 * 여러 파티에 걸쳐 있으면 가장 긴 유예를 준다. 짧은 쪽을 따르면 게임 중인 파티에서
 * 먼저 내보내게 된다. 되돌릴 수 없는 쪽을 기준으로 잡는다.
 */
@Component
public class DepartureGracePolicy {

    private static final Logger log = LoggerFactory.getLogger(DepartureGracePolicy.class);

    private final PartyDepartureService departures;
    private final PresenceProperties presence;

    public DepartureGracePolicy(PartyDepartureService departures, PresenceProperties presence) {
        this.departures = departures;
        this.presence = presence;
    }

    public Duration graceFor(UUID userId) {
        List<PartyStatus> statuses;
        try {
            statuses = departures.openPartyStatusesOf(userId);
        } catch (RuntimeException e) {
            // 조회가 실패했다고 이탈 예약 자체를 건너뛰면 파티가 영영 안 닫힌다.
            // 가장 긴 유예로 물러선다. 오래 기다리는 쪽이 잘못 내보내는 쪽보다 싸다.
            log.warn("파티 상태 조회 실패. 가장 긴 유예로 처리한다 userId={}", userId);
            return presence.playingGrace();
        }
        Duration grace = presence.departureGrace();
        for (PartyStatus status : statuses) {
            Duration candidate = graceFor(status);
            if (candidate.compareTo(grace) > 0) {
                grace = candidate;
            }
        }
        return grace;
    }

    private Duration graceFor(PartyStatus status) {
        return switch (status) {
            case PLAYING -> presence.playingGrace();
            case READY -> presence.readyGrace();
            case OPEN, CLOSED -> presence.departureGrace();
        };
    }
}
