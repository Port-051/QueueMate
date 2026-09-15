package com.queuemate.matching.domain;

/**
 * 게임별 핵심 조건의 종류 (docs/02). 게임 하나당 정확히 하나를 쓴다.
 * 조건을 하나의 거대한 공통 폼으로 키우지 않기 위한 장치다.
 */
public enum KeyConditionType {

    /** LoL 희망 포지션 */
    POSITION,

    /** VALORANT 선호 역할군 */
    ROLE,

    /** PUBG 플레이 스타일 */
    PLAY_STYLE
}
