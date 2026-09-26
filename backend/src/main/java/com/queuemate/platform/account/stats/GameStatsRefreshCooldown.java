package com.queuemate.platform.account.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * <b>전적 갱신의 쿨타임</b> — 같은 게임 계정을 2분 안에 다시 갱신하지 못하게 한다(2026-09-24 소유자 결정 ·
 * {@code contracts/platform-api.md} "전적을 긁는 것"). 키는 <b>{@code qm:riot:refresh:{gameAccountId}}</b> 이고
 * {@code SET … NX PX} 로 찍는다. 접두사 {@code qm:riot:*} 는 이 앱의 것이다({@code qm:auth:*} 와 같은 자리).
 *
 * <p><b>{@link GameStatsSyncLock} 의 자물쇠 키와 다른 키다 — 뜻이 다르다.</b> 자물쇠는 "지금 돌고 있다"(60초)이고
 * 이것은 "최근에 했다"(2분)다. 하나로 합치면 갱신이 끝나 자물쇠가 풀리는 순간 다시 누를 수 있게 된다.
 *
 * <p><b>찍는 때는 긁기를 시작할 때다 — 실패해도 소모된다</b>(소유자 결정). 실패한 갱신만 무제한으로 다시 할 수 있으면
 * Riot 이 거절하는 동안 그 계정으로 한도를 계속 태울 수 있다.
 *
 * <p><b>Redis 가 죽으면 제한 없이 통과시킨다</b> — refresh 토큰의 발급 · 폐기({@code common.security.RefreshTokens})와 같은 원칙이고,
 * 전적 동기화의 자물쇠도 Redis 에 묻지 못하면 락 없이 진행한다. 그래서 이 클래스는 예외를 밖으로 내보내지 않는다.
 */
@Slf4j
@Component
public class GameStatsRefreshCooldown {

    /** 접두사는 이 한 곳에만 둔다. 계약에 적힌 이름이다 */
    static final String REFRESH_KEY_PREFIX = "qm:riot:refresh:";

    private final StringRedisTemplate redis;
    private final RiotProperties properties;

    public GameStatsRefreshCooldown(StringRedisTemplate redis, RiotProperties properties)
    {
        this.redis = redis;
        this.properties = properties;
    }

    /**
     * 쿨타임을 찍는다.
     *
     * @return 이미 찍혀 있으면 남은 시간(초, 올림 — 1 이상. 그대로 {@code Retry-After} 가 된다).
     *         찍었거나 <b>Redis 에 묻지 못했으면</b> 비어 있다 — 부르는 쪽은 그때 갱신을 진행한다
     */
    public OptionalLong start(Long gameAccountId)
    {
        try
        {
            // 값에는 시작한 시각을 적는다 — redis-cli 로 들여다볼 때 "언제 눌렀나"를 알 수 있게 한다. 판정에는 쓰지 않는다
            Boolean stamped = redis.opsForValue().setIfAbsent(key(gameAccountId),
                    Instant.now().truncatedTo(ChronoUnit.MILLIS).toString(), properties.refreshCooldown());
            if(Boolean.TRUE.equals(stamped))
            {
                return OptionalLong.empty();
            }
            Long remainingMillis = redis.getExpire(key(gameAccountId), TimeUnit.MILLISECONDS);
            if(remainingMillis == null || remainingMillis <= 0)
            {
                // 묻는 사이에 수명이 다했다(-2 는 키가 없다) — 막을 이유가 없어졌으니 통과시킨다
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.max(1L, (remainingMillis + 999) / 1000));
        }
        catch(RuntimeException e)
        {
            log.warn("전적 갱신 쿨타임을 확인하지 못했다 — 제한 없이 통과시킨다 gameAccountId={}: {}", gameAccountId, e.toString());
            return OptionalLong.empty();
        }
    }

    static String key(Long gameAccountId)
    {
        return REFRESH_KEY_PREFIX + gameAccountId;
    }
}
