package com.queuemate.matching.domain;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.gameconfig.domain.GameModeConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 조건이 완전히 같은 대기자들의 묶음 (docs/07 §3.1).
 *
 * <p>대기열은 모드당 하나가 아니라 bucket당 하나다. 모드당 하나면 후보를 "앞에서부터 N개"로
 * 읽을 수밖에 없고, 서로 매칭될 수 없는 사람들이 그 창을 채우면 뒤에 있는 진짜 조합이
 * 영원히 보이지 않는다. QUEUED에는 만료가 없으므로 그 모드는 스스로 회복하지 못한다.
 *
 * <p>같은 bucket에 있는 두 사람은 조건이 같으므로 호환성도 같다. 그래서 "어떤 bucket에서
 * 상대를 찾아야 하는가"를 사람 단위가 아니라 bucket 단위로 한 번만 계산할 수 있다.
 *
 * <p>bucket은 색인이지 판정이 아니다. 어떤 bucket끼리 같은 파티가 될 수 있는지는 언제나
 * {@link ConditionCompatibility}가 정한다.
 */
public record MatchBucket(
        GameKey game,
        String modeKey,
        KeyCondition keyCondition,
        VoicePreference voice,
        PlayPurpose purpose
) {

    public MatchBucket {
        if (game == null || modeKey == null || keyCondition == null
                || voice == null || purpose == null) {
            throw new IllegalArgumentException("bucket 좌표는 모두 필수다");
        }
    }

    public static MatchBucket of(MatchCondition condition) {
        return new MatchBucket(condition.game(), condition.modeKey(),
                condition.keyCondition(), condition.voicePreference(), condition.playPurpose());
    }

    /** 모드가 가질 수 있는 bucket 전체. LoL 54개, VALORANT 36개, PUBG 27개다. */
    public static List<MatchBucket> allFor(GameModeConfig config) {
        List<KeyCondition> keys = KeyCondition.valuesOf(config.game());
        List<MatchBucket> buckets =
                new ArrayList<>(keys.size() * VoicePreference.values().length
                        * PlayPurpose.values().length);
        for (KeyCondition key : keys) {
            for (VoicePreference voice : VoicePreference.values()) {
                for (PlayPurpose purpose : PlayPurpose.values()) {
                    buckets.add(new MatchBucket(
                            config.game(), config.modeKey(), key, voice, purpose));
                }
            }
        }
        return buckets;
    }

    /**
     * anchor와 같은 파티가 될 수 있는 bucket. anchor 자신이 언제나 맨 앞이다.
     *
     * <p>요청이 들어온 자리에서 출발하는 매칭이 쓴다. 모드 전체 bucket을 읽을 이유가 없다.
     * 어느 bucket이 상대가 되는지는 조건만으로 정해지므로 Redis를 보기 전에 추릴 수 있다.
     *
     * <p>role uniqueness 모드에서는 anchor가 자기 bucket과 호환되지 않지만 그래도 담는다.
     * anchor는 상대이기 전에 seed다 (docs/03 §6).
     */
    public static List<MatchBucket> compatibleWith(MatchBucket anchor, GameModeConfig config) {
        List<MatchBucket> compatible = new ArrayList<>();
        compatible.add(anchor);
        for (MatchBucket candidate : allFor(config)) {
            if (candidate.equals(anchor)) {
                continue;
            }
            ConditionCompatibility
                    .between(anchor.representative(), candidate.representative(), config)
                    .ifPresent(tier -> compatible.add(candidate));
        }
        return compatible;
    }

    /**
     * 이 bucket을 대표하는 조건.
     *
     * <p>bucket 안의 사람은 조건이 모두 같으므로, 호환 판정에 아무나 하나를 세워도 결과가 같다.
     * 덕분에 사람을 읽어 오기 전에 어느 bucket을 볼지 정할 수 있다.
     */
    public MatchCondition representative() {
        return new MatchCondition(game, modeKey, keyCondition, voice, purpose);
    }

    /** Redis key suffix. 값은 enum 이름을 그대로 쓴다. */
    public String suffix() {
        return keyCondition.value() + ":" + voice.name() + ":" + purpose.name();
    }
}
