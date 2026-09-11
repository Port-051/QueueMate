package com.queuemate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 진짜 {@code application-prod.yml}을 읽어서 붙을 수 있는지 본다 (docs/07 §11).
 *
 * <p>sentinel로 운영하면 {@code REDIS_HOST}는 쓰이지 않는다. 그런데도 yml이 그 값을 필수로
 * 요구하면, sentinel만 설정한 배포가 placeholder 미해결로 기동에 실패한다. 그 회귀를 막는다.
 */
class RedisProdProfileTest {

    private final RedisConfig config = new RedisConfig();

    @Test
    @DisplayName("sentinel 변수만 있으면 REDIS_HOST 없이도 sentinel로 붙는다")
    void bootsWithSentinelVariablesAlone() {
        RedisProperties properties = bind(Map.of(
                "REDIS_SENTINEL_MASTER", "queuemate",
                "REDIS_SENTINEL_NODES", "host-a:26379,host-b:26379,host-c:26379"));

        assertThat(properties.getSentinel().getNodes()).hasSize(3);
        assertThat(config.topologyOf(properties))
                .isInstanceOfSatisfying(RedisSentinelConfiguration.class, sentinel ->
                        assertThat(sentinel.getMaster().getName()).isEqualTo("queuemate"));
    }

    @Test
    @DisplayName("REDIS_HOST만 있으면 단일 인스턴스로 붙는다")
    void bootsWithHostAlone() {
        RedisProperties properties = bind(Map.of("REDIS_HOST", "redis.internal"));

        assertThat(config.topologyOf(properties))
                .isInstanceOfSatisfying(RedisStandaloneConfiguration.class, standalone -> {
                    assertThat(standalone.getHostName()).isEqualTo("redis.internal");
                    assertThat(standalone.getPort()).isEqualTo(6379);
                });
    }

    @Test
    @DisplayName("아무것도 없으면 localhost로 붙지 않고 기동을 막는다")
    void refusesToBootWithNothingConfigured() {
        RedisProperties properties = bind(Map.of());

        assertThat(properties.getHost()).isEmpty();
        assertThatIllegalStateException().isThrownBy(() -> config.topologyOf(properties));
    }

    /** 운영 yml을 그대로 읽고, 주어진 환경 변수만 채운 상태로 바인딩한다. */
    private static RedisProperties bind(Map<String, Object> environment) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test-env", environment));
        env.getPropertySources().addLast(loadProdYaml());
        return Binder.get(env).bindOrCreate("spring.data.redis", RedisProperties.class);
    }

    private static PropertySource<?> loadProdYaml() {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("application-prod", new ClassPathResource("application-prod.yml"));
            return sources.get(0);
        } catch (IOException e) {
            throw new IllegalStateException("application-prod.yml을 읽지 못했다", e);
        }
    }
}
