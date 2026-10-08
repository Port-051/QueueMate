package com.queuemate.platform.room.domain;

/**
 * 방 키를 <b>읽지 못했다</b>(Redis 가 죽었거나 느리다). "읽었더니 방이 없다"와 전혀 다른 것이다 — 이것을 "방이 없다"로 읽으면
 * 멀쩡한 글이 전부 만료된다. 그래서 빈 결과가 아니라 예외로 알린다: <b>부른 쪽이 반드시 갈래를 정해야 한다</b>
 * (목록 · 단건은 방 정보를 비운 채 내려 주고 만료 판정을 건너뛴다 — fail-open, 입장 검사 · 게시판 방 먼저 합류는 503 으로 거절한다 — fail-closed).
 */
public class RoomStateUnavailableException extends RuntimeException {

    public RoomStateUnavailableException(Throwable cause)
    {
        super("방 키를 읽지 못했다", cause);
    }
}
