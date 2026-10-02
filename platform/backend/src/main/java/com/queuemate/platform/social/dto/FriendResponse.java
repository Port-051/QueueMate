package com.queuemate.platform.social.dto;

import java.time.Instant;

/**
 * 친구 한 명. 수락의 응답(새 친구)과 친구 목록이 같이 쓴다.
 *
 * @param since 친구가 된 시각(ISO-8601 UTC)
 */
public record FriendResponse(Long userId, String nickname, Instant since) {
}
