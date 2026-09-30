package com.queuemate.platform.room.domain;

/**
 * 방 만들기 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/create-room.lua} 의 반환값과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum CreateResult {

    /** 방을 만들었다. 만든 사람이 방장이고 곧바로 그 방에 들어와 있다 */
    CREATED(1),

    /** 이미 내가 만든 방이다. 새로고침이나 재시도다 — 아무것도 바뀌지 않았다 */
    ALREADY_CREATED(2),

    /**
     * {@code matching} 의 활성 요청 키가 있다. 자동 매칭을 돌리는 중이다.
     *
     * <p>한 사용자는 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 (docs/11 D-11 9번 · D-19).
     */
    ACTIVE_REQUEST_EXISTS(-1),

    /** 다른 방에 들어가 있다 */
    IN_OTHER_ROOM(-3),

    /** 이미 다른 사람이 방장인 방이다 */
    ROOM_EXISTS(-4);

    private final long code;

    CreateResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 성공으로 읽지 않는다.
     */
    public static CreateResult fromCode(Long code)
    {
        for (CreateResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("create-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
