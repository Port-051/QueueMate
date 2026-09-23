package com.queuemate.platform.party.room;

/**
 * 이 앱이 <b>읽는</b> {@code room} 의 Redis 키. <b>원본은 {@code room} 의 {@code redisKeys/RoomKeys.java} 다</b> — 여기는 그 값을 따라 적은 것이다
 * (계약은 {@code room} 의 {@code contracts/room-api.md} "Redis 키", 이 폴더의 사본은 {@code docs/ROOM_CONTRACT.md}).
 *
 * <p><b>이름과 구조는 두 앱의 약속이다.</b> 어긋나도 컴파일 · 테스트는 통과하고 목록만 조용히 틀린다(방이 전부 "없는 방"으로 보인다) —
 * 그래서 접두사를 이 한 곳에만 둔다. 바꿀 때는 {@code room} 과 같이 바꾼다 (CLAUDE.md §3.3).
 *
 * <p><b>이 앱은 이 키들에 절대 쓰지 않는다</b> — 확정 표시도 {@code room} 이 쓴다(docs/11 D-21). 입장 표시 키({@code qm:user:active-room:*})와
 * {@code matching} 의 키({@code qm:user:*} · {@code qm:party:*} 등)는 읽지도 않는다 — 그래서 여기에 그 이름이 없다(D-19).
 */
public final class RoomKeys {

    /** {@code roomId} 는 글의 id(bigint)다 — 키에는 그 숫자를 십진 문자열로 적는다. 브라우저가 {@code room} 에 주는 {@code roomId} 와 같은 글자다 */
    private static final String PREFIX = "qm:room:";

    private RoomKeys()
    {
    }

    /** 방장 키(STRING, 값은 방장의 {@code userId}). <b>이 키가 있다 = 방이 있다.</b> 확정한 방에서는 값이 바뀔 수 있다(D-23) — 그래서 값은 읽지 않고 {@code EXISTS} 만 한다 */
    public static String host(Long roomId)
    {
        return PREFIX + roomId + ":host";
    }

    /** 멤버 SET(원소는 방장을 포함한 {@code userId}). {@code SMEMBERS} 로 읽어 프로필을 붙이고 차단을 대조한다(D-20). 유령이 잠깐 남을 수 있다 */
    public static String members(Long roomId)
    {
        return PREFIX + roomId + ":members";
    }

    /** 확정 표시 키(STRING). <b>이 키가 있다 = 방장이 확정한 방이다</b>(D-21). {@code EXISTS} 만 한다 */
    public static String confirmed(Long roomId)
    {
        return PREFIX + roomId + ":confirmed";
    }
}
