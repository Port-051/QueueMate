package com.queuemate.platform.social.dto;

import java.util.List;

/** {@code GET /friends} 의 응답 — {@code {"friends": […]}}. 없으면 빈 배열이다 */
public record FriendListResponse(List<FriendResponse> friends) {
}
