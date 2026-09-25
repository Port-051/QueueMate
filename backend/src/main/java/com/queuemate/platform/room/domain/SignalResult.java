package com.queuemate.platform.room.domain;

/**
 * 시그널 전달 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/signal-room.lua} 의 반환값과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum SignalResult {

    /**
     * 받는 사람의 채널에 발행했다. <b>도착을 뜻하지 않는다</b> — 받는 사람의 SSE 가 끊겨 있으면 사라지고,
     * 그 복구(재-offer · ICE restart)는 클라이언트의 일이다 ({@code matching} 의 contracts/events.md "WEBRTC_SIGNAL").
     */
    SENT(1),

    /** 보낸 사람이 이 방에 없다 */
    NOT_IN_ROOM(-1),

    /** 받는 사람이 이 방에 없다. 이미 나갔거나 다른 방에 있다 */
    TARGET_NOT_IN_ROOM(-3);

    private final long code;

    SignalResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 "보내도 된다"로 읽지 않는다.
     */
    public static SignalResult fromCode(Long code)
    {
        for (SignalResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("signal-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
