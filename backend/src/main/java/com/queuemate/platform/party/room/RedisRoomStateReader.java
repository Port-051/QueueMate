package com.queuemate.platform.party.room;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 방 키를 <b>파이프라인 한 번</b>으로 읽는다 — 글 N개면 명령 3N개({@code EXISTS host} · {@code SMEMBERS members} · {@code EXISTS confirmed})를
 * 한꺼번에 보내고 한꺼번에 받는다. 목록은 게시판 신호가 올 때마다 다시 불리므로 글 수만큼 왕복하지 않는다.
 *
 * <p><b>이 클래스에는 쓰는 명령이 없다</b> — {@code EXISTS} 와 {@code SMEMBERS} 뿐이다(CLAUDE.md §3.3 · §11).
 * 세 명령이 한 순간의 것은 아니다(파이프라인은 트랜잭션이 아니다) — 그 사이에 방이 바뀔 수 있지만 다음 목록 조회가 바로잡는다.
 *
 * <p><b>멤버 SET 의 원소는 문자열이다</b>({@code room} 은 {@code userId} 를 문자열로 든다) — 사용자 번호의 십진 문자열이어야 한다. {@code room} 이 지금
 * 인증 없이 돌아({@code TEMP-NO-PLATFORM}) 아무 문자열이나 들어올 수 있다 — <b>숫자가 아닌 값은 건너뛰고 WARN 을 남긴다.</b> 사용자 번호일 수 없는 값이라
 * 카드에도 파티원에도 실을 수 없다(그런 값은 {@code memberCount} 에서도 빠진다). 숫자지만 가입하지 않은 번호는 그대로 둔다 — 카드에 {@code null} 로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRoomStateReader implements RoomStateReader {

    private static final int COMMANDS_PER_ROOM = 3;

    private final StringRedisTemplate redis;

    @Override
    public Map<Long, RoomState> read(Collection<Long> roomIds)
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
                    connection.keyCommands().exists(bytes(RoomKeys.host(id)));
                    connection.setCommands().sMembers(bytes(RoomKeys.members(id)));
                    connection.keyCommands().exists(bytes(RoomKeys.confirmed(id)));
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
            states.put(ids.get(i), new RoomState(Boolean.TRUE.equals(results.get(at)),
                    userIds(ids.get(i), results.get(at + 1)), Boolean.TRUE.equals(results.get(at + 2))));
        }
        return states;
    }

    private static byte[] bytes(String key)
    {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code SMEMBERS} 의 결과 — 템플릿의 문자열 직렬화기가 이미 문자열로 풀어 준다. 없는 키는 빈 집합이다.
     * 사용자 번호로 팔 수 없는 값은 건너뛴다(WARN) — 값 자체는 로그에 남기지 않는다(무엇이 들어 있을지 모른다).
     */
    private static Set<Long> userIds(Long roomId, Object result)
    {
        Set<Long> members = new LinkedHashSet<>();
        if(result instanceof Collection<?> collection)
        {
            for(Object member : collection)
            {
                if(member == null)
                {
                    continue;
                }
                try
                {
                    members.add(Long.parseLong(member.toString()));
                }
                catch(NumberFormatException e)
                {
                    log.warn("멤버 SET 에 사용자 번호가 아닌 값이 있다 — 건너뛴다 roomId={}", roomId);
                }
            }
        }
        return members;
    }
}
