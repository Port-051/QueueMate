package com.queuemate.platform.room.service;

import com.queuemate.platform.room.domain.SignalResult;
import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.room.redisKeys.RoomKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * WebRTC 시그널을 상대에게 전달한다. <b>서버는 우체부다</b> — 같은 방에 있는 두 사람인지만 확인하고,
 * 내용(SDP · ICE 후보)은 해석하지도 저장하지도 않는다 (docs/11 D-9).
 */
@Service
@RequiredArgsConstructor
public class RoomSignalService {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> signalRoomScript;
    private final RoomNotifier roomNotifier;

    /**
     * @param signal 클라이언트가 보낸 그대로다. 열어 보지 않고 payload 에 싣는다
     */
    public SignalResult send(String roomId, String fromUserId, String toUserId, JsonNode signal)
    {
        // 순서는 스크립트 머리의 KEYS 와 같다
        List<String> keys = List.of(
                RoomKeys.activeRoomKey(fromUserId),
                RoomKeys.activeRoomKey(toUserId));
        SignalResult result = SignalResult.fromCode(RoomRedis.call("signal", () -> redis.execute(signalRoomScript, keys, roomId)));

        // 확인 없이 발행하면 아무에게나 시그널을 쏠 수 있는 구멍이 된다 — 같은 방일 때만 발행한다.
        // 발행은 예외를 밖으로 내보내지 않는다. 놓친 시그널은 클라이언트가 재-offer 로 복구한다
        if (result == SignalResult.SENT)
        {
            roomNotifier.toUser(toUserId, PushEventType.WEBRTC_SIGNAL,
                    Map.of("roomId", roomId, "fromUserId", fromUserId, "signal", signal));
        }
        return result;
    }
}
