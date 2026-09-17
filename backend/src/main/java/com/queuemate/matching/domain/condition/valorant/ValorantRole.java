package com.queuemate.matching.domain.condition.valorant;

/**
 * VALORANT 역할군. {@code KeyConditionType.ROLE} 의 값이자 VALORANT 매칭 조건 4개 중 2번 줄이다.
 *
 * <p>한 파티 안에서 <b>겹치지 않게</b> 배정한다(LoL 포지션과 같은 구조). 게임 자체는 역할군
 * 중복을 막지 않지만(막는 것은 같은 요원 중복이다), 선호 역할군을 조건으로 받는 의미를 살리려고
 * 제품 규칙으로 정했다. 파티가 최대 3인이라 4개로 모자라지 않아 FLEX 같은 값은 두지 않는다.
 */
public enum ValorantRole {
    DUELIST,     // 타격대
    INITIATOR,   // 척후대
    CONTROLLER,  // 전략가
    SENTINEL     // 감시자
}
