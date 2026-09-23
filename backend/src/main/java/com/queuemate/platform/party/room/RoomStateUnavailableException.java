package com.queuemate.platform.party.room;

/**
 * 방 키를 <b>읽지 못했다</b>(Redis 가 죽었거나 느리다). "읽었더니 방이 없다"와 전혀 다른 것이다 — 이것을 "방이 없다"로 읽으면
 * 멀쩡한 글이 전부 만료된다. 그래서 빈 결과가 아니라 예외로 알린다: 부른 쪽이 반드시 갈래를 정해야 한다
 * (목록은 방 정보를 비운 채 내려 주고 만료 판정을 건너뛴다, 입장권은 503 으로 거절한다).
 */
public class RoomStateUnavailableException extends RuntimeException {

    public RoomStateUnavailableException(Throwable cause)
    {
        super("room 의 방 키를 읽지 못했다", cause);
    }
}
