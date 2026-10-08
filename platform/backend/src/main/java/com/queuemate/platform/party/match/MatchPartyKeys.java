package com.queuemate.platform.party.match;

/**
 * {@code matching} 이 주인인 <b>확정된 파티 HASH</b>의 Redis 키. <b>이 앱이 정하는 값이 아니다</b> — 원본은 {@code matching} 의
 * {@code redisKeys/SharedKeys.PARTY_PREFIX} 다({@code SharedPrefixTest} 가 옆 폴더의 원본과 글자를 비교한다).
 *
 * <p><b>이 앱은 이 키를 읽기만 한다</b>(2026-09-27 소유자 결정 — docs/11 D-42). {@code matching} 은 확정 뒤 SQS 를 보내지 않고 이 HASH 를
 * 600초 남겨 두며, 이 앱은 프런트가 {@code MATCH_CONFIRMED {partyId}} 를 받고 부르는 요청({@code POST /api/v1/match-parties/{partyId}/room})에서
 * 그것을 {@code HGETALL}(자바 — {@link MatchPartyReader}) · {@code HGET} / {@code HEXISTS}(Lua — {@code lua/enter-match-room.lua})로 읽어 파티 · 방을 만든다.
 * <b>쓰지도 · 지우지도 · 수명을 걸지도 않는다</b> — gameconfig 를 읽는 것(D-29) · 활성 요청 키를 {@code EXISTS} 로 보는 것(D-19)과 같은 방식이다.
 *
 * <p><b>필드 이름도 앱 사이의 계약이다</b>(원본은 {@code matching} 의 {@code proposal/cleanup-confirmed.lua} 머리) —
 * {@code status}({@code CONFIRMED}) · {@code confirmedAt}(epoch millis) · {@code game} · {@code modeKey} · {@code voicePreference} · {@code playPurpose} ·
 * {@code target}(정원) · {@code member:{userId}}(값은 keyValue) · 티어 모드의 {@code tierLo} / {@code tierHi}. 이름이 어긋나도 컴파일 · 테스트는 통과하고
 * 이 앱이 파티를 못 만들 뿐이다 — 바꾸려면 {@code matching} 과 같이 바꾼다.
 *
 * <p>{@code room.redisKeys.SharedKeys}(활성 요청 키)가 아니라 여기 있는 이유 — 읽는 쪽이 {@code party} 다. 방의 Lua 도 이 키를 KEYS 로 받지만 그 키는
 * {@code room.service.RoomService} 가 이 클래스로 조립해 넘긴다(빈이 아니라 상수라 빈 순환과 무관하다).
 */
public final class MatchPartyKeys {

    /**
     * 파티 HASH 접두사. 원본은 {@code matching} 의 {@code SharedKeys.PARTY_PREFIX} 다.
     * <pre>{@code "qm:party:"  →  qm:party:3f9a1c7e-...}</pre>
     */
    public static final String PARTY_PREFIX = "qm:party:";

    /** 파티원 필드의 접두사 — {@code member:{userId}} 의 앞부분. 값은 그 사람의 keyValue(포지션 · 역할군 · 플랫폼)다 */
    public static final String MEMBER_FIELD_PREFIX = "member:";

    private MatchPartyKeys()
    {
    }

    /**
     * 확정된 파티 HASH. {@code partyId} 는 {@code matching} 이 매긴 UUID 문자열이다 — 이 앱에서는 그 파티의 방 번호({@code roomId})이기도 하다.
     * <pre>{@code qm:party:3f9a1c7e-...}</pre>
     */
    public static String partyKey(String partyId)
    {
        return PARTY_PREFIX + partyId;
    }

    /** 파티원 한 명의 필드 이름. Lua 도 같은 규칙({@code 'member:' .. userId})으로 조립한다 */
    public static String memberField(String userId)
    {
        return MEMBER_FIELD_PREFIX + userId;
    }
}
