package com.queuemate.platform.room.domain;

/**
 * 입장 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/enter-room.lua} 의 반환값과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum EnterResult {

    /** 방에 들어왔다 */
    ENTERED(1),

    /** 이미 이 방에 들어와 있다. 새로고침이나 재시도다 — 아무것도 바뀌지 않았다 */
    ALREADY_ENTERED(2),

    /**
     * {@code matching} 의 활성 요청 키가 있다. 자동 매칭을 돌리는 중이다.
     *
     * <p>한 사용자는 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 (docs/11 D-11 9번 · D-19).
     */
    ACTIVE_REQUEST_EXISTS(-1),

    /** 방이 가득 찼다. 방장 포함 5명이다 (docs/11 D-11 10번) */
    FULL(-2),

    /** 다른 방에 들어가 있다. 입장 표시 키의 값이 이 방의 {@code roomId} 가 아니다 */
    IN_OTHER_ROOM(-3),

    /** 그런 방이 없다. 만들어진 적이 없거나 방장이 나가서 사라졌다 — 방장 키가 없다 */
    ROOM_NOT_FOUND(-4),

    /** 방장이 확정한 방이다. 새 사람은 못 들어온다 — 자리가 비어 있어도 마찬가지다. 이미 들어와 있는 사람의 재입장은 {@link #ALREADY_ENTERED} 그대로다 */
    ROOM_CONFIRMED(-7);

    private final long code;

    EnterResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 입장으로 읽지 않는다.
     */
    public static EnterResult fromCode(Long code)
    {
        for (EnterResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("enter-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
