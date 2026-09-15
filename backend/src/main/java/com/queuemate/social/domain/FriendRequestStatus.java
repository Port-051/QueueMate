package com.queuemate.social.domain;

/** friend_requests.status CHECK 제약과 값이 일치해야 한다. */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}
