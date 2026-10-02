package com.queuemate.matching.domain.condition.lol;

/**
 * LoL 희망 포지션. {@code KeyConditionType.POSITION} 의 값이자 LoL 매칭 조건 4개 중 2번 줄이다.
 */
public enum LolPosition {
    TOP, JUNGLE, MID, ADC, SUPPORT,

    /**
     * 포지션 개념이 없는 모드(칼바람 등)에서 쓰는 값.
     */
    NONE
}
