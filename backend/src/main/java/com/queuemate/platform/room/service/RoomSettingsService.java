package com.queuemate.platform.room.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.RoomMemberIds;
import com.queuemate.platform.room.redisKeys.RoomKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** DB 행 잠금을 가진 설정 저장 요청이 호출한다. 현재 방장·명단을 Lua에서 다시 확인한다. */
@Service
@RequiredArgsConstructor
public class RoomSettingsService {
    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> updateRoomSettingsScript;

    public Map<Long, String> update(String roomId, String userId, int capacity, String hostPosition,
                                    Set<String> wanted, boolean metadataOnly) {
        List<String> keys = List.of(RoomKeys.roomHostKey(roomId), RoomKeys.roomMemberKey(roomId),
                RoomKeys.roomNeedsKey(roomId), RoomKeys.roomConfirmedKey(roomId), RoomKeys.activeRoomKey(userId));
        List<String> args = new ArrayList<>(List.of(userId, roomId, String.valueOf(capacity),
                hostPosition == null ? "" : hostPosition, metadataOnly ? "1" : "0"));
        args.addAll(wanted);
        List<?> result = RoomRedis.call("updateSettings", () -> redis.execute(updateRoomSettingsScript, keys, args.toArray()));
        long code = result == null || result.isEmpty() ? 0 : ((Number) result.getFirst()).longValue();
        if (code == -1) throw RoomErrors.roomNotFound();
        if (code == -2) throw new ApiException(HttpStatus.FORBIDDEN, "NOT_HOST", "현재 방장만 방 설정을 변경할 수 있습니다");
        if (code == -3) throw new ApiException(HttpStatus.CONFLICT, "POST_NOT_RECRUITING", "모집이 마감되었습니다. 방 설정을 다시 열어 주세요");
        if (code == -4) throw ApiException.validationFailed("mode", "현재 멤버 수보다 적은 인원으로 변경할 수 없습니다");
        if (code == -5) throw ApiException.validationFailed("wantedPositions", "현재 멤버의 포지션을 유지해야 합니다. 빈 포지션과 방장 포지션만 변경해 주세요");
        if (code != 1) throw RoomErrors.stateUnavailable();
        return RoomMemberIds.parsePairs(roomId, result, 1);
    }
}
