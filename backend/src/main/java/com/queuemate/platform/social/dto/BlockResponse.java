package com.queuemate.platform.social.dto;

import java.time.Instant;

/**
 * 차단 한 건 — 내가 차단한 사람. {@code createdAt} 은 차단한 시각이다(ISO-8601 UTC).
 *
 * @param nickname {@code users} 를 JOIN 해 붙인다
 */
public record BlockResponse(Long userId, String nickname, Instant createdAt) {
}
