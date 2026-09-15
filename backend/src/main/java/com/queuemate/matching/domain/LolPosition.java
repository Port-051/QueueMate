package com.queuemate.matching.domain;

/**
 * LoL 희망 포지션 (docs/02 §3).
 *
 * <p>{@link #ANY}는 "아무 포지션이나 좋다"는 명시적 선택이다. 사용자가 직접 고르지 않는 한
 * 시스템이 포지션을 자동으로 완화하지 않기 때문에, 완화를 허용하려면 이 값이 필요하다.
 */
public enum LolPosition implements KeyCondition {

    TOP, JUNGLE, MID, ADC, SUPPORT, ANY;

    @Override
    public KeyConditionType type() {
        return KeyConditionType.POSITION;
    }

    @Override
    public String value() {
        return name();
    }

    /** ANY는 다른 어떤 포지션과도 자리를 다투지 않는다. */
    public boolean isFlexible() {
        return this == ANY;
    }
}
