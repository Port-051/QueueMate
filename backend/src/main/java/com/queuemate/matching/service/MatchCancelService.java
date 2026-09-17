package com.queuemate.matching.service;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.condition.PlayPurpose;
import com.queuemate.matching.domain.condition.VoicePreference;
import com.queuemate.matching.redisKeys.SharedKeys;
import com.queuemate.matching.rule.CandidateRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchCancelService {

    private final StringRedisTemplate redis;
    private final List<CandidateRule> candidateRules;

    public CancelResult cancel(String userId, String requestId) {
        HashOperations<String, String, String> ops = redis.opsForHash();
        Map<String, String> stored = ops.entries(activeRequestKey(userId));

        if (stored.isEmpty()) {
            return CancelResult.NOT_FOUND;
        }

        ActiveRequest active = toActiveRequest(userId, stored);

        return candidateRules.stream()
                .filter(rule -> rule.supports(active.game()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "취소 규칙이 없는 게임: " + active.game()))
                .leave(active, requestId);
    }

    private ActiveRequest toActiveRequest(String userId, Map<String, String> stored) {
        return new ActiveRequest(
                stored.get("requestId"),
                userId,
                GameKey.valueOf(stored.get("game")),
                stored.get("modeKey"),
                VoicePreference.valueOf(stored.get("voicePreference")),
                PlayPurpose.valueOf(stored.get("playPurpose")),
                stored.get("keyValue"),
                stored.get("partyId"),
                stored.get("tier"));
    }

    private String activeRequestKey(String userId) {
        return SharedKeys.activeRequestKey(userId);
    }
}
