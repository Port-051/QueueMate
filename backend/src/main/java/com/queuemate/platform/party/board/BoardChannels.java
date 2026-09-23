package com.queuemate.platform.party.board;

/**
 * 게시판 채널의 이름. <b>게임을 구분하지 않는 하나다</b>(docs/11 D-22).
 *
 * <p><b>발행하는 앱(이 앱 · {@code room})과 구독하는 앱({@code notification})의 약속이다.</b> 어긋나도 컴파일 · 테스트는 통과하고
 * 목록이 조용히 갱신되지 않는다 — 발행 쪽은 구독자 0명을 실패로 보지 않기 때문이다. 그래서 이 한 곳에만 둔다 (CLAUDE.md §3.2).
 *
 * <p><b>원본 상수를 어느 서비스에 둘지는 미정이다</b>(CLAUDE.md §7.1). 지금은 세 앱이 각자 같은 값을 적어 두었다 —
 * {@code room} 의 {@code redisKeys/SharedKeys.BOARD_CHANNEL}, {@code notification} 의 {@code redisKeys/BoardChannels.BOARD_CHANNEL}, 그리고 여기.
 * <b>세 값이 같아야 한다.</b> 바꿀 때는 셋을 같이 바꾼다.
 */
public final class BoardChannels {

    public static final String BOARD_CHANNEL = "qm:pubsub:board";

    private BoardChannels()
    {
    }
}
