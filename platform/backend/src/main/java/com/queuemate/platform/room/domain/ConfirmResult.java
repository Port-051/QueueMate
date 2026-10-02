package com.queuemate.platform.room.domain;

/**
 * 방장 확정 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/confirm-room.lua} 가 돌려주는 목록의 첫 칸과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum ConfirmResult {

    /** 확정했다. 그 순간 방에 있던 전원이 파티원이다. 이제 새 사람이 못 들어오고, 되돌릴 수 없다 */
    CONFIRMED(1),

    /** 이미 확정된 방이다. 버튼을 두 번 눌렀거나 재시도다 — 아무것도 바뀌지 않았다 */
    ALREADY_CONFIRMED(2),

    /** 방이 없다. 만들어진 적이 없거나 방장이 나가서 사라졌다 */
    ROOM_NOT_FOUND(-4),

    /** 부른 사람이 방장이 아니다. 방 밖의 사람도 여기로 온다 */
    NOT_HOST(-5),

    /** 혼자서는 확정할 수 없다. 방장을 포함해 2명 이상이어야 한다 */
    NOT_ENOUGH_MEMBERS(-7);

    private final long code;

    ConfirmResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 확정으로 읽지 않는다.
     */
    public static ConfirmResult fromCode(Long code)
    {
        for (ConfirmResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("confirm-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
