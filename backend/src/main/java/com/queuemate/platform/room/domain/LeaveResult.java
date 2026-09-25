package com.queuemate.platform.room.domain;

/**
 * 나가기 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/leave-room.lua} 의 반환값과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum LeaveResult {

    /** 방에서 나갔다 */
    LEFT(1),

    /**
     * 방장이 나가서 방이 없어졌다. 방장 키 · 멤버 SET · 남아 있던 전원의 입장 표시 키가 함께 지워졌다.
     *
     * <p>방장이 나가면 방에 다른 사람이 있어도 방을 없앤다 (방장 확정 전까지 — docs/11 D-11 11번).
     */
    ROOM_CLOSED(2),

    /** 이 방에 없는 사람이다. 이미 나갔거나, 늦게 도착한 나가기이거나, 없는 방이다 — 아무것도 지우지 않았다 */
    NOT_IN_ROOM(-1);

    private final long code;

    LeaveResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 성공으로 읽지 않는다.
     */
    public static LeaveResult fromCode(Long code)
    {
        for (LeaveResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("leave-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
