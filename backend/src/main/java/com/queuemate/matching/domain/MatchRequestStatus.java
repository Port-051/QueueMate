package com.queuemate.matching.domain;

/**
 * 매칭 요청의 상태. 상태 조회가 돌려주는 값이다.
 */
public enum MatchRequestStatus {

    /** 활성 요청이 없다. 조회에서 "큐에 없음"을 뜻한다. */
    IDLE,

    /**
     * 요청이 살아 있고 아직 제안 전이다.
     *
     * <p><b>파티에 배정됐는지와 무관하다</b> — 배정 스크립트가 혼자짜리 파티를 만들어 넣으므로
     * 대기 중에도 {@code partyId} 는 대개 채워져 있다.
     */
    QUEUED,

    /** 제안이 열려 있다. 파티 HASH 의 {@code status = PENDING} 이고, 남은 시간은 {@code expiresAt} 이 답한다. */
    PROPOSED,

    /** 확정됐다. 파티 HASH 의 {@code status = CONFIRMED}, 또는 활성 요청의 {@code status = PARTY}. */
    MATCHED,

    /**
     * 취소됐다. <b>지금은 조회가 답할 수 없는 값이다</b> — 취소하면 활성 요청 키를 지우므로
     * 서버에 근거가 남지 않아 {@link #IDLE} 과 구분되지 않는다. 자리만 남겨 둔다.
     */
    CANCELLED,

    /**
     * 제안이 만료됐다. {@link #CANCELLED} 와 같은 이유로 <b>지금은 조회가 답할 수 없다</b> —
     * 만료되면 활성 요청 키를 지우므로 {@link #IDLE} 과 구분되지 않는다. 자리만 남겨 둔다.
     */
    EXPIRED
}
