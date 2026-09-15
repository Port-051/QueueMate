package com.queuemate.matching.infra;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis 정합성 테스트의 공통 바탕.
 *
 * <p>매칭은 Redis 동작에 정합성을 걸고 있으므로 embedded/mock으로 대체하지 않는다.
 * 진짜 Redis에 붙어서 Lua와 TTL 동작을 그대로 확인한다.
 */
@Testcontainers
abstract class RedisTestSupport {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    protected static StringRedisTemplate redis;

    @BeforeAll
    static void startRedis() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void flushRedis() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
