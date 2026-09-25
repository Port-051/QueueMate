package com.queuemate.platform.common.push;

/**
 * 이 앱이 발행하는 <b>개인 알림</b>의 종류. 봉투의 {@code type} 칸에 {@link #name()} 그대로 나간다.
 * 이름과 {@code payload} 의 원본은 {@code contracts/platform-api.md} "이 앱이 내는 알림"(친구 둘)과 {@code contracts/platform-api.md} "방" 의 알림(방 안의 일 여섯 —
 * 2026-09-25 에 {@code room} 앱을 합치며 그쪽의 {@code PushEventType} 에서 옮겨 왔다. 이름 · {@code payload} 는 글자 그대로다)이다 — 거기 없는 것을 여기에 먼저 만들지 마라.
 *
 * <p>문자열이 아니라 enum 인 이유 — 오타를 컴파일에서 막는다. 받는 쪽(브라우저)은 모르는 {@code type} 을 조용히 버리므로
 * 오타가 나가면 어디에서도 에러가 나지 않는다({@code matching} 의 {@code PushEventType} 과 같은 이유다).
 *
 * <p>{@code PARTY_*} 는 아직 없다 — 자동 매칭 파티가 없다. 방 안의 일은 {@code PARTY_*} 를 다시 쓰지 않고 {@code ROOM_*} 이라는 새 이름을 지었다. 거절 · 거두기 · 친구 끊기 · 차단은 알리지 않는다.
 * 게시판 채널의 신호({@code BOARD_CHANGED})는 개인 알림이 아니라서 {@code party.board.BoardEventType} 에 있다.
 */
public enum PushEventType {

    /** 친구 요청을 받았다. 받는 사람 = 요청을 받은 사람. {@code payload} 는 {@code {requestId, fromUserId}} */
    FRIEND_REQUEST_RECEIVED,

    /** 내가 보낸 친구 요청이 수락됐다. 받는 사람 = 요청을 보냈던 사람. {@code payload} 는 {@code {requestId, userId}}(수락한 사람) */
    FRIEND_REQUEST_ACCEPTED,

    /** 누가 방에 들어왔다. 방에 이미 있던 사람들이 받는다. {@code payload} 는 {@code {roomId, userId}} */
    ROOM_MEMBER_ENTERED,

    /**
     * 누가 방에서 나갔다(나가기 · 방장의 접속 확인이 유령을 뺐다 · 확정한 방의 방장이 나갔다). 방에 남은 사람들이 받는다.
     * {@code payload} 는 {@code {roomId, userId}}
     */
    ROOM_MEMBER_LEFT,

    /** 방장이 나가서 방이 없어졌다. 방에 있던 사람들(방장 제외)이 받는다. {@code payload} 는 {@code {roomId}} */
    ROOM_CLOSED,

    /**
     * 방장이 누군가를 강퇴했다. 방에 남은 사람들(방장 포함)과 <b>강퇴된 본인</b>이 받는다. {@code payload} 는 {@code {roomId, userId}} 이고
     * {@code userId} 가 강퇴된 사람이다. 본인에게도 보내는 이유 — 자기가 부른 요청이 아니라서 알림이 아니면 알 길이 없다
     */
    ROOM_MEMBER_KICKED,

    /**
     * 방장이 파티를 확정했다. 그 순간 방에 있던 전원(방장 포함)이 받는다 — 이 사람들이 파티원이다.
     * {@code payload} 는 {@code {roomId, members}}. 이제 새 사람이 못 들어온다
     */
    ROOM_CONFIRMED,

    /** WebRTC 시그널 전달. 이름은 계약이 정했다 (docs/11 D-9 · contracts/events.md). {@code payload} 는 {@code {roomId, fromUserId, signal}} 이다 */
    WEBRTC_SIGNAL
}
