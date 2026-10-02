package com.queuemate.platform.party.board;

/**
 * 게시판 채널의 이름. <b>게임을 구분하지 않는 하나다</b>(docs/11 D-22).
 *
 * <p><b>발행하는 앱(이 앱)과 구독하는 앱({@code notification})의 약속이다.</b> 어긋나도 컴파일 · 테스트는 통과하고
 * 목록이 조용히 갱신되지 않는다 — 발행 쪽은 구독자 0명을 실패로 보지 않기 때문이다. 그래서 이 한 곳에만 둔다 (CLAUDE.md §3.2).
 *
 * <p><b>원본 상수를 어느 서비스에 둘지는 미정이다</b>(CLAUDE.md §7.1). 지금은 두 앱이 각자 같은 값을 적어 두었다 —
 * {@code notification} 의 {@code redisKeys/BoardChannels.BOARD_CHANNEL} 과 여기. <b>두 값이 같아야 한다.</b> 바꿀 때는 둘을 같이 바꾼다.
 * (2026-09-25 에 {@code room} 앱을 합쳤다 — 방의 인원이 바뀔 때의 신호도 이제 이 상수로 나간다. 그쪽이 적어 두었던 {@code redisKeys/SharedKeys.BOARD_CHANNEL} 은 옮기지 않았다.)
 */
public final class BoardChannels {

    public static final String BOARD_CHANNEL = "qm:pubsub:board";

    private BoardChannels()
    {
    }
}
