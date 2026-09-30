package com.queuemate.platform.party.dto;

import java.util.List;

/**
 * {@code GET /api/v1/posts} 의 응답. 단위는 <b>모집 글(방)</b>이다 — 사람 목록이 아니다(CLAUDE.md §1).
 *
 * @param posts      이 페이지에서 <b>보이는</b> 글. 차단으로 숨겨진 글은 빠져 있고, 빠졌다는 흔적도 없다
 * @param nextCursor 더 볼 것이 있으면 다음 요청의 {@code cursor} 로 보낼 값, 없으면 {@code null} (2026-09-23 소유자 결정).
 *                   <b>마지막으로 읽은 글의 번호다</b>(2026-09-25 소유자 결정으로 base64url 한 겹이 없어졌다 — JSON 에 숫자로 나간다.
 *                   {@code PostService#list}). 마지막으로 <b>보여 준</b> 글이 아니다 — 차단으로 숨겨진 글을 다음 페이지에서 또 읽지 않게
 *                   ({@code contracts/platform-api.md} "모집 글 · 목록")
 */
public record PostListResponse(List<PostResponse> posts, Long nextCursor) {
}
