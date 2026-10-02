package com.queuemate.platform.social.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /friend-requests} 의 본문 — {@code userId} 는 상대의 <b>사용자 번호</b>다. <b>번호를 정확히 알아야 요청을 보낼 수 있다</b> — 사람을 찾아보는 길은
 * 없다(CLAUDE.md §1). 문자열로 받아 서비스가 {@code Long} 으로 판다({@code common.web.Ids}) — 숫자가 아니면 있을 수 없는 사용자라 없는 사용자와 같은
 * 404 {@code USER_NOT_FOUND} 다(비어 있는지만 여기서 본다).
 */
public record FriendRequestCreateRequest(@NotBlank(message = "필요합니다") String userId) {
}
