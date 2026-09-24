package com.queuemate.platform.common.gameconfig;

import com.queuemate.platform.account.domain.Game;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code mode} · {@code tier} 가 <b>있는 값인지</b> gameconfig 에서 확인한다(2026-09-24 소유자 결정 — {@code contracts/platform-api.md} "gameconfig 를 읽는 것").
 * 모드는 {@code party}(모집 글), 티어는 {@code account}(게임 계정)가 쓴다 — 도메인 둘이 같이 쓰므로 {@code common} 에 있다.
 *
 * <p><b>왜 남의 앱 키를 읽어도 되는가</b> — gameconfig 는 {@code matching} 이 쓰는 상태가 아니다. 원본이 {@code matching/seed/gameconfig.redis} 파일이고
 * 그 머리가 "앱은 부팅 시 설정을 밀어넣지 않고 Redis 에서 읽기만 한다"고 적었다 — <b>쓰는 앱이 없고 {@code matching} 도 읽는 쪽이다.</b>
 * 운영자가 배포 때 심는 공유 설정이라(Parameter Store · ConfigMap 이 있을 자리다) 여러 서비스가 읽어도 된다. 바뀌는 계기가 사용자의 행동이 아니라 운영자의 배포라는 점이
 * {@code room} 의 방 키(§3.3 — 실시간으로 쓰이는 상태)와 다르다. 이것은 {@code CLAUDE.md} §2 · §11 의 "{@code qm:gameconfig:*} 접근 — 예외가 없다"를 개정한다.
 *
 * <p><b>읽기 전용이다 — 쓰는 명령이 없다.</b> 이 앱이 seed 를 심게 만들지 마라(그러면 모드를 하나 추가할 때마다 이 앱을 재배포해야 한다 — 설정을 데이터로 뺀 뜻이 사라진다).
 *
 * <p><b>fail-open 이다</b>(소유자 결정) — Redis 를 못 읽으면 <b>검증만 건너뛰고 통과시킨다.</b> 글 쓰기 · 게임 계정 연결이 gameconfig 에 묶여 같이 죽는 것보다
 * 이상한 모드가 들어오는 것이 낫다는 판단이고, 목록 조회가 이미 "Redis 를 못 읽으면 방 정보를 비운 채 글만 내려 준다"는 fail-open 인 것과 결을 맞춘 것이다.
 * 대가로 <b>Redis 가 죽은 동안에는 이상한 값이 들어올 수 있다</b> — WARN 한 줄을 남긴다. <b>fail-open 은 이 클래스 한 곳에만 있다</b> — 부르는 쪽은 "있는 값인가"만 묻는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameConfigReader {

    private final StringRedisTemplate redis;

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

    /** 값 자체는 로그에 남기지 않는다 — 사용자가 적은 문자열이다 */
    private static boolean failOpen(Game game, DataAccessException e)
    {
        log.warn("gameconfig 를 읽지 못해 검증을 건너뛴다 game={}: {}", game, e.toString());
        return true;
    }
}
