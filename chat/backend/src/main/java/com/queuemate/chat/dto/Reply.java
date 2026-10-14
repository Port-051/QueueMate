package com.queuemate.chat.dto;

import com.queuemate.chat.domain.Type;

/**
 * 보낸 소켓에만 돌아가는 응답(CLAUDE.md §3.1) — 핸들러가 그 소켓에 직접 쓴다(방에 뿌리는 것이 아니라 C-2 와 어긋나지 않는다).
 *
 * @param type {@link Type#ACK}(발행했다) 또는 {@link Type#ERROR}(발행하지 않았다)
 * @param cid  어느 메시지에 대한 답인가 — 브라우저가 보낸 cid 그대로. JSON 이 깨져 cid 를 모르면 null
 * @param code ERROR 일 때만 — {@code BAD_JSON} · {@code EMPTY} · {@code TOO_LONG} · {@code TOO_FAST} · {@code RETRY}(Redis 실패). ACK 면 null
 */
public record Reply(Type type, String cid, String code) {

    public static Reply ack(String cid) {
        return new Reply(Type.ACK, cid, null);
    }

    public static Reply error(String cid, String code) {
        return new Reply(Type.ERROR, cid, code);
    }
}
