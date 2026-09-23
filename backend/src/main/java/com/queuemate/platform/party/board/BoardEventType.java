package com.queuemate.platform.party.board;

/**
 * 게시판 채널에 보내는 신호의 종류. 봉투의 {@code type} 칸에 {@link #name()} 그대로 나간다.
 *
 * <p>문자열이 아니라 enum 인 이유 — 오타를 컴파일에서 막는다. 받는 쪽(브라우저)은 모르는 {@code type} 을 조용히 버리므로
 * 오타가 나가면 어디에서도 에러가 나지 않는다({@code matching} · {@code room} 의 {@code PushEventType} 과 같은 이유다).
 */
public enum BoardEventType {

    /**
     * "목록을 다시 받아라". {@code room} 도 같은 이름으로 같은 채널에 보낸다(그쪽의 {@code PushEventType.BOARD_CHANGED}).
     * {@code payload} 는 {@code {}} 다 — {@code roomId} 도 {@code game} 도 싣지 않는다(docs/11 D-20 · D-22). 이름은 계약이 정했다 — 바꾸면 프런트가 못 알아본다
     */
    BOARD_CHANGED
}
