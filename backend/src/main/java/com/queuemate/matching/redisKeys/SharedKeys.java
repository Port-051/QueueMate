package com.queuemate.matching.redisKeys;

import com.queuemate.matching.domain.GameKey;

/**
 * 이 앱이 쓰는 Redis 키 문자열을 한 자리에 모은다.
 *
 * <h2>왜 한 자리로 모으는가</h2>
 * <p>같은 접두사를 여러 클래스가 각자 적고 있으면 <b>한쪽만 고쳐도 컴파일은 통과한다.</b>
 * 그때부터 서로 다른 키를 만들고, 아무도 못 찾는 데이터가 조용히 쌓인다. 실제로
 * {@code ProposalService} / {@code ProposalExpiryService} 는 게임과 무관한데도
 * {@code LolPartyKeys.PARTY_PREFIX} 를 빌려 쓰거나 같은 문자열을 따로 적고 있었다.
 *
 * <h2>무엇이 공용이고 무엇이 게임별인가</h2>
 * <p><b>파티 · 제안 · 활성 요청 · 락 · 알림 채널은 게임을 가리지 않는다.</b>
 * <ul>
 *   <li>파티 HASH({@code qm:party:{partyId}}) — 담기는 값은 게임마다 다르지만 키 자체는
 *       게임을 모른다. 제안 상태({@code status}/{@code expiresAt})가 이 HASH 에 얹히는
 *       이상(= proposal 이 곧 party, CLAUDE.md §1) 게임별로 나뉠 수 없다.
 *   <li>수락자 SET({@code qm:proposal:accepts:{partyId}})과 만료 대기 목록
 *       ({@code qm:proposal:pending}) — {@code redis/proposal/*.lua} 가 게임을 보지 않는
 *       것과 같은 이유다. 제안은 파티 HASH 위에서만 돌아가고 조건을 읽지 않는다.
 *   <li>활성 요청({@code qm:user:active-request:{userId}}) — INV-1 은 <b>사용자 단위</b>다.
 *       게임별로 나누면 배그를 하다 롤 큐를 또 잡을 수 있게 되어 INV-1 이 깨진다
 *       ({@code redis/shared/claim-request.lua} 가 {@code shared/} 에 있는 것과 같은 판단).
 *   <li>후보 풀 락({@code qm:lock:pool:*})과 푸시 채널({@code qm:pubsub:push:{userId}}).
 * </ul>
 *
 * <p><b>게임을 가리는 것은 needs 색인과 gameconfig 뿐이다.</b> 그 둘은 조건이 키 이름에
 * 들어가고(INV-8) 게임마다 격자 모양이 다르므로, <b>조각만 여기 두고 조립은 게임별</b>
 * {@code Lol/Pubg/ValorantPartyKeys} 가 한다. 게임 이름과 조건을 엮는 일은 그쪽 몫이다.
 *
 * <h2>같은 문자열이 Lua 안에도 있다</h2>
 * <p>아래 값을 고치면 <b>다음 스크립트의 리터럴도 함께 고쳐야 한다.</b> 컴파일러가 맞춰 주지
 * 않는 짝이고, 어긋나면 한쪽이 만든 키를 다른 쪽이 영영 못 찾는다.
 * <ul>
 *   <li>{@link #PENDING_KEY} — {@code lol,pubg,valorant}/{@code join-party.lua} ·
 *       {@code join-party-tiered.lua}(ZADD), {@code leave-party.lua}(ZREM),
 *       {@code proposal/accept-proposal.lua} · {@code decline-proposal.lua} ·
 *       {@code expiry-proposal.lua}
 *   <li>{@link #ACCEPTS_PREFIX} — {@code lol,pubg,valorant}/{@code leave-party.lua}
 *       (KEYS 로 받지 않고 스스로 조립한다)
 *   <li>{@link #ACTIVE_REQUEST_PREFIX} — {@code proposal/expiry-proposal.lua}
 *       (무응답자의 requestId 를 읽는다)
 *   <li>{@link #NEEDS_ROLES_PREFIX} — VALORANT 전용. {@code valorant/create-or-check-party-untiered.lua} ·
 *       {@code create-or-check-party-tiered.lua} · {@code join-party.lua} ·
 *       {@code join-party-tiered.lua} · {@code leave-party.lua}
 * </ul>
 * <p>{@link #PARTY_PREFIX} 는 예외다 — 배정 스크립트가 ARGV 로 받아 가므로 자바 쪽만 고치면 된다.
 *
 * <p>인스턴스가 필요 없어 {@code @Component} 가 아니라 정적 유틸리티다. 게임별
 * {@code *PartyKeys} 는 요청 DTO/활성 요청에서 조건을 읽어 조립하므로 빈이지만,
 * 이쪽이 받는 것은 id 나 이미 만들어진 키 조각뿐이다.
 */
public final class SharedKeys {

    /**
     * 키 토큰 구분자. 티어 접미사도 이것으로 붙인다.
     * <pre>{@code ":"}</pre>
     */
    public static final String SEPARATOR = ":";

