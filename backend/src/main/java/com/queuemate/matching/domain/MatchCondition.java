package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;

/**
 * 사용자가 고른 매칭 조건 (docs/02 §2).
 *
 * <p>여기에 필드를 늘리는 것은 제품 결정이다. {@code docs/12_ELBOW_CONDITION_SELECTION.md}
 * 절차를 통과하기 전에는 추가하지 않는다.
 */
public record MatchCondition(
        GameKey game,
        String modeKey,
        KeyCondition keyCondition,
        VoicePreference voicePreference,
        PlayPurpose playPurpose
) {

    public MatchCondition {
        if (game == null) {
            throw new IllegalArgumentException("game은 필수다");
        }
        if (modeKey == null || modeKey.isBlank()) {
            throw new IllegalArgumentException("modeKey는 필수다");
        }
        if (keyCondition == null) {
            throw new IllegalArgumentException("keyCondition은 필수다");
        }
        if (keyCondition.type() != KeyCondition.typeOf(game)) {
            throw new IllegalArgumentException(
                    game + "에 맞지 않는 조건 종류다: " + keyCondition.type());
        }
        if (voicePreference == null) {
            throw new IllegalArgumentException("voicePreference는 필수다");
        }
        if (playPurpose == null) {
            throw new IllegalArgumentException("playPurpose는 필수다");
        }
        modeKey = modeKey.trim();
    }

    /** 같은 게임/모드에서만 매칭한다 (docs/03 §4). */
    public boolean sameQueueAs(MatchCondition other) {
        return game == other.game && modeKey.equals(other.modeKey);
    }
}
