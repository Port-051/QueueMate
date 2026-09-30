package com.queuemate.matching.service;

import com.queuemate.matching.dto.JoinResult;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRequestService {

    /** claim-request.lua의 반환값. 1이면 활성 요청 자리를 선점했다. */
    private static final long CLAIMED = 1L;

    /** claim-request.lua의 반환값. -1이면 게시판 방에 들어가 있다 (app:room 의 입장 표시 키가 있다). */
    private static final long IN_ROOM = -1L;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> claimRequestScript;

    /**
     * 접속 확인의 유예(ms) — 접수 순간의 첫 신호에 이 값을 더한 시각이 시한이다 (docs/11 D-43).
     * 생성자 주입이 아니라 필드 주입인 것은 Lombok 의 {@code @RequiredArgsConstructor} 가 {@code @Value} 를 옮겨 주지 않아서다.
     */
    @Value("${queuemate.alive.grace-ms}")
    private long aliveGraceMs;

    /**
     * 활성 요청 자리를 선점하고 요청 내용을 기록한다.
     *
     * "이미 대기 중인가?"를 확인하고 등록하는 두 동작 사이에 다른 요청이 끼어들면
     * 한 사용자가 활성 요청을 둘 가질 수 있다 (INV-1 위반).
     * 확인과 기록을 claim-request.lua 하나에 담아 그 틈을 없앤다.
     *
     * 게시판 방에 들어가 있는 사용자도 같은 자리에서 거절된다 — 스크립트가 app:room 의
     * 입장 표시 키도 함께 본다 (docs/11 D-11 15번).
     */
    public JoinResult join(CreateMatchRequestCommand command)
    {
        String requestId = UUID.randomUUID().toString();
        long queuedAt = System.currentTimeMillis();
        String activeKey = activeRequestKey(command.getUserId());

        List<String> args = new ArrayList<>();
        args.add(command.getUserId());                                             // ARGV[1] — 접속 확인 목록의 member
        args.add(String.valueOf(queuedAt + aliveGraceMs));                         // ARGV[2] — 첫 신호의 시한 (D-43)
        requestFields(command, requestId, queuedAt).forEach((field, value) -> {   // ARGV[3..]
            args.add(field);
            args.add(value);
        });

        Long result = redis.execute(
                claimRequestScript,
                List.of(activeKey, SharedKeys.activeRoomKey(command.getUserId()),
                        SharedKeys.HEARTBEAT_KEY),
                args.toArray());

        if (Long.valueOf(IN_ROOM).equals(result)) {
            log.debug("is in a room: userId={}", command.getUserId());
            return JoinResult.inRoom();
        }
        // 1 이 아닌 나머지(0, 그리고 올 리 없는 null)는 전부 "이미 있다"로 닫는다 — 모르는 값을 접수로 읽지 않는다
        if (!Long.valueOf(CLAIMED).equals(result)) {
            log.debug("already has an active request: userId={}", command.getUserId());
            return JoinResult.alreadyQueued();
        }

        log.debug("joined requestId={} userId={}", requestId, command.getUserId());
        return JoinResult.accepted(requestId, queuedAt);
    }

    /** 사용자의 활성 요청. 요청 내용도 여기 함께 담긴다. */
    private String activeRequestKey(String userId) {
        return SharedKeys.activeRequestKey(userId);
    }

    /**
     * 활성 요청 HASH에 담는 필드들. 나중에 파티 키를 되조립할 재료다.
     *
     * 스크립트가 HSET 한 번으로 전부 쓰므로 일부만 적히는 상태가 없다.
     */
    private Map<String, String> requestFields(CreateMatchRequestCommand command, String requestId, long queuedAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("game", command.getGame().name());
        fields.put("modeKey", command.getModeKey());
        fields.put("voicePreference", command.getVoicePreference().name());
        fields.put("playPurpose", command.getPlayPurpose().name());
        fields.put("keyValue", command.getKeyCondition().getValue());
        fields.put("tier", command.getTier());
        // 줄 선 시각. 조회가 "얼마나 기다렸나"를 답하려면 어딘가에 남아 있어야 하는데,
        // 활성 요청 HASH 말고는 요청이 살아 있는 동안 남는 자리가 없다 (match_requests 테이블은
        // 만들지 않는다 — docs/11 #27). 스크립트는 넘긴 필드를 그대로 HSET 하므로 이 한 줄이면 된다
        fields.put("queuedAt", String.valueOf(queuedAt));
        return fields;
    }
}
