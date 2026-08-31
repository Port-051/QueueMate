package com.queuemate.matching.domain;

/**
 * PUBG 플레이 스타일 (docs/02 §5).
 * soft 조건이다. 일치를 우선하되 불일치만으로 거절하지 않는다.
 */
public enum PubgPlayStyle implements KeyCondition {

    AGGRESSIVE, BALANCED, SURVIVAL;

    @Override
    public KeyConditionType type() {
        return KeyConditionType.PLAY_STYLE;
    }

    @Override
    public String value() {
        return name();
    }
}
