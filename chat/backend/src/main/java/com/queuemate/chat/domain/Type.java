package com.queuemate.chat.domain;

/**
 * 봉투의 {@code type} (CLAUDE.md §3.1).
 * 들어오는 것(브라우저 → 서버)은 {@link #SEND} 하나다. 나가는 것은 방 전체로 가는 {@link #MSG} 와 보낸 소켓에만 돌아가는 {@link #ACK} · {@link #ERROR} 다.
 */
public enum Type {
    SEND,
    MSG,
    ACK,
    ERROR
}