    // ── 파티 ────────────────────────────────────────────────────────────────

    /**
     * 파티 HASH 키 접두사. 배정 Lua 가 기존 파티 키를 조립할 때 ARGV 로도 받아 간다.
     * <pre>{@code "qm:party:"        →  qm:party:3f9a...-uuid}</pre>
     */
    public static final String PARTY_PREFIX = "qm:party:";

    /**
     * 아직 안 찬 파티 색인의 접두사. 뒤에 게임 · 모드 · 음성 · 목적이 붙어 후보 풀이 된다.
     * <pre>{@code "qm:party:open:"   →  qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP}</pre>
     */
    public static final String PARTY_OPEN_PREFIX = PARTY_PREFIX + "open:";

    /**
     * 후보 풀 뒤에 붙는 needs 토큰. 뒤에 keyValue 가 더 붙으면 needs 한 줄이 된다.
     * <pre>{@code ":needs"           →  ...:RANK_UP:needs:TOP}</pre>
     */
    public static final String NEEDS_INFIX = ":needs";

    /**
     * 아직 빈 역할군 SET 접두사. <b>VALORANT 전용</b>이고 지금은 Lua 만 만진다 —
     * 범위를 좁힌 뒤 어느 칸을 다시 만들지가 이 목록이다.
     * <pre>{@code "qm:party:needs-roles:"  →  qm:party:needs-roles:3f9a...-uuid}</pre>
     */
    public static final String NEEDS_ROLES_PREFIX = PARTY_PREFIX + "needs-roles:";

    // ── 제안 ────────────────────────────────────────────────────────────────

    /**
     * 수락자 SET 접두사. 제안 상태(status/expiresAt)는 파티 HASH 에 있으므로
     * 제안이 따로 쓰는 키는 이것과 아래 만료 대기 목록 둘뿐이다.
     * <pre>{@code "qm:proposal:accepts:"   →  qm:proposal:accepts:3f9a...-uuid}</pre>
     */
    public static final String ACCEPTS_PREFIX = "qm:proposal:accepts:";

    /**
     * 진행 중인 제안 목록. ZSET, member = partyId, score = expiresAt.
     *
     * <p>넣는 자리는 합류 스크립트의 {@code HSETNX status 'PENDING'} 성공 분기 <b>안</b>
     * 하나뿐이고, 빼는 자리는 제안이 끝나는 모든 곳이다(수락 확정 · 거절 · 취소 · 만료).
     * 한 군데라도 빠뜨리면 스위퍼가 이미 끝난 제안을 주기마다 영원히 다시 꺼낸다.
     * <pre>{@code "qm:proposal:pending"    (접두사가 아니라 키 하나다)}</pre>
     */
    public static final String PENDING_KEY = "qm:proposal:pending";

    // ── 사용자 ──────────────────────────────────────────────────────────────

    /**
     * 활성 요청 HASH 접두사. INV-1 의 선점 자리이자 키를 되조립할 재료가 담긴다.
     * <pre>{@code "qm:user:active-request:"  →  qm:user:active-request:u123}</pre>
     */
    public static final String ACTIVE_REQUEST_PREFIX = "qm:user:active-request:";

    /**
     * 푸시 알림 채널 접두사. 배달은 app:realtime 이 한다 (CLAUDE.md §3).
     * <pre>{@code "qm:pubsub:push:"  →  qm:pubsub:push:u123}</pre>
     */
    public static final String PUSH_CHANNEL_PREFIX = "qm:pubsub:push:";

    // ── 설정 · 락 ───────────────────────────────────────────────────────────

    /**
     * 게임 모드 설정 접두사. 원본은 seed/gameconfig.redis 이고 앱은 읽기만 한다.
     * <pre>{@code "qm:gameconfig:"   →  qm:gameconfig:LOL:RANKED_SOLO}</pre>
     */
    public static final String GAMECONFIG_PREFIX = "qm:gameconfig:";

    /**
     * 티어 사다리 ZSET 의 꼬리. score 가 단계 번호다.
     * <pre>{@code ":tier"            →  qm:gameconfig:LOL:tier}</pre>
     */
    public static final String TIER_SUFFIX = ":tier";

    /**
     * 모드별 티어 범위 표의 중간 토큰. HASH, 필드 = 티어 이름.
     * <pre>{@code ":tier-range:"     →  qm:gameconfig:LOL:tier-range:RANKED_SOLO}</pre>
     */
    public static final String TIER_RANGE_INFIX = ":tier-range:";

    /**
     * 후보 풀 락 접두사. Redisson 이 이 아래만 만진다 (PoolLock 참고).
     * <pre>{@code "qm:lock:pool:"    →  qm:lock:pool:qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP}</pre>
     */
    public static final String POOL_LOCK_PREFIX = "qm:lock:pool:";

    // ── 게임을 가리지 않는 키 ───────────────────────────────────────────────

