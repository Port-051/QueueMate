package com.queuemate.matching.outbox;

/**
 * outbox 에 적는 이벤트의 종류. 이 앱이 내는 앱 간 이벤트는 지금 하나뿐이다 (docs/11 #21 · D-12 · D-13).
 *
 * <p>DB 에는 이 enum 의 이름이 문자열로 들어간다({@code @Enumerated(STRING)}). 받는 쪽(platform)이 큐 이름으로
 * 아는 것은 {@code ProposalConfirmed} 이고, 그 이름은 {@link #queueName()} 이 준다 — 둘을 한 자리에 묶어
 * 두어 어느 한쪽만 고치는 일이 없게 한다.
 */
public enum OutboxEventType {

    /** 제안이 전원 수락으로 확정됐다. platform 이 받아 파티를 DB 에 만든다 */
    PROPOSAL_CONFIRMED("ProposalConfirmed");

    private final String queueName;

    OutboxEventType(String queueName) {
        this.queueName = queueName;
    }

    /** 계약({@code contracts/events.md})이 부르는 이벤트 이름. SQS 큐 이름의 앞부분이기도 하다 */
    public String queueName() {
        return queueName;
    }
}
