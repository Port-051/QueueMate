package com.queuemate.platform.social.dto;

import java.time.Instant;

/**
 * 최근 함께한 사람 한 명.
 *
 * @param lastPartyId  마지막으로 같이 한 파티의 id
 * @param lastPlayedAt 마지막으로 같이 한 시각(ISO-8601 UTC)
 */
public record RecentPlayerResponse(Long userId, String nickname, Long lastPartyId, Instant lastPlayedAt) {
}
