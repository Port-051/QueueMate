package com.queuemate.matching.infra;

import com.queuemate.common.domain.GameKey;

import java.util.UUID;

/**
 * 매칭이 쓰는 Redis 키의 단일 출처다 (docs/07 §2).
 * 키 문자열을 서비스 곳곳에 흩어 두면 오타 하나가 정합성 구멍이 되므로 여기서만 만든다.
 */
public final class MatchingRedisKeys {

    private static final String PREFIX = "qm:";

    private MatchingRedisKeys() {
    }

    /** 게임/모드별 대기열. score는 최초 queuedAt이고 재시도해도 보존한다. */
    public static String queue(GameKey game, String modeKey) {
        return PREFIX + "queue:" + game.name() + ":" + modeKey;
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
