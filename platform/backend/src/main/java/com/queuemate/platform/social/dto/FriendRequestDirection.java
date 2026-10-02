package com.queuemate.platform.social.dto;

/**
 * {@code GET /api/v1/friend-requests?direction=} 의 값 — 내가 받은 것인가, 보낸 것인가. <b>대문자 그대로만 받는다</b>(2026-09-26 소유자 결정 —
 * 게시판 목록의 {@code game} 과 같다. 소문자는 형 변환에서 400 이다).
 */
public enum FriendRequestDirection {

    /** 내가 받은 요청. 기본값이다 */
    RECEIVED,

    /** 내가 보낸 요청 */
    SENT
}
