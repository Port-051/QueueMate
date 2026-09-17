package com.queuemate.matching.service;

import com.queuemate.matching.dto.MatchRequestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static com.queuemate.matching.redisKeys.SharedKeys.*;

@Service
@RequiredArgsConstructor
public class MatchQueryService {

    private final StringRedisTemplate redis;


    public MatchRequestResponse find(String userId)
    {
        Map<String, String> values = redis.<String, String>opsForHash().entries(activeRequestKey(userId));
        if(values.isEmpty())
        {
            return MatchRequestResponse.idle();
        }
        String status = values.get("status");
        String partyId = values.get("partyId");
        String requestId = values.get("requestId");
        String queuedAt = values.get("queuedAt");
        if("PARTY".equals(status))
        {
            return MatchRequestResponse.matched(requestId, toLong(queuedAt), partyId);
        }
        else {
            if (partyId == null) {
                return MatchRequestResponse.queued(requestId, toLong(queuedAt));
            }
            else
            {
                Map<String, String> partyValues = redis.<String, String>opsForHash().entries(partyKey(partyId));
                String partyStatus = partyValues.get("status");
                if ("CONFIRMED".equals(partyStatus)) {
                    return MatchRequestResponse.matched(requestId, toLong(queuedAt), partyId);
                } else if ("PENDING".equals(partyStatus)) {
                    // 수락자 집합이 비어 있거나 키가 없으면 null 이 온다
                    Set<String> accepted = redis.opsForSet().members(acceptsKey(partyId));
                    boolean isAccepted = accepted != null && accepted.contains(userId);
                    // 파티 HASH 의 필드 이름은 expiresAt 이다. 남은 시간은 클라이언트가 이 값으로 구한다
                    String expiresAt = partyValues.get("expiresAt");
                    return MatchRequestResponse.proposed(
                            requestId, toLong(queuedAt), partyId, toLong(expiresAt), isAccepted);
                } else {
                    String target = partyValues.get("target");
                    int count = 0;
                    for (String key : partyValues.keySet()) {
                        if (key.startsWith("member:")) {
                            ++count;
                        }
                    }
                    return MatchRequestResponse.queued(
                            requestId, toLong(queuedAt), partyId, toInteger(target), count);
                }
            }
        }
    }

    /**
     * 없는 필드는 {@code null} 로 흘려보낸다.
     *
     * <p>Redis HASH 는 필드가 없으면 {@code null} 을 돌려준다. 조회가 그것 때문에 터지면
     * 사용자는 아무 상태도 못 본다 — 없는 값은 응답에서 빠질 뿐이다
     * ({@code MatchRequestResponse} 의 {@code @JsonInclude}). 실제로 {@code queuedAt} 은
     * 나중에 추가한 필드라 그 전에 큐에 들어간 요청에는 없고, 파티는 조회 도중 사라질 수 있다.
     */
    private static Long toLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private static Integer toInteger(String value) {
        return value == null ? null : Integer.valueOf(value);
    }
}
