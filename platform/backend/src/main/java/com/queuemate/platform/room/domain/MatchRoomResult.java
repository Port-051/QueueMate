package com.queuemate.platform.room.domain;

/**
 * 자동 매칭 파티의 방에 "없으면 만들고 있으면 들어가기"({@code RoomService#enterMatchRoom})의 결과. 자동 매칭 파티의 요청을 받는
 * {@code party.controller.MatchPartyController} 가 이 값으로 응답 코드를 정한다(2026-09-27 소유자 결정 — docs/11 D-42).
 *
 * <p>{@link #code} 는 {@code lua/enter-match-room.lua} 의 반환값과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다. 뜻이 같은 값은 {@link EnterResult} 와 번호를 맞췄다.
 */
public enum MatchRoomResult {

    /** 방이 없어서 만들고 들어왔다. 내가 방장이고 방은 태어날 때부터 확정된 방이다(확정 표시 키가 같이 선다) */
    CREATED(1),

    /** 이미 이 방에 들어와 있다. 새로고침이나 재시도다 — 아무것도 바뀌지 않았다. 스크립트가 파티 HASH 보다 먼저 보므로 HASH 가 수명으로 사라진 뒤에도 이 값이다 */
    ALREADY_IN_ROOM(2),

    /** 있던 방에 들어왔다. 뒤에 "내가 들어오기 전부터 있던 사람들"이 붙어 온다 — 서비스가 그 사람들에게 알린다 */
    ENTERED(3),

    /** 방이 가득 찼다. 정원은 파티 HASH 의 {@code target} 이다 — 파티원이 아닌 사람은 먼저 걸리므로 정상이면 나지 않는다 */
    ROOM_FULL(-2),

    /** 다른 방에 들어가 있다. 입장 표시 키의 값이 이 방의 {@code roomId} 가 아니다 — 이것도 파티 HASH 보다 먼저 본다 */
    IN_OTHER_ROOM(-3),

    /** 확정된 파티가 없다 — 아직 제안 중이거나, 수명(600초)이 다해 사라졌거나, 그런 파티가 없다 */
    PARTY_NOT_FOUND(-4),

    /** 이 파티의 파티원이 아니다 — 파티 HASH 에 {@code member:{userId}} 필드가 없다 */
    NOT_PARTY_MEMBER(-6);

    private final long code;

    MatchRoomResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 입장으로 읽지 않는다.
     */
    public static MatchRoomResult fromCode(Long code)
    {
        for (MatchRoomResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("enter-match-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
