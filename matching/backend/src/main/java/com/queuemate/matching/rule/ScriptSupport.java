package com.queuemate.matching.rule;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Set;

/**
 * 게임별 배정·취소 코드가 Lua 스크립트를 부르고 결과를 읽을 때 함께 쓰는 조각들. <b>게임과 무관하다.</b>
 *
 * <p>원래 {@code rule/lol/LolScriptSupport} 였는데, 여기 있는 것이 전부 LoL 에 묶이지 않는다는 것이
 * 드러나 공통으로 끌어올렸다 — 스크립트를 부르고 첫 칸을 코드로 읽는 것({@link #execute} / {@link #code}),
 * 파티 HASH 의 {@code member:} 필드 읽기({@link #memberIds}), 최근 거절 판정({@link #declinedWith}),
 * 후보 훑기 상한({@link #MAX_CANDIDATE_SCAN}). PUBG 등 다른 게임도 이것을 쓴다.
 *
 * <p><b>차단(INV-6)은 여기서 보지 않는다</b> (docs/11 D-57, 2026-10-02). 합류 스크립트({@code join-party*.lua})가
 * 들어오려는 사람의 차단 집합 {@code qm:user:block-rel:{userId}} 를 KEYS 의 마지막으로 받아 파티원마다
 * {@code SISMEMBER} 하고, 걸리면 아무것도 쓰지 않고 {@link #BLOCKED} 를 돌려준다. 그 전에는 여기 있던
 * {@code blockedWith} 가 DB 에서 읽은 차단 목록으로 후보를 걸렀다 — 확인과 합류가 두 실행으로 나뉘어 있었다.
 *
 * <p><b>그래서 게임별 스크립트는 같은 약속을 지켜야 한다.</b> 참가자 필드는 {@code member:{userId}},
 * 반환은 {@code {code, ...}} 모양, 코드 값은 아래 상수와 같다. 한 게임만 다르게 하면 이 클래스를
 * 쓰는 순간 조용히 틀린다.
 *
 * <p><b>공통 상위 타입 대신 유틸로 둔 이유.</b> 여기 있는 것은 스크립트 결과를 읽는
 * 순수 함수와 상수뿐이고 호출부가 물려받을 상태가 없다. 상위 클래스로 묶으면
 * 상속 필드 때문에 호출부가 생성자를 손으로 써야 하는데(Lombok 의
 * {@code @RequiredArgsConstructor} 는 상위 필드를 채우지 않는다) 얻는 것보다 잃는 게 크다.
 *
 * <p>배정의 <b>흐름</b>은 여기로 끌어올리지 않는다. 색인 모양(1차원 vs 격자)과
 * 인자 배치가 게임·모드마다 달라서, 하나로 묶으면 그 차이가 훅으로 흩어져 읽기 어려워진다.
 */
public final class ScriptSupport {

    /** 파티 HASH 의 참가자 필드 접두사. {@code member:{userId}} 형태다 */
    private static final String MEMBER_FIELD_PREFIX = "member:";

    /**
     * 한 요청이 훑어볼 후보 파티 수의 상한. 배정 전용이다.
     *
     * <p>차단(합류 Lua 의 {@link #BLOCKED})이나 최근 거절 상대가 계속 걸리면 색인 끝까지 훑게 되는데 그동안 풀 전체가 잠겨 있다.
     * 여기서 끊고 새 파티를 만드는 편이 낫다 — 매칭이 조금 늦어질 뿐 실패하지는 않는다.
     *
     * <p><b>비용은 스크립트 실행이 아니라 왕복이다.</b> 후보 하나를 볼 때마다 스크립트를
     * 새로 부르므로 상한만큼 왕복이 일어나고, 그 전부가 {@link com.queuemate.matching.redisLock.PoolLock}
     * 의 후보 풀 락 안이다. 같은 조건의 다른 사용자는 그동안 락을 기다린다.
     * 락 유지 시간이 3초이므로 상한이 커질수록 그 한도에 가까워진다.
     */
    public static final int MAX_CANDIDATE_SCAN = 20;

    /** create-or-check-party-*.lua 반환 코드 */
    public static final long CREATED_NEW_PARTY = 1;
    public static final long CANDIDATE_FOUND = 2;

    /** join-party*.lua 반환 코드. 들어갔고 정원까지 찼다는 뜻이다 */
    public static final long JOINED_AND_FULL = 2;

    /**
     * join-party*.lua 반환 코드. 파티원 가운데 나와 차단 관계인 사람이 있어 <b>들어가지 않았다</b> (INV-6, docs/11 D-57).
     * 스크립트는 아무것도 쓰지 않았다 — 그 파티는 색인에 그대로 있으니 호출부는 다음 후보(start + 1)를 본다.
     * 세 게임의 합류 스크립트 6개가 같은 값을 쓴다. {@code -1}(설정과 안 맞음) · {@code -2}(claim 만료)와 달리
     * 배정을 끝내는 값이 아니다.
     */
    public static final long BLOCKED = -3;

    private ScriptSupport() {
    }

    /**
     * 스크립트가 돌려준 첫 칸. 여기 있는 스크립트는 전부 {@code {code, ...}} 모양이다.
     *
     * <p>Redis 는 정수를 {@code Long} 으로 준다. {@code (int) result.getFirst()} 처럼
     * 바로 int 로 캐스팅하면 {@code Integer} 로 언박싱하려다 ClassCastException 이 난다.
     */
    public static long code(List<Object> result) {
        return ((Number) result.get(0)).longValue();
    }

    public static  List<String> memberIds(List<Object> candidate)
    {
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) candidate.get(1);
        return fields.stream()
                .filter(field -> field.startsWith(MEMBER_FIELD_PREFIX))
                // userId 는 요청 바디로 받는 자유 문자열이라 콜론이 들어갈 수 있다.
                // split(":") 으로 자르면 앞부분만 남는다.
                .map(field -> field.substring(MEMBER_FIELD_PREFIX.length()))
                .toList();
    }

    /**
     * 후보 파티에 내가 최근 거절했거나 나를 거절한 사람이 있나 (docs/11 D-45 — 불변식이 아니다). 배정 전용이다.
     *
     * <p>{@code declinedUserIds} 는 호출부가 락 밖에서 {@code qm:user:declined:{나}} 를 한 번 읽은 것이다.
     * 차단과 달리 Lua 로 옮기지 않았다 — 풀리는 시각(score)을 지금 시각과 견줘야 하고, 놓쳐도 깨지는 불변식이 없다.
     */
    public static boolean declinedWith(List<String> memberIds, Set<String> declinedUserIds) {
        if (declinedUserIds.isEmpty()) {
            return false;
        }
        return memberIds.stream()
                .anyMatch(declinedUserIds::contains);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<Object> execute(StringRedisTemplate redis, RedisScript<List> script,
                                List<String> keys, List<String> args) {
        return redis.execute(script, keys, args.toArray());
    }
}
