package com.queuemate.common.security;

import java.util.UUID;

/**
 * 인증된 요청 주체. 클라이언트가 보낸 userId는 신뢰하지 않고
 * 항상 토큰에서 꺼낸 이 값을 쓴다 (docs/13 Authorization).
 */
public record CurrentUser(UUID userId) {
}