    /**
     * 파티 HASH. 참가자({@code member:{userId}})와 제안 상태를 함께 담는다.
     * <pre>{@code qm:party:3f9a1c7e-...}</pre>
     */
    public static String partyKey(String partyId) {
        return PARTY_PREFIX + partyId;
    }

    /**
     * 그 제안을 수락한 사용자들. INV-4 의 SCARD 대상이다.
     * <pre>{@code qm:proposal:accepts:3f9a1c7e-...}</pre>
     */
    public static String acceptsKey(String proposalId) {
        return ACCEPTS_PREFIX + proposalId;
    }

    /**
     * 사용자의 활성 요청. 요청 내용도 여기 함께 담긴다.
     * <pre>{@code qm:user:active-request:u123}</pre>
     */
    public static String activeRequestKey(String userId) {
        return ACTIVE_REQUEST_PREFIX + userId;
    }

    /**
     * 아직 빈 역할군 SET (VALORANT 전용). 자바에는 아직 호출부가 없고 Lua 가 조립한다.
     * <pre>{@code qm:party:needs-roles:3f9a1c7e-...}</pre>
     */
    public static String needsRolesKey(String partyId) {
        return NEEDS_ROLES_PREFIX + partyId;
    }

    /**
     * 후보 풀 하나를 잡는 락. 인자는 게임별 {@code poolKey()} 가 만든 값이다.
     * <pre>{@code qm:lock:pool:qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP}</pre>
     */
    public static String poolLockKey(String poolKey) {
        return POOL_LOCK_PREFIX + poolKey;
    }

    /**
     * 한 사용자의 알림 채널.
     * <pre>{@code qm:pubsub:push:u123}</pre>
     */
    public static String pushChannel(String userId) {
        return PUSH_CHANNEL_PREFIX + userId;
    }

    // ── 게임별 조립의 재료 ──────────────────────────────────────────────────
    // 아래는 게임 이름을 받는다. 호출부는 게임별 *PartyKeys 와 조건 검증기다.

    /**
     * 잠글 후보 풀. needs 키에서 keyValue(포지션/역할군/플랫폼)만 뺀 조합이다.
     *
     * <p>락 키에 keyValue 를 넣으면 안 되는 이유는 {@code PoolLock} 클래스 주석에 있다.
     * <pre>{@code qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP}</pre>
     */
    public static String poolKey(GameKey game, String modeKey, String voice, String purpose) {
        return PARTY_OPEN_PREFIX + game.name() + SEPARATOR + modeKey
                + SEPARATOR + voice + SEPARATOR + purpose;
    }

    /**
     * 역할군도 티어도 없는 needs <b>밑동</b>. VALORANT 의 티어 스크립트가 이것을 받아
     * {@code 밑동 + ':' + 역할군 + ':' + 티어} 로 칸을 다시 조립한다.
     * <pre>{@code qm:party:open:VALORANT:COMPETITIVE_TRIO:REQUIRED:RANK_UP:needs}</pre>
     */
    public static String needsBaseKey(String poolKey) {
        return poolKey + NEEDS_INFIX;
    }

    /**
     * 그 keyValue 를 아직 못 채운 파티 목록. <b>티어 접미사는 없다.</b>
     * <pre>{@code qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs:TOP}</pre>
     */
    public static String needsKey(String poolKey, String keyValue) {
        return needsBaseKey(poolKey) + SEPARATOR + keyValue;
    }

    /**
     * 티어 격자의 한 칸. <b>자바에서 칸을 셀 때만 쓴다</b> — 스크립트에는 접미사 없는 키를
     * 넘기고 Lua 가 같은 규칙으로 붙인다. 규칙이 어긋나면 세는 칸과 실제 칸이 달라진다.
     * <pre>{@code qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs:TOP:GOLD_2}</pre>
     */
    public static String withTier(String needsKey, String tierName) {
        return needsKey + SEPARATOR + tierName;
    }

    /**
     * 게임 모드 설정 HASH. 필드는 {@code targetPartySize} / {@code tierRule} 등이다.
     * <pre>{@code qm:gameconfig:LOL:RANKED_SOLO}</pre>
     */
    public static String gameConfigKey(GameKey game, String modeKey) {
        return GAMECONFIG_PREFIX + game.name() + SEPARATOR + modeKey;
    }

    /**
     * 티어 사다리 ZSET. 값의 원본은 자바가 아니라 Redis 다 (CLAUDE.md §2).
     * <pre>{@code qm:gameconfig:LOL:tier}</pre>
     */
    public static String tierKey(GameKey game) {
        return GAMECONFIG_PREFIX + game.name() + TIER_SUFFIX;
    }

    /**
     * 모드별 티어 범위 표 HASH. 값은 {@code "최저:최고"} 또는 {@code SOLO_ONLY}.
     * <pre>{@code qm:gameconfig:LOL:tier-range:RANKED_SOLO}</pre>
     */
    public static String tierRangeKey(GameKey game, String modeKey) {
        return GAMECONFIG_PREFIX + game.name() + TIER_RANGE_INFIX + modeKey;
    }

    private SharedKeys() {
    }
}
