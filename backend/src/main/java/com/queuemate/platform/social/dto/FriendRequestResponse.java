package com.queuemate.platform.social.dto;

import java.time.Instant;

/**
 * 친구 요청 한 줄 — {@code {requestId, requester: {userId, nickname}, receiver: {userId, nickname}, createdAt}}.
 * 보낸 목록과 받은 목록이 같은 모양을 쓴다. <b>대기 중인 것만 나가므로 상태 칸이 없다.</b>
 */
public record FriendRequestResponse(Long requestId, FriendUser requester, FriendUser receiver, Instant createdAt) {
}
