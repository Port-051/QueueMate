package com.queuemate.platform.social.domain;

/**
 * 친구 요청의 상태. DB 의 {@code friend_requests_status_check} 와 같은 목록이다 — 바꾸면 마이그레이션도 새로 쓴다.
 * <b>{@code PENDING} 에서 나머지 셋 가운데 하나로 한 번만 간다</b> — 되돌아가지 않는다. 다시 요청하면 새 줄이다.
 */
public enum FriendRequestStatus {

    /** 대기 중. 목록에 보이는 것은 이것뿐이다 */
    PENDING,

    /** 받은 사람이 수락했다 — 또는 반대 방향의 요청이 수락되면서 같이 닫혔다 */
    ACCEPTED,

    /** 받은 사람이 거절했다. 보낸 사람에게 알리지 않는다 */
    DECLINED,

    /** 보낸 사람이 거뒀다 */
    CANCELED
}
