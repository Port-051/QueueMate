package com.queuemate.config;

import io.lettuce.core.ReadFrom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * 어떤 토폴로지로 붙을지 고르는 판단 (docs/07 §11).
 *
 * <p>배포 환경은 "설정하지 않음"과 "빈 값"을 구분하지 못하는 경우가 많다. 빈 sentinel 설정을
 * 받고 sentinel로 붙으려 들면 애플리케이션이 아예 뜨지 않는다. 그 경계를 여기서 고정한다.
 */
class RedisConfigTest {

    private final RedisConfig config = new RedisConfig();

    @Test
    @DisplayName("타임아웃을 주지 않아도 Lettuce 기본값 60초로 떨어지지 않는다")
    void appliesDefaultCommandTimeout() {
        // 60초가 걸리면 failover 6초짜리 장애 동안 trigger 스레드가 통째로 묶인다.
        RedisProperties properties = new RedisProperties();
        properties.setHost("redis");

        assertThat(config.redisConnectionFactory(properties).getTimeout())
                .isEqualTo(RedisConfig.DEFAULT_COMMAND_TIMEOUT.toMillis())
                .isLessThan(Duration.ofSeconds(60).toMillis());
    }

    @Test
    @DisplayName("타임아웃을 주면 그 값을 쓴다")
    void respectsConfiguredCommandTimeout() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("redis");
        properties.setTimeout(Duration.ofMillis(750));

        assertThat(config.redisConnectionFactory(properties).getTimeout()).isEqualTo(750);
    }

    @Test
    @DisplayName("sentinel 설정이 아예 없으면 단일 인스턴스로 붙는다")
    void fallsBackToStandalone() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("redis");
        properties.setPort(6380);

        assertThat(config.topologyOf(properties))
                .isInstanceOfSatisfying(RedisStandaloneConfiguration.class, standalone -> {
                    assertThat(standalone.getHostName()).isEqualTo("redis");
                    assertThat(standalone.getPort()).isEqualTo(6380);
                });
        assertThat(config.redisConnectionFactory(properties).isRedisSentinelAware()).isFalse();
    }

    @Test
    @DisplayName("sentinel 설정이 빈 문자열이면 sentinel로 붙지 않는다")
    void treatsBlankSentinelConfigAsAbsent() {
        RedisProperties properties = sentinel("", List.of());
        properties.setHost("redis");

        assertThat(config.topologyOf(properties)).isInstanceOf(RedisStandaloneConfiguration.class);

        // 노드만 있고 master 이름이 비어도 마찬가지다. 감시할 대상을 모르는 설정이다.
        properties.getSentinel().setNodes(List.of("sentinel-1:26379"));
        assertThat(config.topologyOf(properties)).isInstanceOf(RedisStandaloneConfiguration.class);

        // 반대로 master 이름만 있고 노드가 없으면 어디에 물어볼지를 모른다.
        properties.getSentinel().setMaster("queuemate");
        properties.getSentinel().setNodes(List.of("  "));
        assertThat(config.topologyOf(properties)).isInstanceOf(RedisStandaloneConfiguration.class);
    }

    @Test
    @DisplayName("master 이름과 노드가 모두 있으면 sentinel로 붙는다")
    void usesSentinelWhenConfigured() {
        RedisProperties properties = sentinel("queuemate",
                List.of("sentinel-1:26379", "sentinel-2:26379", "sentinel-3:26379"));

        LettuceConnectionFactory factory = config.redisConnectionFactory(properties);

        assertThat(factory.isRedisSentinelAware()).isTrue();
        RedisSentinelConfiguration sentinel = factory.getSentinelConfiguration();
        assertThat(sentinel).isNotNull();
        assertThat(sentinel.getMaster().getName()).isEqualTo("queuemate");
        assertThat(sentinel.getSentinels()).hasSize(3);
    }

    @Test
    @DisplayName("읽기는 언제나 master에서 한다")
    void neverReadsFromReplica() {
        RedisProperties properties = sentinel("queuemate", List.of("sentinel-1:26379"));

        // replica 읽기를 켜면 복제 지연 동안 guard가 낡은 값을 준다.
        // 그 순간 INV-1과 INV-2가 조용히 깨진다. 성능 튜닝으로 바꿀 수 있는 값이 아니다.
        assertThat(config.redisConnectionFactory(properties).getClientConfiguration().getReadFrom())
                .contains(ReadFrom.MASTER);
    }

    @Test
    @DisplayName("단일 인스턴스에서도 읽기 설정은 같다")
    void keepsMasterReadsOnStandalone() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("redis");

        assertThat(config.redisConnectionFactory(properties).getClientConfiguration().getReadFrom())
                .contains(ReadFrom.MASTER);
    }

    @Test
    @DisplayName("host도 sentinel도 비어 있으면 기동을 막는다")
    void failsWhenNeitherTopologyIsConfigured() {
        // 운영 프로파일은 host 기본값이 없다. 여기서 막지 않으면 자동 설정이 localhost로
        // 붙고, 빈 Redis가 모든 guard를 통과시킨다.
        RedisProperties properties = sentinel("", List.of());
        properties.setHost("");

        assertThatIllegalStateException()
                .isThrownBy(() -> config.topologyOf(properties))
                .withMessageContaining("REDIS_HOST")
                .withMessageContaining("REDIS_SENTINEL_MASTER");
    }

    private static RedisProperties sentinel(String master, List<String> nodes) {
        RedisProperties properties = new RedisProperties();
        RedisProperties.Sentinel sentinel = new RedisProperties.Sentinel();
        sentinel.setMaster(master);
        sentinel.setNodes(nodes);
        properties.setSentinel(sentinel);
        return properties;
    }
}
