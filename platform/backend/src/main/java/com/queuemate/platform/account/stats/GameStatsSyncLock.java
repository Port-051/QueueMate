package com.queuemate.platform.account.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * <b>같은 게임 계정을 동시에 여러 번 긁지 않게 하는 자물쇠.</b> 키는 {@code qm:riot:sync:{gameAccountId}} 이고
 * {@code SET … NX EX 60} 으로 잡는다 ({@code contracts/platform-api.md} "게임 프로필"). 접두사 {@code qm:riot:*} 는 이 앱의 것이다 —
 * {@code qm:auth:*}(refresh)와 같은 자리이고, {@code matching} 의 {@code qm:user:*} · {@code room} 의 {@code qm:room:*} 와 겹치지 않는다.
 *
 * <p><b>못 잡으면 줄 서지 않고 건너뛴다</b> — 지금 누군가 같은 계정을 긁고 있으니 한 번 더 긁을 이유가 없다.
 *
 * <p><b>Redis 가 죽으면 락 없이 진행한다</b>({@link Acquired#NO_REDIS}) — 전적 때문에 기능이 멈추면 안 된다. 대가는 그동안 같은 계정을
 * 두 번 긁을 수 있다는 것이고, 저장이 upsert 라 결과는 같다(요청만 두 배로 나간다).
 *
 * <p>푸는 것은 <b>내가 잡은 자물쇠일 때만</b>이다 — 값에 이 사이클의 표를 적어 두고 그 표가 같을 때만 지운다.
 * 수명(60초)보다 오래 걸린 사이클이 남의 자물쇠를 풀어 버리지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameStatsSyncLock {

    /** 접두사는 이 한 곳에만 둔다. 계약에 적힌 이름이다 */
    static final String SYNC_LOCK_PREFIX = "qm:riot:sync:";

    /** 한 번 긁는 데 이보다 오래 걸리면 자물쇠가 저절로 풀린다 — 앱이 죽어도 남지 않게 한다 */
    static final Duration LOCK_TTL = Duration.ofSeconds(60);

    /** KEYS[1] 자물쇠 키 · ARGV[1] 내 표. 내 표일 때만 지운다 */
    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    /** 자물쇠를 잡아 봤을 때의 결과 */
    public enum Acquired {
        /** 잡았다 — 끝나면 {@link GameStatsSyncLock#release} 로 푼다 */
        YES,
        /** 누가 잡고 있다 — <b>건너뛴다</b> */
        NO,
        /** Redis 에 묻지 못했다 — <b>락 없이 진행한다</b>. 풀 것도 없다 */
        NO_REDIS
    }

    /** 잡았으면 {@link Token}, 누가 잡고 있으면 {@code null} */
    public record Token(Long gameAccountId, String value, Acquired acquired) {

        public boolean proceed()
        {
            return acquired != Acquired.NO;
        }
    }

    public Token acquire(Long gameAccountId)
    {
        String value = UUID.randomUUID().toString();
        try
        {
            Boolean set = redis.opsForValue().setIfAbsent(key(gameAccountId), value, LOCK_TTL);
            if(Boolean.TRUE.equals(set))
            {
                return new Token(gameAccountId, value, Acquired.YES);
            }
            return new Token(gameAccountId, value, Acquired.NO);
        }
        catch(RuntimeException e)
        {
            log.warn("전적 동기화 락을 잡지 못했다 — 락 없이 진행한다 gameAccountId={}: {}", gameAccountId, e.toString());
            return new Token(gameAccountId, value, Acquired.NO_REDIS);
        }
    }

    /** 푼다. 내가 잡은 것이 아니면(이미 수명이 다해 남이 잡았다) 아무것도 하지 않는다 */
    public void release(Token token)
    {
        if(token == null || token.acquired() != Acquired.YES)
        {
            return;
        }
        try
        {
            redis.execute(RELEASE, List.of(key(token.gameAccountId())), token.value());
        }
        catch(RuntimeException e)
        {
            // 수명이 있으니 저절로 풀린다 — 실패가 본 작업에 영향을 주면 안 된다
            log.warn("전적 동기화 락을 풀지 못했다 gameAccountId={}: {}", token.gameAccountId(), e.toString());
        }
    }

    static String key(Long gameAccountId)
    {
        return SYNC_LOCK_PREFIX + gameAccountId;
    }
}
