package com.queuemate.platform.common.push;

/**
 * 이 앱이 발행하는 <b>개인 알림</b>의 종류. 봉투의 {@code type} 칸에 {@link #name()} 그대로 나간다.
 * 이름과 {@code payload} 의 원본은 {@code contracts/platform-api.md} "이 앱이 내는 알림" 이다 — 거기 없는 것을 여기에 먼저 만들지 마라.
 *
 * <p>문자열이 아니라 enum 인 이유 — 오타를 컴파일에서 막는다. 받는 쪽(브라우저)은 모르는 {@code type} 을 조용히 버리므로
 * 오타가 나가면 어디에서도 에러가 나지 않는다({@code matching} · {@code room} 의 {@code PushEventType} 과 같은 이유다).
 *
 * <p>{@code PARTY_*} 는 아직 없다 — 자동 매칭 파티가 없다. 거절 · 거두기 · 친구 끊기 · 차단은 알리지 않는다.
 * 게시판 채널의 신호({@code BOARD_CHANGED})는 개인 알림이 아니라서 {@code party.board.BoardEventType} 에 있다.
 */
public enum PushEventType {

    /** 친구 요청을 받았다. 받는 사람 = 요청을 받은 사람. {@code payload} 는 {@code {requestId, fromUserId}} */
    FRIEND_REQUEST_RECEIVED,

    /** 내가 보낸 친구 요청이 수락됐다. 받는 사람 = 요청을 보냈던 사람. {@code payload} 는 {@code {requestId, userId}}(수락한 사람) */
    FRIEND_REQUEST_ACCEPTED
}
