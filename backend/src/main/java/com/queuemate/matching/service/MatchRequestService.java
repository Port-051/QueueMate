package com.queuemate.matching.service;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRequestService {

    /** claim-request.lua의 반환값. 1이면 활성 요청 자리를 선점했다. */
    private static final long CLAIMED = 1L;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> claimRequestScript;

    /**
     * 활성 요청 자리를 선점하고 요청 내용을 기록한다.
     *
     * "이미 대기 중인가?"를 확인하고 등록하는 두 동작 사이에 다른 요청이 끼어들면
     * 한 사용자가 활성 요청을 둘 가질 수 있다 (INV-1 위반).
     * 확인과 기록을 claim-request.lua 하나에 담아 그 틈을 없앤다.
     */
    public Optional<String> join(CreateMatchRequestCommand command)
    {
        String requestId = UUID.randomUUID().toString();
        String activeKey = activeRequestKey(command.getUserId());

        List<String> args = new ArrayList<>();
        requestFields(command, requestId).forEach((field, value) -> {   // ARGV[1..]
            args.add(field);
            args.add(value);
        });

        Long result = redis.execute(
                claimRequestScript,
                List.of(activeKey),
                args.toArray());

        if (!Long.valueOf(CLAIMED).equals(result)) {
            log.debug("already has an active request: userId={}", command.getUserId());
            return Optional.empty();
        }

        log.debug("joined requestId={} userId={}", requestId, command.getUserId());
        return Optional.of(requestId);
    }

    /** 사용자의 활성 요청. 요청 내용도 여기 함께 담긴다. */
    private String activeRequestKey(String userId) {
        return "qm:user:active-request:" + userId;
    }

    /**
     * 활성 요청 HASH에 담는 필드들. 나중에 파티 키를 되조립할 재료다.
     *
     * 스크립트가 HSET 한 번으로 전부 쓰므로 일부만 적히는 상태가 없다.
     */
    private Map<String, String> requestFields(CreateMatchRequestCommand command, String requestId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("game", command.getGame().name());
        fields.put("modeKey", command.getModeKey());
        fields.put("voicePreference", command.getVoicePreference().name());
        fields.put("playPurpose", command.getPlayPurpose().name());
        fields.put("keyValue", command.getKeyCondition().getValue());
        fields.put("tier", command.getTier());
        return fields;
    }
}
