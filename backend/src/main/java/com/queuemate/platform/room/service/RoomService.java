package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomProperties;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.room.redisKeys.RoomKeys;
import com.queuemate.platform.room.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 방 자체의 일 — 만들기 · 내 방 찾기 · 방장 확정. 방 안의 사람(입장 · 나가기 · 강퇴 · 접속 확인)은 {@link RoomMemberService} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> createRoomScript;
    private final RoomProperties roomProperties;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> confirmRoomScript;
    private final RoomNotifier roomNotifier;
    /**
     * 방을 만든다. 만든 사람이 방장이고, 만들면서 곧바로 그 방에 들어와 있다.
     */
    public CreateResult create(String roomId, String userId)
    {
        // 순서는 스크립트 머리의 KEYS 와 같다
        List<String> keys = List.of(
                SharedKeys.activeRequestKey(userId),
                RoomKeys.activeRoomKey(userId),
                RoomKeys.roomHostKey(roomId),
                RoomKeys.roomMemberKey(roomId));
        // 순서는 스크립트 머리의 ARGV 와 같다. StringRedisTemplate 이라 숫자도 문자열로 넘긴다
        Long code = redis.execute(createRoomScript, keys, userId, roomId,
                String.valueOf(roomProperties.ttlSeconds()));
        CreateResult result = CreateResult.fromCode(code);
        if(result == CreateResult.CREATED)
        {
            roomNotifier.boardChanged();
        }
        return result;
    }

    /**
     * 이 사용자가 지금 들어가 있는 방. <b>방에 없으면 {@code null} 이다.</b>
     * 앱을 새로 연 클라이언트는 roomId 를 모른다 — 입장 표시 키의 값이 그 답이다.
     */
    public String myRoom(String userId)
    {
        String roomId = redis.opsForValue().get(RoomKeys.activeRoomKey(userId));
        return roomId;
    }

    /**
     * 방장이 파티를 확정한다. 확정되면 새 사람이 못 들어오고 되돌릴 수 없다.
     * 확정한 그 순간 방에 있던 전원이 파티원이다 — 그 전원(방장 포함)에게 알린다.
     */
    public ConfirmResult confirm(String roomId, String userId)
    {
        // 순서는 스크립트 머리의 KEYS 와 같다. 첫 키는 부른 사람의 입장 표시 키가 아니라 "확정 표시 키"다 —
        // 여기에 입장 표시 키를 넘기면 스크립트가 그 사람의 입장 표시를 roomId 로 덮어쓴다
        List<String> keys = List.of(
                RoomKeys.roomConfirmedKey(roomId),
                RoomKeys.roomHostKey(roomId),
                RoomKeys.roomMemberKey(roomId));
        List<?> reply = redis.execute(confirmRoomScript, keys, userId,
                roomId, String.valueOf(roomProperties.ttlSeconds()));
        // 첫 칸이 코드다. Redis 의 숫자는 Long 으로 온다
        ConfirmResult result = ConfirmResult.fromCode((Long) reply.get(0));

        // 방장도 받는다 — 방장의 응답(204)에는 누가 파티원이 됐는지가 없다.
        // 발행은 예외를 밖으로 내보내지 않는다. 알림이 실패해도 이미 성립한 확정은 그대로다
        if(result == ConfirmResult.CONFIRMED)
        {
            List<String> members = reply.stream().skip(1).map(String::valueOf).toList();
            roomNotifier.toEach(members, PushEventType.ROOM_CONFIRMED,
                    Map.of("roomId", roomId, "members", members));
            roomNotifier.boardChanged();
        }
        return result;
    }
}
