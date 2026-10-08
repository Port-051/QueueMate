package com.queuemate.platform.social.dto;

import java.util.List;

/** {@code GET /recent-players} 의 응답 — {@code {"players": […]}}. 없으면 빈 배열이다 */
public record RecentPlayerListResponse(List<RecentPlayerResponse> players) {
}
