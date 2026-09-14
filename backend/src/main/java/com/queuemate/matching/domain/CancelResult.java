package com.queuemate.matching.domain;

/**
 * 취소 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 */
public enum CancelResult {

    /** 취소됐다. 파티는 남아 있다 */
    CANCELLED,

    /** 취소됐고 마지막 한 명이라 파티까지 지웠다 */
    CANCELLED_AND_PARTY_CLOSED,

    /** 활성 요청이 없다. 이미 취소했거나 애초에 없었다 */
    NOT_FOUND,

    /** 저장된 requestId와 다르다. 늦게 도착한 취소일 수 있다 */
    REQUEST_MISMATCH
}
