package com.queuemate.notification.redisKeys;

/**
 * 게시판 채널. 게임을 구분하지 않는 <b>채널 하나</b>다 (docs/11 D-22 가 D-20 의 게임별 채널을 개정했다).
 *
 * <p>{@code platform} 은 글이 바뀔 때, {@code room} 은 방의 인원이 바뀔 때 여기에 {@code BOARD_CHANGED} 를 발행한다.
 * 이름이 어긋나면 컴파일도 테스트도 통과한 채로 게시판이 조용히 갱신되지 않는다 — 발행하는 쪽과 같이 바꾼다.
 */
public final class BoardChannels {

    public static final String BOARD_CHANNEL = "qm:pubsub:board";

    private BoardChannels() {
    }
}
