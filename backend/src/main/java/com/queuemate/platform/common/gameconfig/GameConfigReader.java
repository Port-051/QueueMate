package com.queuemate.platform.common.gameconfig;

import com.queuemate.platform.account.domain.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code mode} · {@code tier} 가 <b>있는 값인지</b> gameconfig 에서 확인한다(2026-09-24 소유자 결정 — {@code contracts/platform-api.md} "gameconfig 를 읽는 것").
 * 모드는 {@code party}(모집 글), 티어는 {@code account}(게임 계정)가 쓴다 — 도메인 둘이 같이 쓰므로 {@code common} 에 있다.
 * <b>2026-09-28 부터는 게시판 방 먼저 합류(P-28 · docs/11 D-40)가 모드 HASH 의 내용({@code tierRule} · {@code targetPartySize} · 2026-09-29 부터 {@code tierLadder}) · 티어의 단계 번호 ·
 * 티어별 허용 범위도 읽는다</b>({@link #modeConfig} · {@link #tierScores} · {@link #tierRanges} — {@code party.service.AutoJoinService}).
 * <b>2026-09-30 부터는 모집 글의 방장 포지션이 모드 HASH 의 {@code positionUniqueness} 를 읽는다</b>({@link #modePositions} — P-38).
 *
 * <p><b>왜 남의 앱 키를 읽어도 되는가</b> — gameconfig 는 {@code matching} 이 쓰는 상태가 아니다. 원본이 {@code matching/seed/gameconfig.redis} 파일이고
 * 그 머리가 "앱은 부팅 시 설정을 밀어넣지 않고 Redis 에서 읽기만 한다"고 적었다 — <b>쓰는 앱이 없고 {@code matching} 도 읽는 쪽이다.</b>
 * 운영자가 배포 때 심는 공유 설정이라(Parameter Store · ConfigMap 이 있을 자리다) 여러 서비스가 읽어도 된다. 바뀌는 계기가 사용자의 행동이 아니라 운영자의 배포라는 점이
 * {@code room} 의 방 키(§3.3 — 실시간으로 쓰이는 상태)와 다르다. 이것은 {@code CLAUDE.md} §2 · §11 의 "{@code qm:gameconfig:*} 접근 — 예외가 없다"를 개정한다.
 *
 * <p><b>읽기 전용이다 — 쓰는 명령이 없다.</b> 이 앱이 seed 를 심게 만들지 마라(그러면 모드를 하나 추가할 때마다 이 앱을 재배포해야 한다 — 설정을 데이터로 뺀 뜻이 사라진다).
 *
 * <p><b>정책이 둘이다 — 부르는 쪽이 다르다.</b>
 * <ul>
 *   <li><b>fail-open</b>({@link #hasMode} · {@link #hasTier} — 소유자 결정. 2026-09-30 의 {@link #modePositions} 도 이쪽이다) — Redis 를 못 읽으면 <b>검증만 건너뛰고 통과시킨다.</b> 글 쓰기 · 게임 계정 연결이 gameconfig 에
 *       묶여 같이 죽는 것보다 이상한 모드가 들어오는 것이 낫다는 판단이고, 목록 조회가 이미 "Redis 를 못 읽으면 방 정보를 비운 채 글만 내려 준다"는 fail-open 인 것과
 *       결을 맞춘 것이다. 대가로 <b>Redis 가 죽은 동안에는 이상한 값이 들어올 수 있다</b> — WARN 한 줄을 남긴다. <b>gameconfig 가 아예 안 심긴 Redis 도 통과시킨다</b>
 *       (검증할 원본이 없는 것과 값이 틀린 것은 다르다 — 가르는 열쇠는 티어 사다리 키다, {@link #seeded}).</li>
 *   <li><b>fail-closed</b>({@link #seeded} · {@link #modeConfig} · {@link #tierScores} · {@link #tierRanges} — 2026-09-28, 게시판 방 먼저 합류) — Redis 를 못 읽으면
 *       {@link GameConfigUnavailableException} 을 던지고 부르는 쪽이 503 {@code ROOM_STATE_UNAVAILABLE} 로 옮긴다. 그 요청은 곧이어 방 키(Redis)를 읽고 방에 넣어야
 *       하므로 어차피 Redis 없이는 끝낼 수 없다 — 검증을 건너뛰고 통과시켜도 얻는 것이 없다. <b>안 심긴 Redis 는 여기서도 통과다</b> — 부르는 쪽이 {@link #seeded} 로
 *       갈라 티어 검사와 모드 정원 검사를 건너뛴다(정원은 방 정원 5). 심긴 것과 못 읽은 것을 가르는 것은 같은 열쇠(티어 사다리 키)다.</li>
 * </ul>
 * 두 정책이 <b>이 클래스 한 곳에만</b> 있다 — 부르는 쪽은 "있는 값인가" · "값이 무엇인가" 만 묻는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameConfigReader {

    // ---- 모드별 설정 HASH 에서 읽는 필드의 이름 — 이 한 곳에만 둔다(원본은 matching/seed/gameconfig.redis) ----
    private static final String FIELD_TIER_RULE = "tierRule";
    private static final String FIELD_TARGET_PARTY_SIZE = "targetPartySize";
    /** 그 모드가 보는 티어 사다리(2026-09-29 — P-36 · docs/11 D-48). {@code tierRule NONE} 모드에는 없다 */
    private static final String FIELD_TIER_LADDER = "tierLadder";
    /** 그 모드에 포지션이 있는가(2026-09-30 — P-38). {@code "true"} 만 "있다" 다. PUBG 모드에는 없다 */
    private static final String FIELD_POSITION_UNIQUENESS = "positionUniqueness";
    private static final String TRUE = "true";

    private final StringRedisTemplate redis;

    // ---- fail-open — mode · tier 검증 (2026-09-24) ----

    /** 그 게임에 그 모드가 있는가 — 모드별 설정 HASH 의 {@code EXISTS} 하나다(내용은 읽지 않는다) */
    public boolean hasMode(Game game, String modeKey)
    {
        try
        {
            return Boolean.TRUE.equals(redis.hasKey(GameConfigKeys.mode(game, modeKey))) || notSeeded(game);
        }
        catch(DataAccessException e)
        {
            return failOpen(game, e);
        }
    }

    /** 그 게임의 티어 사다리에 있는 이름인가 — {@code ZSCORE} 가 {@code null} 이면 없는 티어다 */
    public boolean hasTier(Game game, String tierName)
    {
        try
        {
            return redis.opsForZSet().score(GameConfigKeys.tierLadder(game), tierName) != null || notSeeded(game);
        }
        catch(DataAccessException e)
        {
            return failOpen(game, e);
        }
    }

    /**
     * 값이 없는 것과 <b>gameconfig 가 아예 안 심긴 것</b>을 가른다 — 안 심겼으면 검증할 원본이 없으니 통과시킨다(fail-open 의 나머지 반쪽).
     * 가르는 열쇠는 <b>티어 사다리 키</b>다(세 게임 모두 사다리가 있고, 모드에는 목록 키가 없어 "하나도 없다"를 물을 데가 없다 — {@link GameConfigKeys}).
     * 안 심긴 Redis 로 앱을 띄우면 이 WARN 이 글 쓰기마다 나온다 — seed 를 부으라는 신호다.
     */
    private boolean notSeeded(Game game)
    {
        if(Boolean.TRUE.equals(redis.hasKey(GameConfigKeys.tierLadder(game))))
        {
            return false;
        }
        log.warn("gameconfig 가 없어 검증을 건너뛴다 game={} — matching/seed/gameconfig.redis 를 심어라", game);
        return true;
    }

    /**
     * 그 모드에 <b>포지션이 있는가</b> — 모드별 설정 HASH 의 {@code positionUniqueness} 한 필드다(2026-09-30 소유자 결정 — 모집 글의 방장 포지션, P-38).
     * {@code "true"} 면 {@link ModePositions#YES}, HASH 는 있는데 그 값이 아니면(없거나 {@code "false"}) {@link ModePositions#NO} 다.
     *
     * <p><b>fail-open 이다</b>({@link #hasMode} 와 같은 쪽) — Redis 를 못 읽거나 모드 HASH 가 없으면(gameconfig 가 안 심겼다 — 모드 검증을 지난 뒤라
     * 그것 말고는 없다) {@link ModePositions#UNKNOWN} 이고 WARN 한 줄을 남긴다. 부르는 쪽은 그때 방장 포지션을 요구하지도 거절하지도 않는다.
     */
    public ModePositions modePositions(Game game, String modeKey)
    {
        String key = GameConfigKeys.mode(game, modeKey);
        try
        {
            String value = redis.<String, String>opsForHash().get(key, FIELD_POSITION_UNIQUENESS);
            if(TRUE.equals(value))
            {
                return ModePositions.YES;
            }
            if(value != null || Boolean.TRUE.equals(redis.hasKey(key)))
            {
                return ModePositions.NO;
            }
            log.warn("gameconfig 에 그 모드가 없어 방장 포지션 검사를 건너뛴다 game={} — matching/seed/gameconfig.redis 를 심어라", game);
            return ModePositions.UNKNOWN;
        }
        catch(DataAccessException e)
        {
            log.warn("gameconfig 를 읽지 못해 방장 포지션 검사를 건너뛴다 game={}: {}", game, e.toString());
            return ModePositions.UNKNOWN;
        }
    }

    /** 값 자체는 로그에 남기지 않는다 — 사용자가 적은 문자열이다 */
    private static boolean failOpen(Game game, DataAccessException e)
    {
        log.warn("gameconfig 를 읽지 못해 검증을 건너뛴다 game={}: {}", game, e.toString());
        return true;
    }

    // ---- fail-closed — 게시판 방 먼저 합류 (2026-09-28 · P-28) ----

    /**
     * 그 게임의 gameconfig 가 심겼는가 — 티어 사다리 키의 {@code EXISTS}({@link #notSeeded} 와 같은 열쇠). {@code false} 면 부르는 쪽이 티어 검사 · 모드 정원 검사를 건너뛴다.
     *
     * @throws GameConfigUnavailableException Redis 를 못 읽었다
     */
    public boolean seeded(Game game)
    {
        try
        {
            boolean seeded = Boolean.TRUE.equals(redis.hasKey(GameConfigKeys.tierLadder(game)));
            if(!seeded)
            {
                log.warn("gameconfig 가 없어 티어 · 정원 검사를 건너뛴다 game={} — matching/seed/gameconfig.redis 를 심어라", game);
            }
            return seeded;
        }
        catch(DataAccessException e)
        {
            throw failClosed(game, e);
        }
    }

    /**
     * 모드별 설정 HASH 의 {@code tierRule} · {@code targetPartySize} · {@code tierLadder} — {@code HMGET} 한 번이다. <b>HASH 가 없으면 비어 있다</b>(필드가 다 {@code null} 로 온다 —
     * {@code matching} 의 validator 가 없는 모드를 아는 법과 같다). 필드 하나만 빠진 모드는 그 칸이 {@code null} 인 채로 돌려준다 — 판단은 부르는 쪽이 한다
     * ({@code tierLadder} 가 없는 옛 seed 도 그렇다 — 2026-09-29).
     *
     * @throws GameConfigUnavailableException Redis 를 못 읽었다
     */
    public Optional<ModeConfig> modeConfig(Game game, String modeKey)
    {
        try
        {
            List<String> values = redis.<String, String>opsForHash()
                    .multiGet(GameConfigKeys.mode(game, modeKey), List.of(FIELD_TIER_RULE, FIELD_TARGET_PARTY_SIZE, FIELD_TIER_LADDER));
            String tierRule = field(values, 0);
            String size = field(values, 1);
            String tierLadder = field(values, 2);
            if(tierRule == null && size == null && tierLadder == null)
            {
                return Optional.empty();
            }
            return Optional.of(new ModeConfig(tierRule, parseSize(size), tierLadder));
        }
        catch(DataAccessException e)
        {
            throw failClosed(game, e);
        }
    }

    /**
     * 티어 이름들의 사다리 단계 번호 — {@code ZMSCORE} 한 번이다. 사다리에 없는 이름은 결과에 <b>없다</b>(값이 {@code null} 인 채로 넣지 않는다).
     * 허용 범위 {@code MIN:MAX} 를 단계 번호로 옮겨 "내 티어가 그 사이인가" 를 비교하는 데 쓴다.
     *
     * @throws GameConfigUnavailableException Redis 를 못 읽었다
     */
    public Map<String, Double> tierScores(Game game, Collection<String> tierNames)
    {
        List<String> names = tierNames.stream().distinct().toList();
        if(names.isEmpty())
        {
            return Map.of();
        }
        try
        {
            List<Double> scores = redis.opsForZSet().score(GameConfigKeys.tierLadder(game), names.toArray());
            Map<String, Double> found = new LinkedHashMap<>();
            for(int i = 0; i < names.size(); i++)
            {
                Double score = (scores == null || i >= scores.size()) ? null : scores.get(i);
                if(score != null)
                {
                    found.put(names.get(i), score);
                }
            }
            return found;
        }
        catch(DataAccessException e)
        {
            throw failClosed(game, e);
        }
    }

    /**
     * 그 모드의 티어별 허용 범위 표에서 여러 티어의 줄 — {@code HMGET} 한 번이다. 값은 {@code MIN:MAX}(티어 이름 둘) 또는 {@code SOLO_ONLY} 이고,
     * <b>줄이 없는 티어는 결과에 없다</b>(표에 없는 티어는 규칙을 모르는 값이다 — {@code matching} 의 validator 와 같다).
     *
     * @throws GameConfigUnavailableException Redis 를 못 읽었다
     */
    public Map<String, String> tierRanges(Game game, String modeKey, Collection<String> tierNames)
    {
        List<String> names = tierNames.stream().distinct().toList();
        if(names.isEmpty())
        {
            return Map.of();
        }
        try
        {
            List<String> ranges = redis.<String, String>opsForHash().multiGet(GameConfigKeys.tierRange(game, modeKey), names);
            Map<String, String> found = new LinkedHashMap<>();
            for(int i = 0; i < names.size(); i++)
            {
                String range = (ranges == null || i >= ranges.size()) ? null : ranges.get(i);
                if(range != null)
                {
                    found.put(names.get(i), range);
                }
            }
            return found;
        }
        catch(DataAccessException e)
        {
            throw failClosed(game, e);
        }
    }

    private static String field(List<String> values, int index)
    {
        return (values == null || values.size() <= index) ? null : values.get(index);
    }

    private static Integer parseSize(String size)
    {
        if(size == null)
        {
            return null;
        }
        try
        {
            return Integer.valueOf(size.trim());
        }
        catch(NumberFormatException e)
        {
            log.warn("gameconfig 의 targetPartySize 가 숫자가 아니다 — 무시한다");
            return null;
        }
    }

    private static GameConfigUnavailableException failClosed(Game game, DataAccessException e)
    {
        log.warn("gameconfig 를 읽지 못했다 — 게시판 방 먼저 합류를 거절한다 game={}: {}", game, e.toString());
        return new GameConfigUnavailableException(e);
    }
}
