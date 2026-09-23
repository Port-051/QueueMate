package com.queuemate.platform.account.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * 로그인 실패 제한 — <b>계정 단위</b>로 세고, 틀릴수록 잠금이 길어진다 ({@code contracts/platform-api.md} "계정"의 "로그인 실패 제한").
 *
 * <p>세는 열쇠는 <b>로그인 아이디</b>({@code loginId})다 — 사용자 번호가 아니다. 사용자를 찾기 전에 세야 하고(없는 아이디도 센다), 사용자 번호는
 * 로그인 응답으로 비로소 알게 되는 값이다. 횟수는 Redis 에 둔다 — 앱은 stateless 다(CLAUDE.md §5). 키는 둘이다.
 * <ul>
 *   <li>{@code qm:auth:login-fail:{loginId}} — 실패 횟수. 첫 실패 때 창({@code window})만큼의 수명을 건다</li>
 *   <li>{@code qm:auth:login-lock:{loginId}} — 잠금. <b>이 키가 있다 = 잠겨 있다.</b> 수명이 곧 잠금의 남은 시간이다({@code Retry-After})</li>
 * </ul>
 * {@code qm:auth:} 는 이 앱만 쓰는 접두사다 — {@code matching} 의 {@code qm:user:*} · {@code room} 의 {@code qm:room:*} 와 겹치지 않는다.
 *
 * <p><b>Redis 가 죽으면 제한 없이 통과시킨다</b> — 로그인이 Redis 에 묶이지 않게 한다. 그래서 이 클래스의 어떤 메서드도 예외를 밖으로 내보내지 않는다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class LoginThrottle {

    /** 접두사는 이 한 곳에만 둔다. 계약에 적힌 이름이다 */
    static final String FAIL_KEY_PREFIX = "qm:auth:login-fail:";
    static final String LOCK_KEY_PREFIX = "qm:auth:login-lock:";

    /**
     * 실패를 하나 세고, 허용 횟수에 닿았으면 잠근다 — <b>스크립트 하나다.</b> {@code INCR} 와 {@code EXPIRE} 를 따로 보내면 그 사이에 앱이 죽었을 때
     * 수명 없는 키가 남아 그 계정의 횟수가 영영 사라지지 않는다.
     *
     * <p>잠금의 길이는 {@code firstLock × 2^(횟수 − maxFailures)} 이고 {@code maxLock} 을 넘지 않는다. 잠글 때 횟수 키의 수명을
     * "잠금 + 창"으로 늘린다 — 안 그러면 긴 잠금이 끝날 때 횟수도 같이 사라져 두 배로 늘던 잠금이 처음(1분)으로 돌아간다.
     *
     * <p>KEYS[1] 횟수 키 · KEYS[2] 잠금 키 · ARGV[1] 허용 횟수 · ARGV[2] 창(ms) · ARGV[3] 첫 잠금(ms) · ARGV[4] 최대 잠금(ms). 돌려주는 값은 잠금의 길이(ms), 안 잠갔으면 0.
     */
    private static final RedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>("""
            local failures = redis.call('INCR', KEYS[1])
            if failures == 1 or redis.call('PTTL', KEYS[1]) < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            local maxFailures = tonumber(ARGV[1])
            if failures < maxFailures then
                return 0
            end
            local maxLock = tonumber(ARGV[4])
            local lock = tonumber(ARGV[3])
            local doublings = failures - maxFailures
            while doublings > 0 and lock < maxLock do
                lock = lock * 2
                doublings = doublings - 1
            end
            if lock > maxLock then
                lock = maxLock
            end
            redis.call('SET', KEYS[2], failures, 'PX', lock)
            redis.call('PEXPIRE', KEYS[1], lock + tonumber(ARGV[2]))
            return lock
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LoginThrottleProperties properties;

    public LoginThrottle(StringRedisTemplate redis, LoginThrottleProperties properties)
    {
        this.redis = redis;
        this.properties = properties;
    }

    /** 잠겨 있으면 남은 시간(초, 올림 — 1 이상). 잠겨 있지 않거나 <b>Redis 에 묻지 못했으면</b> 비어 있다 */
    public OptionalLong lockedForSeconds(String loginId)
    {
        try
        {
            Long remainingMillis = redis.getExpire(lockKey(loginId), TimeUnit.MILLISECONDS);
            if(remainingMillis == null || remainingMillis <= 0)
            {
                // -2 는 키가 없다, -1 은 수명이 없다(이 앱은 그렇게 쓰지 않는다) — 둘 다 잠기지 않은 것으로 본다
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.max(1L, (remainingMillis + 999) / 1000));
        }
        catch(RuntimeException e)
        {
            log.warn("로그인 실패 제한을 확인하지 못했다 — 제한 없이 통과시킨다: {}", e.toString());
            return OptionalLong.empty();
        }
    }

    /** 실패를 하나 센다. 허용 횟수에 닿았으면 잠근다 */
    public void recordFailure(String loginId)
    {
        try
        {
            Long lockedMillis = redis.execute(RECORD_FAILURE, List.of(failKey(loginId), lockKey(loginId)),
                    String.valueOf(properties.maxFailures()), millis(properties.window()),
                    millis(properties.firstLock()), millis(properties.maxLock()));
            if(lockedMillis != null && lockedMillis > 0)
            {
                log.warn("로그인 잠금 loginId={} lockedMillis={}", loginId, lockedMillis);
            }
        }
        catch(RuntimeException e)
        {
            log.warn("로그인 실패를 세지 못했다: {}", e.toString());
        }
    }

    /** 로그인에 성공했다 — 횟수와 잠금을 지운다 */
    public void clear(String loginId)
    {
        try
        {
            redis.delete(List.of(failKey(loginId), lockKey(loginId)));
        }
        catch(RuntimeException e)
        {
            log.warn("로그인 실패 횟수를 지우지 못했다: {}", e.toString());
        }
    }

    static String failKey(String loginId)
    {
        return FAIL_KEY_PREFIX + loginId;
    }

    static String lockKey(String loginId)
    {
        return LOCK_KEY_PREFIX + loginId;
    }

    private static String millis(Duration duration)
    {
        return String.valueOf(duration.toMillis());
    }
}
