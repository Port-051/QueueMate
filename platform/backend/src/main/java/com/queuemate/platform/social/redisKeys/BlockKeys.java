package com.queuemate.platform.social.redisKeys;

/**
 * 차단 관계 사본의 Redis 키 — <b>이 파일이 원본이다</b>(2026-10-02 소유자 결정 · docs/11 D-57 · {@code contracts/platform-api.md} P-52).
 *
 * <p>{@code qm:user:block-rel:{userId}} 는 SET 이고 member 는 그 사용자와 <b>어느 방향으로든</b> 차단 관계인 사용자 번호의 십진 문자열이다 —
 * A 가 B 를 차단하면 A 의 집합에 {@code "B"}, B 의 집합에 {@code "A"} 가 같이 들어간다(대칭). 수명은 없다.
 * <b>쓰는 앱은 이 앱뿐이다</b>({@code social.service.BlockRelationRedis} — 차단 · 해제 · 회원 탈퇴 · 재구성). {@code matching} 은 합류 스크립트에서
 * {@code SISMEMBER} 로 읽기만 한다(INV-6 — 그래서 {@code matching} 이 DB 를 읽지 않는다). 원본은 DB 의 {@code blocks} 표이고 이 집합은 사본이다 — 어긋나면 DB 가 맞다.
 *
 * <p><b>{@code matching} 과의 약속이다</b> — 그쪽 {@code redisKeys/SharedKeys.BLOCK_REL_PREFIX} 가 같은 글자를 따라 적었다. 혼자 바꾸면 컴파일도 테스트도
 * 통과한 채 {@code matching} 이 늘 "차단 없음" 을 보고 차단한 사람끼리 파티를 만든다 — {@code SharedPrefixTest} 가 글자를 대조한다.
 * 접두사가 {@code qm:user:} 로 시작하지만 {@code matching} 의 {@code qm:user:*} 키(활성 요청 · 거절한 상대)와는 다른 키다.
 */
public final class BlockKeys {

    /**
     * 차단 관계 SET 접두사.
     * <pre>{@code "qm:user:block-rel:"  →  qm:user:block-rel:42   (SET: "7", "19")}</pre>
     */
    public static final String BLOCK_REL_PREFIX = "qm:user:block-rel:";

    private BlockKeys()
    {
    }

    /**
     * 그 사용자의 차단 관계 SET.
     * <pre>{@code qm:user:block-rel:42}</pre>
     */
    public static String key(long userId)
    {
        return BLOCK_REL_PREFIX + userId;
    }
}
