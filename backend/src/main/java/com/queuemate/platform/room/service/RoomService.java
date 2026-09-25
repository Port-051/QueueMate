package com.queuemate.platform.room.service;

import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.room.RoomProperties;
import com.queuemate.platform.room.domain.Confirmation;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
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

/**
 * 방 자체의 일 — 만들기 · 내 방 찾기 · 방장 확정 · 방의 상태 읽기. 방 안의 사람(입장 · 나가기 · 강퇴 · 접속 확인)은 {@link RoomMemberService} 가 맡는다.
 *
 * <p><b>게시판({@code party})이 부르는 창구이기도 하다</b>(2026-09-25 2단계). 글 쓰기가 {@link #create} 를, 방장 확정이 {@link #confirm} 을
 * 글의 트랜잭션 안에서 부르고({@code party.service.PostStore}), 목록 · 단건 · 고치기 · 입장 검사가 {@link #states} 로 방 안을 읽는다.
 * <b>이 클래스는 {@code party} 를 부르지 않는다</b> — {@code party} 가 이것을 부르므로 거꾸로 물면 빈 순환이다.
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

    /**
     * 방을 만든다. 만든 사람이 방장이고, 만들면서 곧바로 그 방에 들어와 있다.
     *
     * <p><b>HTTP 요청이 없다</b>(2026-09-25 2단계 — 소유자 결정 C). 게시판의 방은 글 쓰기가 만든다({@code PostStore#create}) — 글과 방이 한 요청에서 같이 생긴다.
     * 자동 매칭 파티의 방을 어떻게 만들지는 미정이다(CLAUDE.md §7.2 (다)).
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
        Long code = RoomRedis.call("create", () -> redis.execute(createRoomScript, keys, userId, roomId,
                String.valueOf(roomProperties.ttlSeconds())));
        CreateResult result = CreateResult.fromCode(code);
        if(result == CreateResult.CREATED)
        {
            // 글의 트랜잭션 안이면 글의 신호와 합쳐져 커밋 뒤에 한 번 나간다(BoardSignalPublisher#changed)
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

        List<String> members = List.of();
        // 방장도 받는다 — 방장의 응답(204)에는 누가 파티원이 됐는지가 없다.
        // 발행은 예외를 밖으로 내보내지 않는다. 알림이 실패해도 이미 성립한 확정은 그대로다
        if(result == ConfirmResult.CONFIRMED)
        {
            members = reply.stream().skip(1).map(String::valueOf).toList();
            roomNotifier.toEach(members, PushEventType.ROOM_CONFIRMED,
                    Map.of("roomId", roomId, "members", members));
            roomNotifier.boardChanged();
        }
        return new Confirmation(result, members);
    }

    /**
     * 여러 방의 상태를 <b>파이프라인 한 번</b>으로 읽는다 — 방 N개면 명령 3N개({@code EXISTS host} · {@code SMEMBERS members} ·
     * {@code EXISTS confirmed})를 한꺼번에 보내고 한꺼번에 받는다. 게시판 목록은 신호가 올 때마다 다시 불리므로 방 수만큼 왕복하지 않는다.
     *
     * <p>2026-09-25 2단계 전에는 게시판이 이 키들을 Redis 에서 직접 읽었다({@code party.room.RedisRoomStateReader} — 두 앱이던 때의 모양).
     * 같은 앱이 되어 방 키를 아는 곳을 {@code room} 하나로 모았다. <b>쓰는 명령이 없다</b> — {@code EXISTS} 와 {@code SMEMBERS} 뿐이다.
     * 세 명령이 한 순간의 것은 아니다(파이프라인은 트랜잭션이 아니다) — 그 사이에 방이 바뀔 수 있지만 다음 조회가 바로잡는다.
     *
     * <p><b>못 읽으면 503 으로 바꾸지 않고 {@link RoomStateUnavailableException} 을 던진다</b> — fail-open(목록)인지 fail-closed(고치기 · 입장)인지는
     * <b>부르는 쪽이 정한다.</b>
     *
     * @return 물어본 방이 전부 들어 있는 맵(없는 방은 "방장 키 없음 · 멤버 없음"이다). 방 번호는 글의 번호다
     */
    public Map<Long, RoomState> states(Collection<Long> roomIds)
    {
        // 같은 방을 두 번 묻지 않는다. 순서를 지켜야 결과를 자리로 짝지을 수 있다
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(roomIds));
        if(ids.isEmpty())
        {
            return Map.of();
        }
        List<Object> results;
        try
        {
            results = redis.executePipelined((RedisCallback<Object>) connection -> {
                for(Long id : ids)
                {
                    connection.keyCommands().exists(bytes(RoomKeys.roomHostKey(String.valueOf(id))));
                    connection.setCommands().sMembers(bytes(RoomKeys.roomMemberKey(String.valueOf(id))));
                    connection.keyCommands().exists(bytes(RoomKeys.roomConfirmedKey(String.valueOf(id))));
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

        Map<Long, RoomState> states = new LinkedHashMap<>();
        for(int i = 0; i < ids.size(); i++)
        {
            int at = i * COMMANDS_PER_ROOM;
            // SMEMBERS 의 결과는 템플릿의 문자열 직렬화기가 이미 문자열로 풀어 준다. 없는 키는 빈 집합이다
            Object members = results.get(at + 1);
            states.put(ids.get(i), new RoomState(Boolean.TRUE.equals(results.get(at)),
                    RoomMemberIds.parse(String.valueOf(ids.get(i)), members instanceof Collection<?> c ? c : null),
                    Boolean.TRUE.equals(results.get(at + 2))));
        }
        return states;
    }

    private static byte[] bytes(String key)
    {
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
