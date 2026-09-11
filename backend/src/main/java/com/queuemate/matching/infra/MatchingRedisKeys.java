package com.queuemate.matching.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.domain.MatchBucket;

import java.util.List;
import java.util.UUID;

/**
 * 매칭이 쓰는 Redis 키의 단일 출처다 (docs/07 §2).
 * 키 문자열을 서비스 곳곳에 흩어 두면 오타 하나가 정합성 구멍이 되므로 여기서만 만든다.
 */
public final class MatchingRedisKeys {

    private static final String PREFIX = "qm:";

    private MatchingRedisKeys() {
    }

    /**
     * 조건이 같은 사람끼리 묶인 대기열 (docs/07 §3.1). score는 최초 queuedAt이고 재시도해도 보존한다.
     *
     * <p>모드당 하나가 아니라 bucket당 하나다. 이유는 {@link com.queuemate.matching.domain.MatchBucket}에 있다.
     */
    public static String queue(MatchBucket bucket) {
        return PREFIX + "queue:" + bucket.game().name() + ":" + bucket.modeKey()
                + ":" + bucket.suffix();
    }

    /** 계획에 담긴 bucket들의 key. Lua에 넘길 KEYS 순서를 그대로 유지한다. */
    public static List<String> queues(List<MatchBucket> buckets) {
        return buckets.stream().map(MatchingRedisKeys::queue).toList();
    }

    /** INV-1 guard. 값은 현재 활성 requestId다. */
    public static String activeRequest(UUID userId) {
        return activeRequestPrefix() + userId;
    }

    /** reconciliation이 guard 키를 훑을 때 쓰는 접두사. */
    public static String activeRequestPrefix() {
        return PREFIX + "user:active-request:";
    }

    /** INV-2 guard. 값은 현재 활성 proposalId이고 TTL을 가진다. */
    public static String activeProposal(UUID userId) {
        return PREFIX + "user:active-proposal:" + userId;
    }

    /** proposal 참가자 userId 집합. proposal과 같은 TTL을 가진다. */
    public static String proposalMembers(UUID proposalId) {
        return PREFIX + "proposal:members:" + proposalId;
    }

    /**
     * 30분 슬롯 하나에 걸린 예약 id 집합 (docs/07 §8).
     * 상세 조건은 담지 않는다. 후보를 좁히는 색인일 뿐이고 판정은 DB를 읽고 한다.
     */
    public static String reservationSlot(GameKey game, String modeKey, String slotKey) {
        return PREFIX + "reservation:slot:" + game.name() + ":" + modeKey + ":" + slotKey;
    }
}
