package com.queuemate.platform.social.dto;

/**
 * 친구 요청 한 줄 안의 사람 — {@code {userId, nickname}}.
 *
 * @param nickname {@code account} 의 창구({@code UserReader})로 붙인다 — {@code account.users} 를 JOIN 하지 않는다 (CLAUDE.md §3.5)
 */
public record FriendUser(Long userId, String nickname) {
}
