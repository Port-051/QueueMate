package com.queuemate.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import com.queuemate.common.metrics.QueueMateMetrics;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.queuemate.common.ratelimit.RateLimiter.OnUnavailable.ALLOW;
import static com.queuemate.common.ratelimit.RateLimiter.OnUnavailable.REJECT;

/** 실제 Redis로 카운터 동작을 보고, 장애 동작은 mock으로 본다. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private RedisRateLimiter limiter;
    /** 지표는 여기서 재는 대상이 아니다. 값을 버리는 레지스트리로 둔다. */
    private final QueueMateMetrics metrics =
            new QueueMateMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        var factory = new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        limiter = new RedisRateLimiter(redis, metrics);
    }

    @Test
    void 한도까지는_통과하고_넘으면_막는다() {
        String identity = UUID.randomUUID().toString();

        for (int i = 1; i <= 5; i++) {
            assertTrue(limiter.tryAcquire("signal", identity, 5, Duration.ofSeconds(10), ALLOW),
                    i + "번째는 통과해야 한다");
        }
        assertFalse(limiter.tryAcquire("signal", identity, 5, Duration.ofSeconds(10), ALLOW),
                "6번째는 막혀야 한다");
    }

    @Test
    void scope가_다르면_한도를_따로_센다() {
        String identity = UUID.randomUUID().toString();
        limiter.tryAcquire("signal", identity, 1, Duration.ofSeconds(10), ALLOW);

        // 기능마다 한도가 달라야 하므로 signal이 login 한도를 깎으면 안 된다.
        assertTrue(limiter.tryAcquire("login", identity, 1, Duration.ofSeconds(10), ALLOW));
    }

    @Test
    void 사용자가_다르면_한도를_따로_센다() {
        limiter.tryAcquire("signal", UUID.randomUUID().toString(), 1, Duration.ofSeconds(10), ALLOW);

        assertTrue(limiter.tryAcquire("signal", UUID.randomUUID().toString(), 1,
                Duration.ofSeconds(10), ALLOW));
    }

    @Test
    void 창이_지나면_다시_허용된다() throws Exception {
        String identity = UUID.randomUUID().toString();
        Duration window = Duration.ofMillis(500);
        // 고정 창은 키에 창 번호가 들어간다. 창이 끝나갈 때 시작하면 두 호출이 서로 다른 창에
        // 떨어져 두 번째도 통과한다. 창 시작 직후로 맞춰야 의도한 것을 잰다.
        awaitWindowStart(window);

        assertTrue(limiter.tryAcquire("signal", identity, 1, window, ALLOW));
        assertFalse(limiter.tryAcquire("signal", identity, 1, window, ALLOW));

        Thread.sleep(window.toMillis());

        assertTrue(limiter.tryAcquire("signal", identity, 1, window, ALLOW), "창이 바뀌면 다시 허용된다");
    }

    private static void awaitWindowStart(Duration window) {
        try {
        long millis = window.toMillis();
        Thread.sleep(millis - (System.currentTimeMillis() % millis) + 20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void 카운터_키에_만료가_걸린다() {
        String identity = UUID.randomUUID().toString();
        limiter.tryAcquire("signal", identity, 10, Duration.ofSeconds(10), ALLOW);

        // 만료가 없으면 창이 지나도 키가 남아 메모리가 계속 자란다.
        List<String> keys = redis.keys("qm:rate:signal:" + identity + ":*").stream().toList();
        assertEquals(1, keys.size());
        Long ttl = redis.getExpire(keys.get(0));
        assertTrue(ttl != null && ttl > 0, "만료가 걸려 있어야 한다. ttl=" + ttl);
    }

    @Test
    void Redis가_죽으면_통과시킨다() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        // fail-open이다. 여기서 막으면 Redis 장애가 통화 중인 연결까지 끊는다.
        assertTrue(new RedisRateLimiter(broken, metrics)
                .tryAcquire("signal", "user", 1, Duration.ofSeconds(10), ALLOW));
    }

    @Test
    void REJECT면_Redis가_죽었을_때_예외를_낸다() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        // 로그인처럼 통과시키면 방어가 통째로 사라지는 자리다.
        assertThrows(RateLimitUnavailableException.class, () -> new RedisRateLimiter(broken, metrics)
                .tryAcquire("login", "user", 1, Duration.ofSeconds(10), REJECT));
    }

    @Test
    void reset하면_카운터가_다시_찬다() {
        String identity = UUID.randomUUID().toString();
        Duration window = Duration.ofSeconds(30);
        awaitWindowStart(window);
        assertTrue(limiter.tryAcquire("login", identity, 1, window, REJECT));
        assertFalse(limiter.tryAcquire("login", identity, 1, window, REJECT));

        limiter.reset("login", identity, window);

        // 로그인에 성공하면 그동안의 실패를 없던 일로 본다.
        assertTrue(limiter.tryAcquire("login", identity, 1, window, REJECT));
    }
}
