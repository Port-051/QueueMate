package com.queuemate.matching.domain;

/**
 * VALORANT 선호 역할군 (docs/02 §4).
 * 역할 중복은 게임에서 막지 않으므로 hard reject 대상이 아니다. tier로만 구분한다.
 */
public enum ValorantRole implements KeyCondition {

    DUELIST, INITIATOR, CONTROLLER, SENTINEL;

    @Override
    public KeyConditionType type() {
        return KeyConditionType.ROLE;
    }

    @Override
    public String value() {
        return name();
    }
}
