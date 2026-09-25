package com.queuemate.platform.room.redisKeys;

/**
 * 방의 Redis 키 — 방장 키 · 멤버 SET · 확정 표시 키와 입장 표시 키. <b>방을 바꾸려면 Lua 스크립트를 부르는 서비스({@code RoomService} ·
 * {@code RoomMemberService})를 거친다</b> — 정원 검사 · 입장 표시 키 · 활성 요청 키의 {@code EXISTS} 가 한 스크립트 안에 있어서, 맨손으로
 * {@code SADD} 하면 그 불변식이 깨진다.
 */
public final class RoomKeys {

    private static final String prefix = "qm:room:";

    /**
     * 입장 표시 키 접두사. <b>{@code matching} 과의 약속이다(docs/11 D-19)</b> — 이 키는 platform 이 쓰고 {@code matching} 은 {@code EXISTS} 로만 본다.
     * {@code matching} 의 {@code claim-request.lua} 가 이 키를 보고 방에 있는 사람의 매칭 요청을 거절하므로 그쪽이 같은 글자를 따라 적었다
     * ({@code matching} 의 {@code redisKeys/SharedKeys.ACTIVE_ROOM_PREFIX}). 혼자 바꾸면 그 검사가 조용히 무력화된다 — {@code SharedPrefixTest} 가 본다.
     * <pre>{@code "qm:user:active-room:"  →  qm:user:active-room:42}</pre>
     */
    public static final String ACTIVE_ROOM_PREFIX = "qm:user:active-room:";

    private RoomKeys()
    {
    }

    /**
     * 멤버 SET. 원소는 방에 있는 사람의 {@code userId}(사용자 번호의 십진 문자열 — 방장 포함)이고 크기가 현재 인원이다.
     * <pre>{@code qm:room:123:members}</pre>
     */
    public static String roomMemberKey(String roomId)
    {
        return prefix + roomId + ":members";
    }

    /**
     * 방장 키. STRING 이고 값은 방장의 {@code userId} 다. <b>이 키가 있다 = 방이 있다.</b>
     * 방을 만들 때 쓰고({@code create-room.lua}), 방장이 나가면 방과 함께 지운다. 확정한 방에서는 값이 바뀔 수 있다(docs/11 D-23).
     * <pre>{@code qm:room:123:host}</pre>
     */
    public static String roomHostKey(String roomId)
    {
        return prefix + roomId + ":host";
    }

    /**
     * 사용자의 입장 표시. STRING 이고 값은 {@code roomId} 다. 방에 들어올 때 쓰고, 나갈 때
     * <b>값이 그 방의 {@code roomId} 와 같을 때만</b> 지운다 — 늦게 온 나가기가 다른 방의 표시를 지우면 안 된다.
     * <pre>{@code qm:user:active-room:42}</pre>
     */
    public static String activeRoomKey(String userId)
    {
        return ACTIVE_ROOM_PREFIX + userId;
    }

    /**
     * 확정 표시 키. STRING 이고 값은 {@code roomId} 다. <b>이 키가 있다 = 방장이 확정한 방이다</b> — 새 사람이 못 들어온다.
     * 방장이 확정할 때 쓰고({@code confirm-room.lua}), 방장의 접속 확인이 수명을 늘리고, 방이 없어질 때 같이 지운다.
     * 게시판이 글의 상태를 "확정"으로 기록할 때도 이 키를 본다({@code party.room.RedisRoomStateReader}).
     * <pre>{@code qm:room:123:confirmed}</pre>
     */
    public static String roomConfirmedKey(String roomId)
    {
        return prefix + roomId + ":confirmed";
    }
}
