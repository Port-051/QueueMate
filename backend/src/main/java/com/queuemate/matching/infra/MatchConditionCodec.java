package com.queuemate.matching.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.domain.KeyCondition;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.MatchCondition;
import org.springframework.stereotype.Component;

/**
 * MatchCondition을 condition_json 컬럼에 담고 꺼낸다.
 *
 * <p>도메인의 {@link KeyCondition}은 게임마다 타입이 다른 sealed interface라 그대로 직렬화하면
 * 다형성 정보가 필요하다. 저장 형태는 평평한 레코드로 고정해서 컬럼을 사람이 읽을 수 있게 둔다.
 */
@Component
public class MatchConditionCodec {

    private final ObjectMapper mapper;

    public MatchConditionCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(MatchCondition condition) {
        try {
            return mapper.writeValueAsString(Stored.from(condition));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("매칭 조건을 직렬화하지 못했다", e);
        }
    }

    public MatchCondition fromJson(String json) {
        try {
            return mapper.readValue(json, Stored.class).toDomain();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("매칭 조건을 읽지 못했다: " + json, e);
        }
    }

    record Stored(
            GameKey game,
            String modeKey,
            KeyConditionType keyConditionType,
            String keyConditionValue,
            VoicePreference voicePreference,
            PlayPurpose playPurpose
    ) {
        static Stored from(MatchCondition condition) {
            return new Stored(
                    condition.game(),
                    condition.modeKey(),
                    condition.keyCondition().type(),
                    condition.keyCondition().value(),
                    condition.voicePreference(),
                    condition.playPurpose());
        }

        MatchCondition toDomain() {
            return new MatchCondition(
                    game, modeKey,
                    KeyCondition.of(game, keyConditionType, keyConditionValue),
                    voicePreference, playPurpose);
        }
    }
}
