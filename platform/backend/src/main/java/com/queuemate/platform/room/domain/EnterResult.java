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

    /** 마지막 자리 입장과 같은 Lua에서 모집을 확정했다. 응답에는 포지션 스냅숏이 온다. */
    ENTERED_AND_CONFIRMED(3),

    /** 이미 이 방에 들어와 있다. 새로고침이나 재시도다 — 아무것도 바뀌지 않았다 */
    ALREADY_ENTERED(2),

    /**
     * {@code matching} 의 활성 요청 키가 있다. 자동 매칭을 돌리는 중이다.
     *
     * <p>한 사용자는 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 (docs/11 D-11 9번 · D-19).
     */
    ACTIVE_REQUEST_EXISTS(-1),

    /** 방이 가득 찼다. 정원은 방장 포함 그 글의 모드의 인원이다 — 솔로 랭크 2 · 많아야 5 (2026-09-30 — P-41 · docs/11 D-11 10번) */
    FULL(-2),

    /** 다른 방에 들어가 있다. 입장 표시 키의 값이 이 방의 {@code roomId} 가 아니다 */
    IN_OTHER_ROOM(-3),

    /** 그런 방이 없다. 만들어진 적이 없거나 방장이 나가서 사라졌다 — 방장 키가 없다 */
    ROOM_NOT_FOUND(-4),

    /**
     * 이 방에서 강퇴당한 지 10분이 안 됐다 — 입장 금지 목록({@code RoomKeys#noEntryKey})에 이 방이 남아 있다(2026-09-29 소유자 결정).
     * 강퇴만 여기 들고 스스로 나간 사람은 막지 않는다. 시간이 지나면 저절로 풀린다 — 원소의 score(풀리는 시각)와 키의 수명이 그것이다
     */
    KICKED_RECENTLY(-5),

    /**
     * 이 방에서 고를 수 없는 포지션이다(2026-09-30 소유자 결정 — P-44: 참가할 때 남은 찾는 포지션 가운데 하나를 고른다).
     * 포지션을 골라 들어오는 방(글에 찾는 포지션이 있다)인데 안 골랐거나 · 고른 것이 남은 찾는 포지션 SET 에 없다 — 남이 이미 고른 포지션도 SET 에서 빠져 있어 이것이다
     * (다 골라 SET 이 없어진 방도). 포지션이 없는 방인데 골랐어도 이것이다 — 조용히 버리지 않는다(2026-10-01).
     * 400 {@code VALIDATION_FAILED} 로 옮긴다 — 요청의 {@code position} 이 틀린 것이다
     */
    INVALID_POSITION(-6),

    /** 방장이 확정한 방이다. 새 사람은 못 들어온다 — 자리가 비어 있어도 마찬가지다. 이미 들어와 있는 사람의 재입장은 {@link #ALREADY_ENTERED} 그대로다 */
    ROOM_CONFIRMED(-7),

    /**
     * 이미 방 안의 다른 사람이 고른 포지션이다(P-44). <b>지금 스크립트는 돌려주지 않는다</b>(2026-09-30 — 입장이 고른 포지션을 찾는 포지션 SET 에서 빼는 방식으로 바꿔,
     * 남이 고른 포지션은 SET 에 없으므로 {@link #INVALID_POSITION} 이다). 옛 방식의 코드 번호로 남아 있다 — 지울지는 소유자가 정한다
     */
    POSITION_TAKEN(-8);

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
