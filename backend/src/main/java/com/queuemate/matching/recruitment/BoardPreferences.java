package com.queuemate.matching.recruitment;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.domain.*;
import java.util.List;

/** 티어는 사용자 직접 입력 값이다. 외부 게임의 참가 가능 여부를 보증하지 않는다. */
public record BoardPreferences(String ownTier, String minTier, String maxTier,
                               List<String> desiredKeys, boolean purposeRequired) {
    public static final BoardPreferences ANY = new BoardPreferences(null, null, null, List.of(), false);
    public BoardPreferences {
        desiredKeys = desiredKeys == null ? List.of() : List.copyOf(desiredKeys);
        if (desiredKeys.size() > 10 || desiredKeys.stream().distinct().count() != desiredKeys.size())
            throw new IllegalArgumentException("상대 포지션을 중복 없이 선택해 주세요");
    }
    public static List<String> tiers(GameKey game) {
        return switch (game) {
            case LOL -> List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER");
            case VALORANT -> List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "ASCENDANT", "IMMORTAL", "RADIANT");
            case PUBG -> List.of("BRONZE", "SILVER", "GOLD", "PLATINUM", "DIAMOND", "MASTER");
        };
    }
    public void validate(MatchCondition condition) {
        var tiers = tiers(condition.game());
        for (String value : new String[]{ownTier, minTier, maxTier})
            if (value != null && !tiers.contains(value)) throw new IllegalArgumentException("게임에 맞는 티어를 선택해 주세요");
        if (minTier != null && maxTier != null && tiers.indexOf(minTier) > tiers.indexOf(maxTier))
            throw new IllegalArgumentException("최소 티어가 최대 티어보다 높습니다");
        for (String key : desiredKeys) KeyCondition.of(condition.game(), condition.keyCondition().type(), key);
    }
    public boolean accepts(MatchCondition mine, BoardPreferences other, MatchCondition theirs) {
        if (!mine.sameQueueAs(theirs)) return false;
        var tiers = tiers(mine.game());
        if (minTier != null || maxTier != null) {
            int value = other.ownTier == null ? -1 : tiers.indexOf(other.ownTier);
            if (value < 0 || (minTier != null && value < tiers.indexOf(minTier))
                    || (maxTier != null && value > tiers.indexOf(maxTier))) return false;
        }
        if (!desiredKeys.isEmpty() && !desiredKeys.contains("ANY")
                && !desiredKeys.contains(theirs.keyCondition().value())) return false;
        return !purposeRequired || mine.playPurpose() == theirs.playPurpose();
    }
    public static boolean mutual(BoardPreferences a, MatchCondition ca, BoardPreferences b, MatchCondition cb) {
        return a.accepts(ca, b, cb) && b.accepts(cb, a, ca);
    }
}
