package com.queuemate.platform.party.match;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

/**
 * {@code matching} 의 <b>확정된 파티 HASH</b>({@code qm:party:{partyId}})를 읽는다(2026-09-27 소유자 결정 — docs/11 D-42.
 * 창구의 이름 · 자리 · 빈 값의 처리는 Claude 가 정한 세부다). gameconfig 를 읽는 {@code common.gameconfig.GameConfigReader} 가 본보기다 —
 * 남의 앱 키를 <b>읽기만</b> 하고 쓰는 명령이 없다({@link MatchPartyKeys}).
 *
 * <p><b>읽는 것은 {@code HGETALL} 한 번이다.</b> {@code status} 가 {@code CONFIRMED} 가 아니면(아직 제안 중이다 · 수명이 다해 사라졌다 · 그런 파티가 없다)
 * 비어 있다 — 부르는 쪽은 그 셋을 가르지 않고 404 다. {@code game} · {@code target} 이 없거나 팔 수 없으면 HASH 가 혼자 읽어도 되게 만들어지지 않은 것이라
 * <b>역시 "없는 파티"로 다룬다</b>(WARN 한 줄) — 반쯤 읽은 값으로 파티를 만들지 않는다. {@code member:} 필드 가운데 사용자 번호가 아닌 것은 건너뛴다
 * ({@code room.domain.RoomMemberIds} 와 같은 정책 · WARN).
 *
 * <p><b>fail-closed 다 — {@code GameConfigReader} 와 다르다.</b> 그쪽은 검증을 건너뛰면 될 뿐이지만 여기는 읽은 값으로 파티와 방을 만든다 — Redis 에 닿지 못했는데
 * "없는 파티"(404)라고 답하면 파티원이 방을 못 만든 채 확정된 파티가 증발한다. 그래서 {@code DataAccessException} 은 삼키지 않고 방의 Redis 장애와 같은
 * 503 {@code ROOM_STATE_UNAVAILABLE}({@code Retry-After: 5})로 옮긴다 — {@code room.service.RoomRedis} 와 같은 처리다(그 클래스는 패키지 안에서만 보여 여기서 따라 적었다).
 * 프런트는 잠시 뒤 다시 부른다 — HASH 는 600초 남아 있다.
 *
 * <p><b>팀원 카드용 읽기는 따로다</b>({@link #findRoster} — 2026-10-01). 제안 중인 파티도 읽고, 못 읽으면 503 대신 예외로 알려 부르는 쪽이 DB 를 한 번 더 본다.
 * 위의 규칙(확정만 · fail-closed 503)은 방 만들기의 것({@link #findConfirmed})이고 그대로다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchPartyReader {

    private final StringRedisTemplate redis;

    /**
     * 확정된 파티. 없거나 · 확정이 아니거나 · 혼자 읽을 수 없는 HASH 면 비어 있다.
     *
     * @throws com.queuemate.platform.common.error.ApiException 503 {@code ROOM_STATE_UNAVAILABLE} — Redis 에 닿지 못했다
     */
    public Optional<MatchParty> findConfirmed(String partyId)
    {
        Map<Object, Object> fields;
        try
        {
            fields = redis.opsForHash().entries(MatchPartyKeys.partyKey(partyId));
        }
        catch(DataAccessException e)
        {
            log.error("Redis 에 닿지 못해 자동 매칭 파티를 읽지 못했다 partyId={}", partyId, e);
            throw RoomErrors.stateUnavailable();
        }
        // 없는 키의 HGETALL 은 빈 맵이다
        if(fields == null || fields.isEmpty() || !"CONFIRMED".equals(fields.get("status")))
        {
            return Optional.empty();
        }
        Optional<Game> game = Game.fromName(text(fields, "game"));
        Integer target = integer(fields, "target");
        if(game.isEmpty() || target == null || target <= 0)
        {
            // 값 자체는 남기지 않는다 — matching 이 적은 것이라도 무엇이 들어 있을지 모른다
            log.warn("자동 매칭 파티 HASH 에 game · target 이 없거나 팔 수 없다 — 없는 파티로 다룬다 partyId={}", partyId);
            return Optional.empty();
        }
        return Optional.of(new MatchParty(partyId, game.get(), text(fields, "modeKey"), text(fields, "voicePreference"),
                text(fields, "playPurpose"), target, new LinkedHashSet<>(members(partyId, fields).keySet()), confirmedAt(fields)));
    }

    /**
     * <b>제안 중({@code PENDING})이거나 확정된({@code CONFIRMED}) 파티의 파티원</b> — 퀵 매칭 파티의 팀원 카드가 쓴다(2026-10-01 소유자 결정 —
     * {@code MatchPartyService#members}). {@link #findConfirmed} 와 같은 {@code HGETALL} 한 번이고 역시 읽기만 한다.
     *
     * <p>{@code status} 가 둘 중 하나가 아니면 비어 있다 — 아직 사람을 모으는 파티(정원이 차기 전에는 {@code status} 가 없다)와 제안이 거절 · 만료돼
     * 다시 모으는 파티는 "매칭된 파티" 가 아니다(Claude 가 정한 세부). 확정 뒤에만 적히는 {@code game} 은 없을 수 있다({@link MatchPartyRoster#game}).
     *
     * <p><b>못 읽으면 503 으로 바꾸지 않고 {@link RoomStateUnavailableException} 을 던진다</b> — {@link #findConfirmed} 와 다르다. 이 조회는 HASH 가 없을 때
     * DB 의 파티 기록으로 한 번 더 볼 수 있어서, fail-closed 로 끝낼지는 부르는 쪽이 DB 까지 본 뒤에 정한다({@code room.service.RoomService#states} 와 같은 짜임).
     *
     * @throws RoomStateUnavailableException Redis 에 닿지 못했다
     */
    public Optional<MatchPartyRoster> findRoster(String partyId)
    {
        Map<Object, Object> fields;
        try
        {
            fields = redis.opsForHash().entries(MatchPartyKeys.partyKey(partyId));
        }
        catch(DataAccessException e)
        {
            log.warn("Redis 에 닿지 못해 자동 매칭 파티의 파티원을 읽지 못했다 partyId={}: {}", partyId, e.toString());
            throw new RoomStateUnavailableException(e);
        }
        Object status = (fields == null) ? null : fields.get("status");
        if(!"PENDING".equals(status) && !"CONFIRMED".equals(status))
        {
            return Optional.empty();
        }
        return Optional.of(new MatchPartyRoster(partyId, Game.fromName(text(fields, "game")).orElse(null), members(partyId, fields)));
    }

    /** {@code member:{userId}} 필드 — 사용자 번호 → 값. 사용자 번호가 아닌 필드는 건너뛴다({@code room.domain.RoomMemberIds} 와 같은 정책 · WARN) */
    private static Map<Long, String> members(String partyId, Map<Object, Object> fields)
    {
        Map<Long, String> members = new LinkedHashMap<>();
        for(Map.Entry<Object, Object> field : fields.entrySet())
        {
            String name = String.valueOf(field.getKey());
            if(!name.startsWith(MatchPartyKeys.MEMBER_FIELD_PREFIX))
            {
                continue;
            }
            try
            {
                members.put(Long.parseLong(name.substring(MatchPartyKeys.MEMBER_FIELD_PREFIX.length())),
                        field.getValue() == null ? null : String.valueOf(field.getValue()));
            }
            catch(NumberFormatException e)
            {
                log.warn("자동 매칭 파티 HASH 에 사용자 번호가 아닌 파티원이 있다 — 건너뛴다 partyId={}", partyId);
            }
        }
        return members;
    }

    private static Instant confirmedAt(Map<Object, Object> fields)
    {
        String raw = text(fields, "confirmedAt");
        if(raw == null)
        {
            return null;
        }
        try
        {
            return Instant.ofEpochMilli(Long.parseLong(raw));
        }
        catch(NumberFormatException e)
        {
            return null;
        }
    }

    private static String text(Map<Object, Object> fields, String name)
    {
        Object value = fields.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Map<Object, Object> fields, String name)
    {
        String raw = text(fields, name);
        if(raw == null)
        {
            return null;
        }
        try
        {
            return Integer.parseInt(raw);
        }
        catch(NumberFormatException e)
        {
            return null;
        }
    }
}
