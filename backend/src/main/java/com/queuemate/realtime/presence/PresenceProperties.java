package com.queuemate.realtime.presence;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 연결이 끊긴 뒤 이탈로 판정하기까지 기다리는 시간. 파티 상태마다 다르다.
 *
 * 하나의 값으로는 맞출 수 없다. 잘못 내보내는 비용과 기다리는 비용의 비율이
 * 게임 전후로 뒤집히기 때문이다.
 *
 *   게임 전   남은 사람이 파티룸을 보면서 기다린다. 기다리는 비용이 크다.
 *             잘못 내보내도 다시 매칭하면 되므로 오판 비용은 작다.
 *   게임 중   아무도 파티룸을 보고 있지 않다. 기다리는 비용이 거의 없다.
 *             잘못 내보내면 진행 중인 게임의 음성 채널과 파티가 사라진다. 되돌릴 수 없다.
 *
 * 끊김의 실제 길이는 이렇다.
 *
 *   브라우저 새로고침      1~3초
 *   와이파이와 LTE 전환    5~15초
 *   짧은 터널              20~40초
 *   공유기 재시작          약 60초
 *   PC 재부팅              2~5분
 *
 * @param departureGraceSeconds OPEN 상태의 유예.
 *
 * 아직 전원이 준비하지 않았다. 안 오는 사람을 오래 붙들면 나머지가 그만큼
 * 대기열로 못 돌아간다. 새로고침과 짧은 끊김만 흡수하고 나머지는 포기한다.
 *
 * @param readyGraceSeconds READY 상태의 유예.
 *
 * 전원이 합의하고 게임 클라이언트에서 서로를 추가하는 중이다. 여기서 내보내면
 * 다 만들어진 파티가 깨지고 남은 사람들은 처음부터 다시 매칭해야 한다.
 * OPEN보다 길게 주되, 아직 남은 사람이 화면을 보고 있으므로 분 단위로 가지 않는다.
 *
 * @param playingGraceSeconds PLAYING 상태의 유예.
 *
 * 게임 중이다. PC가 한 번 재부팅되는 시간까지 덮는다. 이 구간에서 길게 기다리는 것은
 * 사실상 공짜다. 반대로 잘못 내보내면 되돌릴 방법이 없다.
 * 게임이 끝나고 다들 탭을 닫으면 이 유예가 지난 뒤 파티가 정리된다.
 *
 * @param sweepMs 만료된 이탈 대상을 확인하는 주기. 이 값만큼 판정이 늦어질 수 있다.
 *
 * @param reconcileMs 파티 멤버와 접속 여부를 대조하는 주기.
 *
 * 정상 경로가 실패했을 때만 무언가를 찾는 수리 작업이다. 서버가 강제 종료되는 일은
 * 드물기 때문에 자주 돌 이유가 없고, 대신 한 번에 훑는 양이 sweep보다 훨씬 많다.
 * 이 값만큼 방치된 파티의 정리가 늦어지지만, 아예 안 되던 것에 비하면 상한이 생긴 것이다.
 */
@ConfigurationProperties(prefix = "queuemate.presence")
public record PresenceProperties(long departureGraceSeconds, long readyGraceSeconds,
                                 long playingGraceSeconds, long sweepMs, long reconcileMs) {

    public Duration departureGrace() {
        return Duration.ofSeconds(departureGraceSeconds);
    }

    public Duration readyGrace() {
        return Duration.ofSeconds(readyGraceSeconds);
    }

    public Duration playingGrace() {
        return Duration.ofSeconds(playingGraceSeconds);
    }
}
