package com.queuemate.platform.party.dto;

import java.util.List;

/**
 * {@code GET /api/v1/posts} 의 응답. 단위는 <b>모집 글(방)</b>이다 — 사람 목록이 아니다(CLAUDE.md §1).
 *
 * @param posts      이 페이지에서 <b>보이는</b> 글. 차단으로 숨겨진 글은 빠져 있고, 빠졌다는 흔적도 없다
 * @param nextCursor 더 볼 것이 있으면 다음 요청의 {@code cursor} 로 보낼 값, 없으면 {@code null} (2026-09-23 소유자 결정).
 *                   <b>불투명한 문자열이다</b> — 뜯어보지 말고 받은 그대로 보낸다. <b>"마지막으로 읽은 줄"이 기준이다</b> —
 *                   차단으로 숨겨진 글을 다음 페이지에서 또 읽지 않게 ({@code contracts/platform-api.md} "모집 글 · 목록 · 입장권")
 */
public record PostListResponse(List<PostResponse> posts, String nextCursor) {
}
