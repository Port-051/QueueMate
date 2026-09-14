package com.queuemate.matching.notification;

/**
 * 이 앱이 발행하는 사용자 알림 종류.
 *
 * <p>SSE 전체 14종 중 {@code app:matching} 이 발행 주체인 5종만 둔다. 나머지 9종
 * (`RESERVATION_*` 2종 = {@code app:reservation-batch}, `PARTY_*`·`FRIEND_*` 7종 =
 * {@code app:platform})은 이 저장소 소관이 아니다 (contracts/events.md).
 *
 * <p>문자열이 아니라 enum 으로 다루는 이유는 오타를 컴파일 단계에서 막기 위해서다.
 * 문자열이면 {@code "MATCH_CANCELED"} 같은 오타가 그대로 나가고, 받는 쪽은 모르는
 * type 을 조용히 버리므로 어디에서도 에러가 나지 않는다.
 */
public enum PushEventType {

    /** 대기 상태가 바뀌었다. payload 는 계약 미정의 (contracts/README.md) */
    MATCH_QUEUE_UPDATED,

    /** 파티 정원이 차서 제안이 생겼다 */
    MATCH_PROPOSAL_CREATED,

    /** 제안 TTL 만료 (INV-5) */
    MATCH_PROPOSAL_EXPIRED,

    /** 전원 수락으로 확정 (INV-4) */
    MATCH_CONFIRMED,

    /** 매칭 취소 */
    MATCH_CANCELLED
}
