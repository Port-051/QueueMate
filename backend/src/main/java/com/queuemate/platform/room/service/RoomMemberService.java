package com.queuemate.platform.room.service;

import com.queuemate.platform.party.service.MatchPartyStore;
import com.queuemate.platform.party.service.PostEntryGate;
import com.queuemate.platform.party.service.PostLifecycle;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 방 안의 사람 — 입장 · 나가기 · 강퇴 · 방 안 사람 목록 · 접속 확인. 방을 바꾸는 것은 전부 Lua 스크립트 하나 안에서 끝난다(정원 · 입장 표시 키 ·
 * 활성 요청 키의 {@code EXISTS} 가 한 스크립트 안에 있어야 불변식이 선다).
 *
 * <p><b>게시판을 부르는 곳이 둘이다.</b> ① 입장(2026-09-25 2단계 — 입장권을 없앴다. 소유자 결정 ①) — 스크립트를 부르기 <b>전에</b> {@link PostEntryGate} 가
 * 글의 상태와 차단을 본다. ② 나가기(2026-09-25 소유자 결정 — "확정 전에는 방과 글이 같이 끝난다") — 방이 닫히면(나가기 · 접속 확인의 {@code ROOM_CLOSED}) {@link PostLifecycle} 이
 * 글을 만료시킨다. 확정한 방이었으면 대신 파티를 닫는다(2026-09-26 소유자 결정). <b>방 번호가 UUID 면 자동 매칭 파티의 방이다</b>(2026-09-27 — docs/11 D-42) —
 * 글이 없으니 {@link MatchPartyStore} 가 파티만 닫는다. 세 창구 모두 {@code PostService} 를 물지 않는 따로 선 빈이라 이 클래스와 빈 순환이 생기지 않는다
 * ({@code PostService} → {@code RoomService} 이고, 이 클래스 → {@code PostEntryGate} → {@code PostStore} · {@code RoomService}, 이 클래스 → {@code PostLifecycle} ·
 * {@code MatchPartyStore} 이다).
 *
 * <p><b>자동 매칭 파티의 방에 들어오는 길은 여기({@link #enter})가 아니다</b> — {@code POST /api/v1/match-parties/{partyId}/room}({@code party.controller.MatchPartyController} →
 * {@link RoomService#enterMatchRoom}). 이 입장은 글부터 보므로 UUID {@code roomId} 는 404 {@code POST_NOT_FOUND} 다. 나가기 · 강퇴 · 접속 확인 · 목록 · 시그널은
 * 방 번호를 글자 그대로 키에 넣으므로 UUID 방에도 그대로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomMemberService {

    /** 한 방의 정원. 방장을 포함하고 둘러보는 사람도 센다 (docs/11 D-11 10번) */
    private static final int CAPACITY = 5;

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enterRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> membersRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> heartbeatRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> kickRoomScript;
    private final RoomNotifier roomNotifier;
    private final RoomProperties roomProperties;
    private final PostEntryGate postEntryGate;
    private final PostLifecycle postLifecycle;
    private final MatchPartyStore matchPartyStore;
    private final RoomService roomService;

    /**
     * 방에 들어온다. 들어왔으면 방에 이미 있던 사람들에게 알린다.
     *
     * <p><b>순서</b>(2026-09-25 2단계) — ① 게시판의 검사({@link PostEntryGate#check}: 없는 글 · 차단으로 숨겨진 글 404 {@code POST_NOT_FOUND},
     * 모집 중이 아닌 글 409 {@code POST_NOT_RECRUITING}) → ② 스크립트({@code ROOM_NOT_FOUND} · {@code ROOM_FULL} · {@code ROOM_CONFIRMED} ·
     * {@code ALREADY_QUEUED} · {@code IN_OTHER_ROOM} · 이미 들어와 있음). 두 앱이던 때 입장권 발급이 하던 ① 을 같은 요청 안에서 한다.
     * ① 과 ② 사이는 원자적이지 않다 — 그 사이에 나와 차단 관계인 사람이 먼저 들어오는 경쟁이 남는다(입장권 60초였던 창이 밀리초로 줄었을 뿐이다).
     *
     * @throws com.queuemate.platform.common.error.ApiException ① 의 거절. 스크립트의 거절은 결과 enum 으로 돌려준다
     */
    public EnterResult enter(String roomId, String userId)
    {
        postEntryGate.check(roomId, Long.parseLong(userId));
        List<String> keys = new ArrayList<String>();
        keys.add(SharedKeys.activeRequestKey(userId));
        keys.add(RoomKeys.activeRoomKey(userId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomConfirmedKey(roomId));
        keys.add(RoomKeys.noEntryKey(userId));
        // StringRedisTemplate 이라 인자는 전부 문자열로 넘긴다. 순서는 스크립트 머리의 ARGV 와 같다
        List<?> reply = RoomRedis.call("enter", () -> redis.execute(enterRoomScript, keys, userId, roomId, String.valueOf(CAPACITY),
                String.valueOf(roomProperties.ttlSeconds()), String.valueOf(System.currentTimeMillis())));
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
     * 방에서 나간다. 방장이 나가면 방이 통째로 없어진다 — 그 일은 전부 스크립트 안에서 끝난다({@link RoomService#leave}).
     * 나갔으면 남은 사람들에게, 방이 없어졌으면 있던 사람들에게 알린다.
     *
     * <p><b>확정 전의 방이 닫히면 그 글도 그 자리에서 만료된다</b>(2026-09-25 소유자 결정 — "확정 전에는 방과 글이 같이 끝난다").
     * 전에는 글을 건드리지 않고 다음 목록 조회가 "방장 키 없음 → 만료" 로 옮겨 적기를 기다렸다 — 그 사이에 방장이 새 글을 쓰면 409 {@code ALREADY_RECRUITING} 이었다.
     * <b>확정한 방은 글이 만료되지 않는다</b> — 방장이 나가면 승계라 결과가 {@link LeaveResult#LEFT} 이다. 넘길 사람이 없어(마지막 사람이 나가) 닫히면
     * 글은 {@code CONFIRMED} 그대로이고 <b>대신 파티가 닫힌다</b>(2026-09-26 소유자 결정 — {@link PostLifecycle#endByRoomClosed}).
     *
     * <p><b>글을 만료시키지 못해도 나가기는 성공이다</b> — 방은 이미 닫혔고 되돌릴 수 없다. 글은 다음 목록 · 단건이 방장 키가 없는 것을 보고 만료로 옮겨 적는다
     * (그래서 {@code PostService} 의 자가 치유는 그대로 남는다). WARN 만 남긴다.
     */
    public LeaveResult leave(String roomId, String userId)
    {
        return roomService.leave(roomId, userId, () -> endPostOf(roomId));
    }

    /**
     * 방이 없어진 글 · 파티를 정리한다 — 글 번호(숫자)의 방이면 확정 전이면 글을 만료시키고 확정한 방이면 파티를 닫는다({@link PostLifecycle#endByRoomClosed}).
     * <b>숫자가 아니면 자동 매칭 파티의 방({@code roomId} = {@code matching} 의 UUID)이다</b> — 글이 없으니 파티만 닫는다({@link MatchPartyStore#closeByRoomClosed}.
     * 2026-09-27 — docs/11 D-42). 게시판 신호를 냈으면(글이 이 호출로 만료됐으면) {@code true}
     *
     * <p>자동 매칭 파티는 <b>"전원이 말없이 사라져 키만 만료"된 경우 닫히지 않는다</b> — 글이 없어 목록 · 단건의 옮겨 적기(길 ②)가 그 방 키를 볼 일이 없다.
     * 남은 사람의 접속 확인(여기 {@code ROOM_CLOSED})이 그 자리를 얼마간 메우지만, 전원이 신호 없이 사라지면 {@code parties} 에 {@code ACTIVE} 로 남는다.
     * <b>미정으로 남겨 둔다</b>(D-42 "아직 미정") — 게시판 파티의 길 ② 에 해당하는 것을 어디에 둘지 정해지지 않았다.
     */
    private boolean endPostOf(String roomId)
    {
        try
        {
            Long postId;
            try
            {
                postId = Long.parseLong(roomId);
            }
            catch(NumberFormatException e)
            {
                matchPartyStore.closeByRoomClosed(roomId);
                return false;
            }
            return postLifecycle.endByRoomClosed(postId);
        }
        catch(RuntimeException e)
        {
            log.warn("방은 닫혔는데 글 · 파티를 정리하지 못했다(만료 · 파티 닫기) — 목록 · 단건의 옮겨 적기에 맡긴다 roomId={}: {}", roomId, e.toString());
            return false;
        }
    }

    /**
     * 강퇴. 방장이 방에 들어와 있는 사람을 내보낸다. 부른 사람이 방장인지는 스크립트가 방장 키와 비교해서 안다 —
     * 여기서 먼저 읽어 보고 판단하지 않는다(그 사이에 방장이 나가 방이 닫힐 수 있다).
     * 강퇴했으면 방에 남은 사람들과 강퇴된 본인에게 알린다.
     *
     * <p><b>강퇴당한 사람은 10분 동안 그 방에 다시 들어올 수 없다</b>(2026-09-29 소유자 결정 — 직접 입장도 자동 합류도). 금지는 스크립트가 강퇴와 한 번에
     * 적는다({@code RoomKeys#noEntryKey} · {@code noAutoJoinKey}) — 여기서 따로 쓰면 강퇴만 되고 금지는 안 남는 틈이 생긴다. 풀리는 시각은 이 앱의 시계로 넘긴다.
     */
    public KickResult kick(String roomId, String hostUserId, String targetUserId)
    {
        List<String> keys = new ArrayList<>();
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        // 부른 사람이 아니라 대상의 입장 표시 키다. 금지 목록 둘도 대상의 것이다
        keys.add(RoomKeys.activeRoomKey(targetUserId));
        keys.add(RoomKeys.noAutoJoinKey(targetUserId));
        keys.add(RoomKeys.noEntryKey(targetUserId));
        long expireTime = System.currentTimeMillis() + 600000;
        List<?> reply = RoomRedis.call("kick", () -> redis.execute(kickRoomScript, keys, hostUserId, targetUserId,
                                                                        roomId, String.valueOf(expireTime)));
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
        List<?> reply = RoomRedis.call("members", () -> redis.execute(membersRoomScript, keys, roomId));
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
        List<?> reply = RoomRedis.call("heartbeat", () -> redis.execute(heartbeatRoomScript, keys, userId, roomId, RoomKeys.ACTIVE_ROOM_PREFIX,
                                                        String.valueOf(roomProperties.ttlSeconds())));
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
        // 나가기와 같은 정리를 한다(2026-09-26) — 확정 전의 방이면 글을 만료시키고(목록 · 단건이 할 옮겨 적기를 앞당긴다), 확정한 방이면 파티를 닫는다.
        // 이 스크립트가 -2 를 주는 것은 방장 키도 확정 표시 키도 없을 때뿐이다 — 확정한 방이었다면 키 셋이 다 없어진 것이다
        if (result == HeartbeatResult.ROOM_CLOSED && !endPostOf(roomId))
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
