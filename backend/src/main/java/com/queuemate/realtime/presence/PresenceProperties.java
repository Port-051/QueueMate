package com.queuemate.realtime.presence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param departureGraceSeconds 연결이 끊긴 뒤 이탈로 판정하기까지 기다리는 시간.
 *
 * 짧게 잡으면 잠깐 끊긴 사람을 내보내고, 길게 잡으면 이미 떠난 사람 때문에 남은 사람이
 * 빈 파티룸에서 기다린다. 실제 끊김 시간을 놓고 잡았다.
 *
 *   브라우저 새로고침      1~3초
 *   와이파이와 LTE 전환    5~15초
 *   짧은 터널              20~40초
 *
 * 30초면 앞의 둘을 확실히 흡수하고 터널의 상당 부분을 덮는다. 그보다 길게 끊긴 사람은
 * 돌아와도 이미 게임이 시작됐을 가능성이 높아 파티를 유지할 실익이 적다.
 * 반대로 남은 사람 입장에서 30초는 답답하지만 참을 수 있는 수준이다.
 *
 * @param sweepMs 만료된 이탈 대상을 확인하는 주기. 이 값만큼 판정이 늦어질 수 있다.
 */
@ConfigurationProperties(prefix = "queuemate.presence")
public record PresenceProperties(long departureGraceSeconds, long sweepMs) {

    public Duration departureGrace() {
        return Duration.ofSeconds(departureGraceSeconds);
    }
}
