package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomProperties;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.HeartbeatResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import com.queuemate.platform.room.domain.RoomMembersResult;
import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.room.redisKeys.RoomKeys;
import com.queuemate.platform.room.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class RoomMemberService {

    /** 한 방의 정원. 방장을 포함하고 둘러보는 사람도 센다 (docs/11 D-11 10번) */
    private static final int CAPACITY = 5;

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enterRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> leaveRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> membersRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> heartbeatRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> kickRoomScript;
    private final RoomNotifier roomNotifier;
    private final RoomProperties roomProperties;

    /**
     * 방에 들어온다. 들어왔으면 방에 이미 있던 사람들에게 알린다.
     */
    public EnterResult enter(String roomId, String userId)
    {
        List<String> keys = new ArrayList<String>();
        keys.add(SharedKeys.activeRequestKey(userId));
        keys.add(RoomKeys.activeRoomKey(userId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomConfirmedKey(roomId));
        // StringRedisTemplate 이라 인자는 전부 문자열로 넘긴다. 순서는 스크립트 머리의 ARGV 와 같다
        List<?> reply = redis.execute(enterRoomScript, keys, userId, roomId, String.valueOf(CAPACITY),
                String.valueOf(roomProperties.ttlSeconds()));
        EnterResult result = EnterResult.fromCode(codeOf(reply));

        // 발행은 예외를 밖으로 내보내지 않는다 — 알림이 실패해도 이미 성립한 입장은 그대로다 (CLAUDE.md §3.2)
        if (result == EnterResult.ENTERED)
        {
            roomNotifier.toEach(othersIn(reply, userId), PushEventType.ROOM_MEMBER_ENTERED,
                    Map.of("roomId", roomId, "userId", userId));
            roomNotifier.boardChanged();
        }
        return result;
    }

    /**
     * 방에서 나간다. 방장이 나가면 방이 통째로 없어진다 — 그 일은 전부 스크립트 안에서 끝난다.
     * 나갔으면 남은 사람들에게, 방이 없어졌으면 있던 사람들에게 알린다.
     */
    public LeaveResult leave(String roomId, String userId) {
        List<String> keys = new ArrayList<>();
        keys.add(RoomKeys.activeRoomKey(userId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomConfirmedKey(roomId));
        // 접두사를 넘기는 이유 — 방을 없앨 때 남은 사람들의 입장 표시 키를 스크립트가 직접 조립한다
        List<?> reply = redis.execute(leaveRoomScript, keys, userId, roomId, RoomKeys.ACTIVE_ROOM_PREFIX);
        LeaveResult result = LeaveResult.fromCode(codeOf(reply));

        switch (result) {
            case LEFT -> {
                roomNotifier.toEach(othersIn(reply, userId), PushEventType.ROOM_MEMBER_LEFT,
                        Map.of("roomId", roomId, "userId", userId));
                roomNotifier.boardChanged();
            }
            // 방이 없어진 뒤에는 멤버 SET 도 없다. 누구에게 알릴지는 스크립트가 지우기 전에 읽어 돌려준 것이 전부다
            case ROOM_CLOSED -> {
                roomNotifier.toEach(othersIn(reply, userId), PushEventType.ROOM_CLOSED,
                        Map.of("roomId", roomId));
                roomNotifier.boardChanged();
            }
            case NOT_IN_ROOM -> { }
        }
        return result;
    }

    /**
     * 강퇴. 방장이 방에 들어와 있는 사람을 내보낸다. 부른 사람이 방장인지는 스크립트가 방장 키와 비교해서 안다 —
     * 여기서 먼저 읽어 보고 판단하지 않는다(그 사이에 방장이 나가 방이 닫힐 수 있다).
     * 강퇴했으면 방에 남은 사람들과 강퇴된 본인에게 알린다. 재입장은 막지 않는다 (미정 — {@code contracts/room-api.md} "강퇴").
     */
    public KickResult kick(String roomId, String hostUserId, String targetUserId)
    {
        List<String> keys = new ArrayList<>();
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        // 부른 사람이 아니라 대상의 입장 표시 키다
        keys.add(RoomKeys.activeRoomKey(targetUserId));
        List<?> reply = redis.execute(kickRoomScript, keys, hostUserId, targetUserId, roomId);
        KickResult result = KickResult.fromCode(codeOf(reply));

        if (result == KickResult.KICKED)
        {
            // 방에 남은 사람들(방장 포함)에 강퇴된 본인을 더한다. 본인은 자기가 부른 요청이 아니라서 알림이 아니면 알 길이 없다.
            // 방장도 받는다 — 다른 알림과 모양을 맞춰 남은 사람 전원에게 보낸다
            List<String> recipients = new ArrayList<>(reply.stream().skip(1).map(String::valueOf).toList());
            recipients.add(targetUserId);
            roomNotifier.toEach(recipients, PushEventType.ROOM_MEMBER_KICKED,
                    Map.of("roomId", roomId, "userId", targetUserId));
            roomNotifier.boardChanged();
        }
        return result;
    }

    /**
     * 방 안 사람 목록. 알림을 놓친 클라이언트가 지금 상태를 다시 맞추는 조회다(새로고침 · SSE 재연결 직후).
     * 읽기만 한다 — Lua 가 필요 없다.
     */
    public RoomMembersResult members(String roomId, String userId)
    {
        List<String> keys = new ArrayList<>();
        keys.add(RoomKeys.activeRoomKey(userId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        keys.add(RoomKeys.roomHostKey(roomId));
        List<?> reply = redis.execute(membersRoomScript, keys, roomId);
        // 코드는 언제나 첫 칸에 있다. Redis 의 숫자는 Long 으로 온다 — (int) 로 꺼내면 실행 중에 ClassCastException 이다
        Long code = codeOf(reply);
        if(Long.valueOf(-1).equals(code))
        {
            return RoomMembersResult.notInRoom();
        }
        else if(Long.valueOf(-2).equals(code))
        {
            return RoomMembersResult.roomNotFound();
        }
        else if(Long.valueOf(1).equals(code))
        {
            // { 1, 방장, 멤버들… } — 멤버들에는 방장도 들어 있다. 방에 있는 사람 전부이고 인원수는 이 목록의 크기다
            String hostId = reply.get(1).toString();
            List<String> members = reply.stream().skip(2).map(String::valueOf).toList();
            return RoomMembersResult.found(hostId, members);
        }
        // 모르는 값을 성공으로 읽지 않는다
        throw new IllegalStateException("members-room.lua 가 모르는 값을 돌려줬다: " + code);
    }

    /** 첫 칸(코드) 뒤에 붙어 온 사람들에서 본인을 뺀다. 본인은 REST 응답으로 이미 안다 */
    private static List<String> othersIn(List<?> reply, String userId)
    {
        return reply.stream()
                .skip(1)
                .map(String::valueOf)
                .filter(memberId -> !memberId.equals(userId))
                .toList();
    }

    /**
     * 접속 확인. 브라우저가 1분마다 부른다 — 키의 수명을 다시 걸어 늘린다. 신호가 끊기면 수명이 다해 저절로 사라진다.
     * 방의 수명은 방장의 신호만 늘리고, 방장의 신호는 멤버 SET 에 이름만 남은 유령도 뺀다.
     */
    public HeartbeatResult heartbeat(String roomId, String userId)
    {
        List<String> keys = new ArrayList<>();
        keys.add(RoomKeys.activeRoomKey(userId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomConfirmedKey(roomId));
        List<?> reply = redis.execute(heartbeatRoomScript, keys, userId, roomId, RoomKeys.ACTIVE_ROOM_PREFIX,
                                                        String.valueOf(roomProperties.ttlSeconds()));
        HeartbeatResult result = HeartbeatResult.fromCode(codeOf(reply));

        // 방장의 신호는 { 1, 뺀 사람 수 N, 뺀 사람 N명…, 남은 사람들… } 로 온다. 일반 멤버의 신호는 { 1 } 뿐이다.
        // 뺀 사람(멤버 SET 에 이름만 남아 있던 유령)마다, 남은 사람들에게 그 사람의 퇴장을 알린다 —
        // 받는 쪽은 userId 를 보고 목록에서 지우고 그 사람과의 음성 연결을 끊는다
        if(result == HeartbeatResult.ALIVE && reply.size() > 2)
        {
            int removedCount = ((Long) reply.get(1)).intValue();
            List<String> removed = reply.subList(2, 2 + removedCount).stream().map(String::valueOf).toList();
            // 신호를 보낸 방장도 받는다. 방장의 응답(204)에는 누가 빠졌는지가 없어서, 알림이 아니면 방장만 모르게 된다
            List<String> remaining = reply.subList(2 + removedCount, reply.size()).stream().map(String::valueOf).toList();
            for(String leftUserId : removed)
            {
                roomNotifier.toEach(remaining, PushEventType.ROOM_MEMBER_LEFT,
                        Map.of("roomId", roomId, "userId", leftUserId));
            }
            // 유령을 뺐으면 방의 인원이 바뀐 것이다. 몇 명을 뺐든 게시판 신호는 한 번이면 된다 — "다시 받아라"일 뿐이다
            if (!removed.isEmpty())
            {
                roomNotifier.boardChanged();
            }
        }
        // 방이 없어졌을 때(-2) 방 안의 사람에게는 알리지 않는다. 본인은 이 응답으로 알고, 다른 사람은 누구였는지 알 길이 없다 —
        // 멤버 SET 이 이미 만료돼 사라졌다. 그 사람들도 자기 다음 신호에서 같은 답을 받는다.
        // 게시판에는 알린다. 방장이 말없이 사라져 수명이 다한 방은 없어지는 순간에 이 앱의 코드가 돌지 않아 신호를 못 낸다 —
        // 남아 있던 사람의 신호가 -2 를 받는 지금이 이 앱이 그것을 아는 첫 순간이다(늦어도 1분 뒤). 남은 사람이 여럿이면
        // 각자 한 번씩 보내게 되지만 해가 없다. 방장 혼자 있던 방은 신호를 보낼 사람이 없어 여전히 못 낸다
        if (result == HeartbeatResult.ROOM_CLOSED)
        {
            roomNotifier.boardChanged();
        }
        return result;
    }

    /** 스크립트가 돌려준 목록의 첫 칸. 비어 있으면 {@code null} 이고 {@code fromCode} 가 예외로 알린다 */
    private static Long codeOf(List<?> reply)
    {
        return reply == null || reply.isEmpty() ? null : (Long) reply.get(0);
    }
}
