package com.queuemate.realtime.event;

/**
 * contracts/events.md의 Server → Client 목록과 정확히 일치해야 한다.
 * 이름이 곧 계약이므로 임의로 바꾸거나 추가하지 않는다.
 */
public enum EventType {
    MATCH_QUEUE_UPDATED,
    MATCH_PROPOSAL_CREATED,
    MATCH_PROPOSAL_EXPIRED,
    MATCH_CONFIRMED,
    MATCH_CANCELLED,
    RESERVATION_UPDATED,
    RESERVATION_PROPOSAL_CREATED,
    PARTY_MEMBER_JOINED,
    PARTY_MEMBER_LEFT,
    PARTY_READY_CHANGED,
    PARTY_CLOSED,
    FRIEND_REQUEST_RECEIVED,
    FRIEND_REQUEST_UPDATED,
    PARTY_INVITE_RECEIVED,
    WEBRTC_SIGNAL
}
