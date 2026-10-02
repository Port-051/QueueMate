package com.queuemate.platform.room.service;

import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.party.match.MatchPartyKeys;
import com.queuemate.platform.room.RoomProperties;
import com.queuemate.platform.room.domain.Confirmation;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.domain.LeaveResult;
import com.queuemate.platform.room.domain.MatchRoomResult;
import com.queuemate.platform.room.domain.RoomMemberIds;
import com.queuemate.platform.room.domain.RoomState;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.redisKeys.RoomKeys;
import com.queuemate.platform.room.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 방 자체의 일 — 만들기 · 내 방 찾기 · 방장 확정 · 방 닫기(나가기 스크립트) · 방의 상태 읽기. 방 안의 사람(입장 · 강퇴 · 접속 확인)은 {@link RoomMemberService} 가 맡는다.
 * 나가기만은 여기 있다 — 글 지우기가 방을 닫는 데 같은 스크립트를 쓰고, {@code party} 는 {@link RoomMemberService} 를 물 수 없다(빈 순환 — {@link #leave}).
 *
 * <p><b>게시판({@code party})이 부르는 창구이기도 하다</b>(2026-09-25 2단계). 글 쓰기가 {@link #create} 를, 방장 확정이 {@link #confirm} 을
 * 글의 트랜잭션 안에서 부르고({@code party.service.PostStore}), 목록 · 단건 · 입장 검사가 {@link #states} 로 방 안을 읽는다.
 * 자동 매칭 파티의 방은 {@link #enterMatchRoom} 이다({@code party.service.MatchPartyService} 가 트랜잭션 밖에서 부른다 — 2026-09-27 · docs/11 D-42).
 * <b>이 클래스는 {@code party} 의 빈을 부르지 않는다</b> — {@code party} 가 이것을 부르므로 거꾸로 물면 빈 순환이다({@code party.match.MatchPartyKeys} 는
 * 상수라 빈이 아니다).
 *
 * <p>알림은 {@link RoomNotifier} 가 낸다 — 트랜잭션 안에서 부르면 커밋 뒤에 나가고 되돌려지면 나가지 않는다(두 발행기가 그렇게 만들어져 있다).
 * 그래서 글의 트랜잭션 안에서 방을 만들거나 확정해도 되돌려진 변경을 알리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int COMMANDS_PER_ROOM = 3;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> createRoomScript;
    private final RoomProperties roomProperties;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> confirmRoomScript;
    private final RoomNotifier roomNotifier;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> leaveRoomScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> enterMatchRoomScript;

    /**
     * 자동 매칭 파티의 방 — <b>없으면 만들고 있으면 들어간다</b>({@code lua/enter-match-room.lua} 하나. 2026-09-27 소유자 결정 — docs/11 D-42).
     * {@code roomId} 는 {@code matching} 의 {@code partyId}(UUID 문자열)다. 파티원 전원이 {@code MATCH_CONFIRMED} 를 받고 동시에 부르므로 "첫 사람이 만들고
     * 나머지는 들어간다"가 한 스크립트 안이어야 한다 — 둘로 나누면 먼저 온 둘이 각자 만들거나 만들기 전에 들어가려다 거절당한다.
     *
     * <p>확정인가 · 파티원인가 · 정원({@code target})은 스크립트가 <b>{@code matching} 의 파티 HASH 를 읽어</b> 가른다 — 자바가 먼저 읽고 판단하면 그 사이에
     * HASH 가 사라질 수 있다. 부르는 쪽({@code party.service.MatchPartyService})도 같은 HASH 를 읽지만 그것은 DB 에 적을 값을 얻기 위한 것이고, 방의 판정은 여기다.
     * <b>활성 요청 키를 보지 않는다</b> — 확정된 파티원의 활성 요청은 {@code status = 'PARTY'} 로 60초쯤 더 남아 있고 그것이 곧 이 파티다(스크립트 머리).
     *
     * <p>들어왔으면({@link MatchRoomResult#ENTERED}) 먼저 있던 사람들에게 {@code ROOM_MEMBER_ENTERED} 를 알린다 — 게시판 방의 입장
     * ({@code RoomMemberService#enter})과 같은 {@code payload} 다. <b>게시판 신호는 내지 않는다</b> — 자동 매칭 파티에는 글이 없다.
     * 만들었을 때({@link MatchRoomResult#CREATED})는 알릴 사람이 없다.
     */
    public MatchRoomResult enterMatchRoom(String roomId, String userId)
    {
        // 순서는 스크립트 머리의 KEYS 와 같다. 첫 키는 matching 의 파티 HASH 다 — 스크립트는 그것을 읽기만 한다
        List<String> keys = List.of(
                MatchPartyKeys.partyKey(roomId),
                RoomKeys.activeRoomKey(userId),
                RoomKeys.roomHostKey(roomId),
                RoomKeys.roomMemberKey(roomId),
                RoomKeys.roomConfirmedKey(roomId));
        List<?> reply = RoomRedis.call("enterMatchRoom", () -> redis.execute(enterMatchRoomScript, keys, userId, roomId,
                String.valueOf(roomProperties.ttlSeconds())));
        MatchRoomResult result = MatchRoomResult.fromCode(reply == null || reply.isEmpty() ? null : (Long) reply.get(0));

        // 발행은 예외를 밖으로 내보내지 않는다 — 알림이 실패해도 이미 성립한 입장은 그대로다 (CLAUDE.md §3.2)
        if(result == MatchRoomResult.ENTERED)
        {
            roomNotifier.toEach(othersIn(reply, userId), PushEventType.ROOM_MEMBER_ENTERED,
                    Map.of("roomId", roomId, "userId", userId));
        }
        return result;
    }

    /** 방장 포지션 없이 방을 만든다 — {@link #create(String, String, Set, String)} 에 {@code null} 을 넘긴다(테스트가 쓴다) */
    public CreateResult create(String roomId, String userId, Set<String> wanted)
    {
        return create(roomId, userId, wanted, null);
    }

    /**
     * 방을 만든다. 만든 사람이 방장이고, 만들면서 곧바로 그 방에 들어와 있다(멤버 HASH 에 방장 — 값은 <b>방장 포지션</b>, 없으면 {@code ""}).
     *
     * <p><b>HTTP 요청이 없다</b>(2026-09-25 2단계 — 소유자 결정 C). 게시판의 방은 글 쓰기가 만든다({@code PostStore#create}) — 글과 방이 한 요청에서 같이 생긴다.
     * 자동 매칭 파티의 방은 {@link #enterMatchRoom} 이 만든다(2026-09-27 P-30).
     *
     * @param wanted       글의 찾는 포지션(2026-09-30 소유자 결정 — P-44). 남은 찾는 포지션 SET({@link RoomKeys#roomNeedsKey})을 이것으로 채운다 —
     *                     참가하는 사람이 이 가운데 하나를 골라 SET 에서 빼고({@code enter-room.lua}), 나가거나 강퇴되면 돌아온다. 비었거나 {@code null} 이면 SET 을 만들지 않는다
     *                     (포지션이 없는 모드 — 그 방은 포지션을 받지 않는다)
     * @param hostPosition 방장 포지션(글의 {@code hostPosition}) — 멤버 HASH 의 방장 값이 된다(2026-09-30 소유자 결정 — 사람마다의 포지션을 HASH 한 곳에 둔다).
     *                     포지션이 없는 모드면 {@code null} 이고 {@code ""} 로 넘긴다. 글은 고칠 수 없어(2026-10-01 소유자 결정) 이 값은 방이 있는 동안 그대로다
     */
    public CreateResult create(String roomId, String userId, Set<String> wanted, String hostPosition)
    {
        // 순서는 스크립트 머리의 KEYS 와 같다
        List<String> keys = List.of(
                SharedKeys.activeRequestKey(userId),
                RoomKeys.activeRoomKey(userId),
                RoomKeys.roomHostKey(roomId),
                RoomKeys.roomMemberKey(roomId),
                RoomKeys.roomNeedsKey(roomId));
        // 순서는 스크립트 머리의 ARGV 와 같다 — [1] userId [2] roomId [3] ttl [4] 방장 포지션 [5] 찾는 포지션 수 [6..] 찾는 포지션들.
        // StringRedisTemplate 이라 숫자도 문자열로 넘긴다. 가변 인자에는 배열 하나만 넘긴다 — 값 뒤에 배열을 붙이면
        // 배열이 펼쳐지지 않고 원소 하나로 들어가 직렬화에서 깨진다(2026-09-30).
        // null 은 보낼 수 없다 — Redis 인자는 전부 문자열이라 "없음" 은 "" 다(포지션이 없는 모드의 방장 포지션)
        List<String> args = new ArrayList<>();
        args.add(userId);
        args.add(roomId);
        args.add(String.valueOf(roomProperties.ttlSeconds()));
        args.add(hostPosition == null ? "" : hostPosition);
        args.add(String.valueOf(wanted == null ? 0 : wanted.size()));
        if(wanted != null)
        {
            args.addAll(wanted);
        }
        Long code = RoomRedis.call("create", () -> redis.execute(createRoomScript, keys, args.toArray()));
        CreateResult result = CreateResult.fromCode(code);
        if(result == CreateResult.CREATED)
        {
            // 글의 트랜잭션 안이면 글의 신호와 합쳐져 커밋 뒤에 한 번 나간다(BoardSignalPublisher#changed)
            roomNotifier.boardChanged();
        }
        return result;
    }

    /**
     * 이 사용자가 지금 자동 매칭을 기다리는가 — {@code matching} 의 활성 요청 키를 <b>{@code EXISTS} 로만</b> 본다(docs/11 D-19 — 쓰지도 지우지도 값을 읽지도 않는다).
     * 회원 탈퇴가 부른다(2026-10-02 소유자 결정 · P-48 — 매칭 대기 중이면 409 {@code ALREADY_QUEUED}. 이 앱은 그 키를 지울 수 없어 사용자가 먼저 취소해야 한다).
     * 글 쓰기 · 입장은 같은 검사를 방의 스크립트 안에서 한다({@code create-room.lua} · {@code enter-room.lua}) — 탈퇴는 방을 바꾸는 일이 아니라 따로 묻는다.
     * 묻고 난 뒤에 매칭을 거는 밀리초 경쟁은 감수한다(계약 P-48).
     *
     * <p>Redis 에 닿지 못하면 503 {@code ROOM_STATE_UNAVAILABLE} 이다({@link RoomRedis}) — 모르는 채 "기다리지 않는다" 로 읽지 않는다.
     */
    public boolean queued(String userId)
    {
        return Boolean.TRUE.equals(RoomRedis.call("queued", () -> redis.hasKey(SharedKeys.activeRequestKey(userId))));
    }

    /**
     * 이 사용자가 지금 들어가 있는 방. <b>방에 없으면 {@code null} 이다.</b>
     * 앱을 새로 연 클라이언트는 roomId 를 모른다 — 입장 표시 키의 값이 그 답이다.
     */
    public String myRoom(String userId)
    {
        return RoomRedis.call("myRoom", () -> redis.opsForValue().get(RoomKeys.activeRoomKey(userId)));
    }

    /**
     * 방장이 파티를 확정한다. 확정되면 새 사람이 못 들어오고 되돌릴 수 없다.
     * 확정한 그 순간 방에 있던 전원이 파티원이다 — 그 전원(방장 포함)에게 알리고, 부른 쪽에도 돌려준다(파티원을 적는 데 쓴다).
     *
     * <p><b>HTTP 요청({@code POST /api/v1/rooms/{roomId}/confirm})은 여기로 곧장 오지 않는다</b> — 게시판이 글의 줄을 잠근 트랜잭션 안에서 이것을
     * 부르고 성공하면 같은 트랜잭션에서 파티를 적는다({@code PostStore#confirmRoom}. 2026-09-25 2단계).
     */
    public Confirmation confirm(String roomId, String userId)
    {
        // 순서는 스크립트 머리의 KEYS 와 같다. 첫 키는 부른 사람의 입장 표시 키가 아니라 "확정 표시 키"다 —
        // 여기에 입장 표시 키를 넘기면 스크립트가 그 사람의 입장 표시를 roomId 로 덮어쓴다
        List<String> keys = List.of(
                RoomKeys.roomConfirmedKey(roomId),
                RoomKeys.roomHostKey(roomId),
                RoomKeys.roomMemberKey(roomId));
        List<?> reply = RoomRedis.call("confirm", () -> redis.execute(confirmRoomScript, keys, userId,
                roomId, String.valueOf(roomProperties.ttlSeconds())));
        // 첫 칸이 코드다. Redis 의 숫자는 Long 으로 온다
        ConfirmResult result = ConfirmResult.fromCode((Long) reply.get(0));

        Map<Long, String> positions = Map.of();
        List<String> members = List.of();
        // 방장도 받는다 — 방장의 응답(204)에는 누가 파티원이 됐는지가 없다.
        // 발행은 예외를 밖으로 내보내지 않는다. 알림이 실패해도 이미 성립한 확정은 그대로다
        if(result == ConfirmResult.CONFIRMED)
        {
            positions = RoomMemberIds.parsePairs(roomId, reply, 1);
            members = positions.keySet().stream().map(String::valueOf).toList();
            roomNotifier.toEach(members, PushEventType.ROOM_CONFIRMED,
                    Map.of("roomId", roomId, "members", members));
            roomNotifier.boardChanged();
        }
        return new Confirmation(result, members, positions);
    }

    /**
     * 방에서 나간다 — 나가기 스크립트({@code lua/leave-room.lua}) 하나로 끝나고, 나갔으면 남은 사람들에게, 방이 없어졌으면 있던 사람들에게 알린다.
     * 방장이 나가면 확정 전의 방은 통째로 없어지고, 확정한 방은 남은 사람이 방장을 넘겨받는다(D-23).
     *
     * <p><b>부르는 곳이 둘이다</b>(2026-09-25 소유자 결정 — "확정 전에는 방과 글이 같이 끝난다"). ① 나가기 요청({@code RoomMemberService#leave}) —
     * 방장이 나가 방이 닫히면 {@code whenClosed} 가 그 글을 만료시킨다. ② 글 지우기({@code party.service.PostStore#expireByHost}) — 글을 만료시킨 트랜잭션 안에서
     * <b>방장으로서</b> 이것을 불러 방을 닫는다. 방을 닫는 길을 이 스크립트 하나로 모은 것은 두 길의 결과(방장 키 · 멤버 HASH · 전원의 입장 표시 키 삭제 ·
     * {@code ROOM_CLOSED} · 게시판 신호)가 글자까지 같아야 해서다 — 방을 없애는 일을 자바의 맨손 {@code DEL} 로 나눠 하면 중간에 죽었을 때 입장 표시 키가 남는다.
     *
     * <p><b>이 클래스는 {@code party} 를 부르지 않는다</b>(빈 순환) — 방이 닫힌 뒤 글을 어떻게 할지는 부르는 쪽이 {@code whenClosed} 로 넘긴다.
     *
     * @param whenClosed 방장이 나가 <b>방이 없어졌을 때만</b>({@link LeaveResult#ROOM_CLOSED}) 부른다. {@code true} 를 돌려주면 그쪽이 게시판 신호를
     *                   이미 냈다는 뜻이라 여기서 또 내지 않는다 — 글의 만료 · 파티 닫기가 커밋된 뒤에 나가는 신호 하나가 방의 신호를 겸한다(신호는 "다시 받아라" 한 번이면 된다).
     *                   예외를 던지지 않아야 한다 — 방은 이미 닫혔고 알림은 나가야 한다
     */
    public LeaveResult leave(String roomId, String userId, BooleanSupplier whenClosed)
    {
        return leave(roomId, userId, whenClosed, () -> false);
    }

    public LeaveResult leave(String roomId, String userId, BooleanSupplier whenClosed, BooleanSupplier whenLeft)
    {
        List<String> keys = new ArrayList<>();
        keys.add(RoomKeys.activeRoomKey(userId));
        keys.add(RoomKeys.roomMemberKey(roomId));
        keys.add(RoomKeys.roomHostKey(roomId));
        keys.add(RoomKeys.roomConfirmedKey(roomId));
        keys.add(RoomKeys.noAutoJoinKey(userId));
        // 방을 없앨 때 찾는 포지션 SET 도 같이 지운다(P-44)
        keys.add(RoomKeys.roomNeedsKey(roomId));
        // 접두사를 넘기는 이유 — 방을 없앨 때 남은 사람들의 입장 표시 키를 스크립트가 직접 조립한다
        List<?> reply = RoomRedis.call("leave", () -> redis.execute(leaveRoomScript, keys, userId, roomId,
                                                                        RoomKeys.ACTIVE_ROOM_PREFIX,
                                                                        String.valueOf(System.currentTimeMillis() + 600000)));
        LeaveResult result = LeaveResult.fromCode(reply == null || reply.isEmpty() ? null : (Long) reply.get(0));

        // 발행은 예외를 밖으로 내보내지 않는다 — 알림이 실패해도 이미 성립한 나가기는 그대로다 (CLAUDE.md §3.2)
        switch(result)
        {
            case LEFT ->
            {
                boolean signaled = whenLeft.getAsBoolean();
                roomNotifier.toEach(othersIn(reply, userId), PushEventType.ROOM_MEMBER_LEFT,
                        Map.of("roomId", roomId, "userId", userId));
                if (!signaled) roomNotifier.boardChanged();
            }
            // 방이 없어진 뒤에는 멤버 HASH 도 없다. 누구에게 알릴지는 스크립트가 지우기 전에 읽어 돌려준 것이 전부다
            case ROOM_CLOSED ->
            {
                roomNotifier.toEach(othersIn(reply, userId), PushEventType.ROOM_CLOSED, Map.of("roomId", roomId));
                if(!whenClosed.getAsBoolean())
                {
                    roomNotifier.boardChanged();
                }
            }
            case NOT_IN_ROOM ->
            {
            }
        }
        return result;
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
     * 여러 방의 상태를 <b>파이프라인 한 번</b>으로 읽는다 — 방 N개면 명령 3N개({@code EXISTS host} · {@code HGETALL members} ·
     * {@code EXISTS confirmed})를 한꺼번에 보내고 한꺼번에 받는다. 게시판 목록은 신호가 올 때마다 다시 불리므로 방 수만큼 왕복하지 않는다.
     * 멤버 HASH 를 {@code HGETALL} 로 읽어 사람과 그 사람이 고른 포지션을 같이 받는다(2026-09-30 — P-44. {@link RoomState#positions} —
     * 게시판 방 먼저 합류가 "내 포지션이 이미 찬 방" 을 거르는 데 쓰고, 게시판의 카드가 모집 중인 글의 사람마다 싣는다 — 2026-10-01).
     *
     * <p>2026-09-25 2단계 전에는 게시판이 이 키들을 Redis 에서 직접 읽었다({@code party.room.RedisRoomStateReader} — 두 앱이던 때의 모양).
     * 같은 앱이 되어 방 키를 아는 곳을 {@code room} 하나로 모았다. <b>쓰는 명령이 없다</b> — {@code EXISTS} 와 {@code HGETALL} 뿐이다.
     * 세 명령이 한 순간의 것은 아니다(파이프라인은 트랜잭션이 아니다) — 그 사이에 방이 바뀔 수 있지만 다음 조회가 바로잡는다.
     *
     * <p><b>못 읽으면 503 으로 바꾸지 않고 {@link RoomStateUnavailableException} 을 던진다</b> — fail-open(목록)인지 fail-closed(입장)인지는
     * <b>부르는 쪽이 정한다.</b>
     *
     * @return 물어본 방이 전부 들어 있는 맵(없는 방은 "방장 키 없음 · 멤버 없음"이다). 방 번호는 글의 번호다
     */
    public Map<Long, RoomState> states(Collection<Long> roomIds)
    {
        // 같은 방을 두 번 묻지 않는다. 순서를 지켜야 결과를 자리로 짝지을 수 있다
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(roomIds));
        Map<String, RoomState> byRoomId = statesOf(ids.stream().map(String::valueOf).toList());
        Map<Long, RoomState> states = new LinkedHashMap<>();
        for(Long id : ids)
        {
            states.put(id, byRoomId.get(String.valueOf(id)));
        }
        return states;
    }

    /**
     * {@link #states} 의 <b>{@code roomId} 문자열 판</b> — 자동 매칭 파티의 방은 {@code roomId} 가 UUID 라 숫자가 아니다(2026-09-27 · P-30).
     * 게시판 목록이 그 게임의 열려 있는 자동 매칭 파티의 방 키를 읽어 "방이 없어졌나" 를 볼 때 부른다({@code party.service.PostService} — 2026-09-28).
     * 읽는 명령 · 파이프라인 · 못 읽을 때의 예외는 {@link #states} 와 같다 — 숫자 방은 그 메서드가 이것으로 온다.
     *
     * @return 물어본 방이 전부 들어 있는 맵(없는 방은 "방장 키 없음 · 멤버 없음"이다). 물어본 순서대로다
     */
    public Map<String, RoomState> statesOf(Collection<String> roomIds)
    {
        // 같은 방을 두 번 묻지 않는다. 순서를 지켜야 결과를 자리로 짝지을 수 있다
        List<String> ids = new ArrayList<>(new LinkedHashSet<>(roomIds));
        if(ids.isEmpty())
        {
            return Map.of();
        }
        List<Object> results;
        try
        {
            results = redis.executePipelined((RedisCallback<Object>) connection -> {
                for(String id : ids)
                {
                    connection.keyCommands().exists(bytes(RoomKeys.roomHostKey(id)));
                    connection.hashCommands().hGetAll(bytes(RoomKeys.roomMemberKey(id)));
                    connection.keyCommands().exists(bytes(RoomKeys.roomConfirmedKey(id)));
                }
                // 파이프라인의 콜백은 null 을 돌려줘야 한다 — 결과는 executePipelined 가 모아서 준다
                return null;
            });
        }
        catch(RuntimeException e)
        {
            // 연결 거부 · 타임아웃(application.yaml 의 2초) 등. "방이 없다"로 읽히지 않게 예외로 올린다
            log.warn("방 키를 읽지 못했다 rooms={}: {}", ids.size(), e.toString());
            throw new RoomStateUnavailableException(e);
        }
        if(results.size() != ids.size() * COMMANDS_PER_ROOM)
        {
            throw new RoomStateUnavailableException(new IllegalStateException(
                    "파이프라인의 결과 수가 다르다 expected=" + ids.size() * COMMANDS_PER_ROOM + " actual=" + results.size()));
        }

        Map<String, RoomState> states = new LinkedHashMap<>();
        for(int i = 0; i < ids.size(); i++)
        {
            int at = i * COMMANDS_PER_ROOM;
            // HGETALL 의 결과는 템플릿의 문자열 직렬화기가 이미 문자열 맵으로 풀어 준다(필드 = userId · 값 = 고른 포지션). 없는 키는 빈 맵이다
            Object members = results.get(at + 1);
            Map<Long, String> positions = RoomMemberIds.parsePositions(ids.get(i), members instanceof Map<?, ?> m ? m : null);
            states.put(ids.get(i), new RoomState(Boolean.TRUE.equals(results.get(at)), positions.keySet(),
                    Boolean.TRUE.equals(results.get(at + 2)), positions));
        }
        return states;
    }

    /**
     * 자동 합류가 건너뛸 방 — 그 사람이 <b>10분 안에 나갔거나 강퇴당한</b> 방의 {@code roomId} 들이다(2026-09-29 소유자 결정. {@code RoomKeys#noAutoJoinKey}).
     * {@code kick-room.lua} · {@code leave-room.lua} 가 적고 게시판 방 먼저 합류({@code party.service.AutoJoinService})가 후보를 거를 때 부른다 —
     * {@code party} 는 방 키를 직접 읽지 않는다(§3.3). 풀리는 시각(score)이 아직 안 지난 원소만 준다 — 지난 원소는 키의 수명이 지운다.
     *
     * <p>못 읽으면 {@link RoomStateUnavailableException} — {@link #states} 와 같다(자동 합류는 fail-closed 라 부르는 쪽이 503 으로 바꾼다).
     */
    public Set<String> noAutoJoinRooms(String userId)
    {
        try
        {
            Set<String> rooms = redis.opsForZSet().rangeByScore(RoomKeys.noAutoJoinKey(userId),
                    System.currentTimeMillis(), Double.POSITIVE_INFINITY);
            return rooms == null ? Set.of() : rooms;
        }
        catch(RuntimeException e)
        {
            log.warn("자동 합류 건너뛰기 목록을 읽지 못했다 userId={}: {}", userId, e.toString());
            throw new RoomStateUnavailableException(e);
        }
    }

    private static byte[] bytes(String key)
    {
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
