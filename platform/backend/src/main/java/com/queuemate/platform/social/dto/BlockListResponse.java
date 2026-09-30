package com.queuemate.platform.social.dto;

import java.util.List;

/** {@code GET /blocks} 의 응답 — {@code {"blocks": […]}}. 없으면 빈 배열이다 */
public record BlockListResponse(List<BlockResponse> blocks) {
}
