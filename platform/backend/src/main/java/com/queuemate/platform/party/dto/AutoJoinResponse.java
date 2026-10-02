package com.queuemate.platform.party.dto;

/**
 * 게시판 방 먼저 합류의 200 — 들어간 글과 그 방. <b>둘은 같은 숫자다</b>(게시판 방의 {@code roomId} 는 글의 번호) — 프런트가 그것을 전제하지 않도록 둘 다 준다
 * (2026-09-28 소유자 결정 · P-28).
 */
public record AutoJoinResponse(Long postId, Long roomId) {
}
