package com.queuemate.platform.party.dto;

import java.util.List;

/** {@code GET /api/v1/posts} 의 응답. 단위는 <b>모집 글(방)</b>이다 — 사람 목록이 아니다(CLAUDE.md §1) */
public record PostListResponse(List<PostResponse> posts) {
}
