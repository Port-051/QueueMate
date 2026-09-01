package com.queuemate.realtime.presence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param departureGraceSeconds 연결이 끊긴 뒤 이탈로 판정하기까지 기다리는 시간.
 *
 * 두 사람을 함께 봐야 한다. 끊긴 사람은 돌아올 시간이 필요하고, 남은 사람은 빈 파티룸에서
 * 그만큼 기다린다.
 *
 *   브라우저 새로고침      1~3초
 *   와이파이와 LTE 전환    5~15초
 *   짧은 터널              20~40초
 *
 * 처음에는 터널까지 덮으려고 30초로 잡았다가 10초로 줄였다. 끊긴 사람만 기준으로 본
 * 값이었기 때문이다. 남은 사람 입장에서 게임 시작 전 30초는 길다.
 *
 * 지금 파티는 전부 게임 시작 전이다. 이 구간에서는 잘못 내보내도 다시 매칭하면 되므로
 * 오판 비용이 크지 않다. 새로고침과 짧은 끊김은 10초로 흡수되고, 그보다 긴 전환은
 * 놓치는 것을 감수한다.
 *
 * PLAYING이 구현되면 게임 중에는 더 길게 둬야 한다. 그때는 잘못 내보내는 비용이
 * 진행 중인 게임을 버리게 만드는 것이라 성격이 달라진다.
 *
 * @param sweepMs 만료된 이탈 대상을 확인하는 주기. 이 값만큼 판정이 늦어질 수 있다.
 */
@ConfigurationProperties(prefix = "queuemate.presence")
public record PresenceProperties(long departureGraceSeconds, long sweepMs) {

    public Duration departureGrace() {
        return Duration.ofSeconds(departureGraceSeconds);
    }
}
