package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.gameconfig.domain.GameModeConfig;

import java.util.List;
import java.util.Optional;

/**
 * 조건 호환 판정 (docs/02, docs/03 §4·§5).
 *
 * <p>순서는 언제나 hard filtering → tiering 이다. hard를 통과하지 못하면 등급 자체가 없다.
 * 결과가 {@code empty}면 "이 조합은 어떤 상황에서도 같은 파티가 될 수 없다"는 뜻이다.
 */
public final class ConditionCompatibility {

    private ConditionCompatibility() {
    }

    /**
     * 두 사람이 같은 파티가 될 수 있는지 판정하고, 가능하면 등급을 매긴다.
     *
     * @return 호환되면 등급, hard 조건을 하나라도 어기면 empty
     */
    public static Optional<CompatibilityTier> between(
            MatchCondition a, MatchCondition b, GameModeConfig config) {
        requireSameMode(a, config);
        requireSameMode(b, config);

        // --- hard filtering ---
        if (!a.sameQueueAs(b)) {
            return Optional.empty();
        }
        if (!voiceCompatible(a.voicePreference(), b.voicePreference())) {
            return Optional.empty();
        }
        if (config.roleUniqueness() && keyConflicts(a.keyCondition(), b.keyCondition())) {
            return Optional.empty();
        }

        // --- tiering ---
        int misses = 0;
        if (!keyOptimal(a.game(), a.keyCondition(), b.keyCondition())) {
            misses++;
        }
        if (a.voicePreference() != b.voicePreference()) {
            misses++;
        }
        if (a.playPurpose() != b.playPurpose()) {
            misses++;
        }
        return Optional.of(CompatibilityTier.fromMisses(misses));
    }

    /**
     * 파티 후보 전체를 판정한다. 모든 쌍이 호환되어야 하고, 등급은 가장 나쁜 쌍이 결정한다.
     *
     * @return 파티가 될 수 있으면 등급, 아니면 empty
     */
    public static Optional<CompatibilityTier> forParty(
            List<MatchCondition> conditions, GameModeConfig config) {
        if (conditions == null || conditions.size() < 2) {
            return Optional.empty();
        }
        // INV-3: 정원을 넘는 조합은 애초에 파티가 아니다.
        if (conditions.size() > config.targetPartySize()) {
            return Optional.empty();
        }

        CompatibilityTier worst = CompatibilityTier.BEST;
        for (int i = 0; i < conditions.size(); i++) {
            for (int j = i + 1; j < conditions.size(); j++) {
                Optional<CompatibilityTier> tier =
                        between(conditions.get(i), conditions.get(j), config);
                if (tier.isEmpty()) {
                    return Optional.empty();
                }
                worst = worst.worseOf(tier.get());
            }
        }
        return Optional.of(worst);
    }

    /** REQUIRED와 NO_VOICE는 어떤 등급에서도 함께 둘 수 없다 (docs/02 §2). */
    public static boolean voiceCompatible(VoicePreference a, VoicePreference b) {
        return !(a == VoicePreference.REQUIRED && b == VoicePreference.NO_VOICE)
                && !(a == VoicePreference.NO_VOICE && b == VoicePreference.REQUIRED);
    }

    /** 자리가 하나뿐인 모드에서 같은 값을 원하면 충돌이다. "아무거나"는 자리를 다투지 않는다. */
    private static boolean keyConflicts(KeyCondition a, KeyCondition b) {
        if (a.isFlexible() || b.isFlexible()) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * 게임마다 "좋은 조합"의 방향이 다르다 (docs/02).
     * LoL/VALORANT는 서로 다른 자리를 맡는 쪽이, PUBG는 같은 스타일끼리가 낫다.
     */
    private static boolean keyOptimal(GameKey game, KeyCondition a, KeyCondition b) {
        return switch (game) {
            case LOL, VALORANT -> !a.isFlexible() && !b.isFlexible() && !a.equals(b);
            case PUBG -> a.equals(b);
        };
    }

    private static void requireSameMode(MatchCondition condition, GameModeConfig config) {
        if (condition.game() != config.game() || !condition.modeKey().equals(config.modeKey())) {
            throw new IllegalArgumentException(
                    "조건과 모드 설정이 다르다: " + condition.game() + "/" + condition.modeKey()
                            + " vs " + config.game() + "/" + config.modeKey());
        }
    }
}
