package com.queuemate.platform.social.dto;

/**
 * 친구 요청 한 줄 안의 사람 — {@code {userId, nickname}}.
 *
 * @param nickname {@code users} 를 JOIN 해 붙인다
 */
public record FriendUser(Long userId, String nickname) {
}
