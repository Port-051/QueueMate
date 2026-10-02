package com.queuemate.platform.room.domain;

/**
 * 접속 확인의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/heartbeat-room.lua} 가 돌려주는 목록의 첫 칸과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum HeartbeatResult {

    /** 수명을 늘렸다. 아직 이 방에 있다 */
    ALIVE(1),

    /** 이 방에 없는 사람이다. 신호가 끊겨 있던 동안 수명이 다해 빠졌을 수 있다 — 클라이언트는 방 화면을 닫는다 */
    NOT_IN_ROOM(-1),

    /**
     * 방이 없어졌다. 방장의 신호가 끊겨 방의 수명이 다했다 — 이 사람의 입장 표시는 스크립트가 그 자리에서 지웠다.
     *
     * <p>방의 수명은 방장의 신호만 늘린다. 그래서 방장이 말없이 사라지면 방이 저절로 없어진다 (docs/11 D-11 11번).
     */
    ROOM_CLOSED(-2);

    private final long code;

    HeartbeatResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 "살아 있다"로 읽지 않는다.
     */
    public static HeartbeatResult fromCode(Long code)
    {
        for (HeartbeatResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("heartbeat-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
