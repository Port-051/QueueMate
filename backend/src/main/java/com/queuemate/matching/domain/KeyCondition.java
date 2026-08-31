package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;

/**
 * 게임별 핵심 조건 하나 (docs/02 §1).
 *
 * <p>게임마다 구현이 하나씩만 있고 그 밖의 값은 존재할 수 없다.
 * 문자열을 그대로 들고 다니면 "LOL인데 ROLE" 같은 조합이 도메인 안까지 흘러 들어온다.
 */
public sealed interface KeyCondition permits LolPosition, ValorantRole, PubgPlayStyle {

    KeyConditionType type();

    /** 계약(openapi)에 실리는 값. enum 이름을 그대로 쓴다. */
    String value();

    /**
     * 자리를 다투지 않는 조건인지. 사용자가 명시적으로 "아무거나"를 고른 경우만 true다.
     * role uniqueness 모드에서 중복 판정에서 빠진다 (docs/02 §3).
     */
    default boolean isFlexible() {
        return false;
    }

    /** 게임이 요구하는 조건 종류. */
    static KeyConditionType typeOf(GameKey game) {
        return switch (game) {
            case LOL -> KeyConditionType.POSITION;
            case VALORANT -> KeyConditionType.ROLE;
            case PUBG -> KeyConditionType.PLAY_STYLE;
        };
    }

    /**
     * 계약으로 들어온 type/value를 게임에 맞는 조건으로 바꾼다.
     *
     * @throws IllegalArgumentException 게임에 맞지 않는 종류이거나 값이 없는 경우
     */
    static KeyCondition of(GameKey game, KeyConditionType type, String value) {
        KeyConditionType expected = typeOf(game);
        if (type != expected) {
            throw new IllegalArgumentException(
                    game + "의 조건 종류는 " + expected + "다. 받은 값: " + type);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(game + "의 조건 값이 비어 있다");
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return switch (game) {
                case LOL -> LolPosition.valueOf(normalized);
                case VALORANT -> ValorantRole.valueOf(normalized);
                case PUBG -> PubgPlayStyle.valueOf(normalized);
            };
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    game + "가 모르는 " + expected + " 값이다: " + value, e);
        }
    }
}
