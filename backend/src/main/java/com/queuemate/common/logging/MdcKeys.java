package com.queuemate.common.logging;

/**
 * structured log의 상관관계 필드 이름. docs/09 §3이 정의한 집합이다.
 * 새 키를 임의로 만들지 말고 여기에 모아 팀 간 필드 이름이 갈라지지 않게 한다.
 */
public final class MdcKeys {

    public static final String TRACE_ID = "traceId";
    public static final String REQUEST_ID = "requestId";
    /** 내부 UUID만 넣는다. email 등 식별 가능한 값은 로그에 남기지 않는다. */
    public static final String USER_ID = "userId";

    public static final String PROPOSAL_ID = "proposalId";
    public static final String PARTY_ID = "partyId";
    public static final String RESERVATION_ID = "reservationId";
    public static final String GAME = "game";
    public static final String MODE = "mode";
    public static final String STATE_FROM = "stateFrom";
    public static final String STATE_TO = "stateTo";

    private MdcKeys() {
    }
}
