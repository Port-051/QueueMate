package com.queuemate.platform.room.redisKeys;

/**
 * 방의 Redis 키 — 방장 키 · 멤버 HASH · 찾는 포지션 SET · 확정 표시 키와 입장 표시 키. <b>방을 바꾸려면 Lua 스크립트를 부르는 서비스({@code RoomService} ·
 * {@code RoomMemberService})를 거친다</b> — 정원 검사 · 포지션 검사 · 입장 표시 키 · 활성 요청 키의 {@code EXISTS} 가 한 스크립트 안에 있어서, 맨손으로
 * {@code HSET} 하면 그 불변식이 깨진다.
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
     * 멤버 HASH(2026-09-30 소유자 결정 — P-44. 그 전에는 SET 이었다). <b>필드는 방에 있는 사람의 {@code userId}</b>(사용자 번호의 십진 문자열 — 방장 포함)이고
     * 필드 수({@code HLEN})가 현재 인원이다. <b>값은 참가할 때 고른 포지션</b>이다 — 방장의 값은 글의 방장 포지션({@code host_position} — 글은 고칠 수 없다,
     * 2026-10-01 소유자 결정), 고르지 않았으면 빈 문자열(포지션이 없는 모드 · 자동 매칭 파티의 방). 들어온 뒤에는 바꿀 수 없고,
     * 나가거나 강퇴되면 필드째 빠지고 그 포지션은 남은 찾는 포지션 SET({@link #roomNeedsKey})에 돌아간다.
     * <pre>{@code qm:room:123:members  →  { "42": "MID", "43": "JUNGLE" }}</pre>
     */
    public static String roomMemberKey(String roomId)
    {
        return prefix + roomId + ":members";
    }

    /**
     * <b>남은</b> 찾는 포지션 SET(2026-09-30 소유자 결정 — P-44). 글 쓰기가 방을 만들 때 글의 {@code wantedPositions} 로 채우고({@code create-room.lua} — 포지션이 없는 모드면 만들지 않는다),
     * 입장이 고른 포지션을 뺀다({@code enter-room.lua} — {@code SREM}). 나가기 · 강퇴가 그 사람의 포지션을 돌려놓고({@code leave-room.lua} · {@code kick-room.lua}),
     * 글은 고칠 수 없어(2026-10-01 소유자 결정) 그 밖에 SET 을 바꾸는 길은 없다. 방장의 접속 확인이 수명을 늘리고, 방이 없어질 때 같이 지운다.
     * 다 고르면 Redis 가 빈 SET 을 지워 키가 없어진다 — 그래서 <b>포지션 방인가는 이 키가 있는지로 가르지 않는다</b>. 입장은 글의 찾는 포지션이 비지 않았는지를
     * {@code PostEntryGate#check} 에서 받아 스크립트에 넘긴다(2026-10-01 소유자 결정).
     * <pre>{@code qm:room:123:needs}</pre>
     */
    public static String roomNeedsKey(String roomId)
    {
        return prefix + roomId + ":needs";
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
     * 게시판이 글의 상태를 "확정"으로 기록할 때도 이 키를 본다({@code RoomService#states}).
     * <pre>{@code qm:room:123:confirmed}</pre>
     */
    public static String roomConfirmedKey(String roomId)
    {
        return prefix + roomId + ":confirmed";
    }

    /**
     * 입장 금지 목록. ZSET 이고 <b>원소는 {@code roomId}, score 는 금지가 풀리는 시각(epoch ms)</b> 이다 — 한 사람이 여러 방에서 강퇴당할 수 있어 사용자별 키 하나에 방을 모은다.
     * <b>강퇴가 쓴다</b>(2026-09-29 소유자 결정 — 강퇴당한 사람은 그 방에 10분 동안 직접 들어올 수 없다). 입장 스크립트({@code enter-room.lua})가 그 방의 score 를 읽어
     * 아직 안 지났으면 거절한다. 스스로 나간 사람은 여기 들지 않는다 — 직접 입장은 막지 않는다.
     * <pre>{@code qm:room:no-entry:42}</pre>
     */
    public static String noEntryKey(String userId)
    {
        return prefix + "no-entry:" + userId;
    }

    /**
     * 자동 합류 건너뛰기 목록. 모양은 {@link #noEntryKey} 와 같다(ZSET · 원소 {@code roomId} · score 는 풀리는 시각). <b>강퇴와 나가기 둘 다 쓴다</b> —
     * 강퇴당했든 스스로 나갔든 그 방은 10분 동안 게시판 방 먼저 합류({@code POST /api/v1/posts/auto-join} · P-28)의 후보에서 뺀다.
     * 읽는 곳은 이 앱의 {@code party.service.AutoJoinService} 다 — {@code matching} 앱과는 무관하다(그쪽의 "거절한 상대 회피" D-45 는 사람 단위, 이것은 방 단위다).
     * <pre>{@code qm:room:no-auto-join:42}</pre>
     */
    public static String noAutoJoinKey(String userId)
    {
        return prefix + "no-auto-join:" + userId;
    }
}
