package com.queuemate.party.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param playStartDelaySeconds 전원 준비 상태가 이만큼 유지되면 게임에 들어갔다고 본다.
 *
 * 서버는 게임을 관측할 수 없다. Riot API 연동이 범위 밖이고 게임은 브라우저 밖에서
 * 벌어진다. 시작 버튼을 두는 방법도 있지만, 누르지 않으면 게임 중인데도 게임 전 규칙이
 * 그대로 적용된다. 매번 눌러야 걸리는 보호 장치는 정확히 필요한 순간에 빠진다.
 * 그래서 관측 대신 추정한다. 근거는 전원 준비 상태가 유지된 시간뿐이다.
 *
 * 값의 의미는 전원이 준비를 마치고 게임 클라이언트에서 서로를 추가해 파티를 맺기까지
 * 걸리는 시간이다. 짧게 잡으면 아직 준비 중인 파티를 게임 중으로 오인해 이탈 판정이
 * 느려지고, 길게 잡으면 이미 게임에 들어간 파티에 짧은 유예가 남는다.
 * 뒤쪽이 더 비싸므로 확신이 없으면 짧은 쪽으로 잡는다.
 *
 * @param maxPlaySeconds 이 시간을 넘긴 PLAYING 파티는 닫는다.
 *
 * 게임 종료도 관측할 수 없다. 보통은 탭을 닫으면서 이탈 정리가 파티를 닫지만,
 * 탭을 켜 둔 채 자리를 뜨면 파티가 남는다. 그러면 최근 함께한 사람에 잡히지 않고
 * 재접속 스냅샷에 끝난 파티가 계속 실린다.
 * 2판 이상 예약이 두 판을 도는 시간을 넉넉히 넘겨서 잡는다.
 *
 * @param sweepMs 전이 대상을 확인하는 주기. 이 값만큼 전이가 늦어질 수 있다.
 */
@ConfigurationProperties(prefix = "queuemate.party")
public record PartyLifecycleProperties(long playStartDelaySeconds, long maxPlaySeconds, long sweepMs) {

    public Duration playStartDelay() {
        return Duration.ofSeconds(playStartDelaySeconds);
    }

    public Duration maxPlay() {
        return Duration.ofSeconds(maxPlaySeconds);
    }
}
