package com.queuemate.platform.social.dto;

import java.util.List;

/** {@code GET /friend-requests} 의 응답 — {@code {"requests": […]}}. 없으면 빈 배열이다 */
public record FriendRequestListResponse(List<FriendRequestResponse> requests) {
}
