package com.queuemate.config;

import io.lettuce.core.ReadFrom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;
import java.util.Set;

/**
 * Redis 연결 구성 (docs/07 §9·§11).
 *
 * <p>Spring Boot 자동 설정을 쓰지 않고 직접 만든다. 이유는 세 가지다.
 *
 * <p>첫째, sentinel 설정을 빈 문자열로 받으면 자동 설정은 그것을 "sentinel을 쓰겠다"로 읽는다.
 * 환경 변수는 설정되지 않은 것과 빈 값을 구분하지 못하는 배포가 많아, 그대로 두면
 * master 이름이 빈 sentinel 연결을 시도하다 뜨지 않는다. 여기서 공백을 명시적으로 걸러 낸다.
 *
 * <p>둘째, sentinel도 host도 없는 설정은 기동 자체를 막는다. 자동 설정은 이때 localhost로
 * 붙어 버려서, 빈 Redis를 정상으로 읽고 guard가 전부 통과한다.
 *
 * <p>셋째, <b>읽기는 반드시 master에서 한다</b>. replica 읽기를 켜면 복제 지연 동안
 * {@code active-request}/{@code active-proposal} guard가 낡은 값을 돌려주고, 그 순간
 * INV-1과 INV-2가 조용히 깨진다. 성능을 이유로 이 설정을 바꾸면 안 된다는 것을
 * 코드에 남겨 둔다.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder client =
                LettuceClientConfiguration.builder()
                        // 복제 지연이 곧 guard 오작동이다. 읽기를 replica로 보내지 않는다.
                        .readFrom(ReadFrom.MASTER);
        if (properties.getTimeout() != null) {
            client.commandTimeout(properties.getTimeout());
        }
        return new LettuceConnectionFactory(topologyOf(properties), client.build());
    }

    /** 테스트가 직접 부를 수 있게 열어 둔다. 어떤 토폴로지를 고르는지가 이 클래스의 전부다. */
    RedisConfiguration topologyOf(RedisProperties properties) {
        RedisProperties.Sentinel sentinel = properties.getSentinel();
        if (sentinel == null || !hasText(sentinel.getMaster()) || isEmpty(sentinel.getNodes())) {
            if (!hasText(properties.getHost())) {
                // 운영에서 접속 설정을 통째로 빠뜨린 경우다. localhost의 빈 Redis에 조용히
                // 붙는 것보다 뜨지 않는 편이 낫다 (application-prod.yml 첫 줄과 같은 판단).
                throw new IllegalStateException(
                        "Redis 접속 설정이 없다. REDIS_HOST를 주거나 "
                                + "REDIS_SENTINEL_MASTER/REDIS_SENTINEL_NODES를 채워라");
            }
            log.info("Redis 단일 인스턴스로 붙는다 host={} port={}",
                    properties.getHost(), properties.getPort());
            RedisStandaloneConfiguration standalone =
                    new RedisStandaloneConfiguration(properties.getHost(), properties.getPort());
            applyAuth(standalone::setUsername, standalone::setPassword, properties);
            return standalone;
        }

        // failover 중에는 명령이 실패한다. 그 창에서는 새 매칭을 만들지 않는다 (INV-10).
        log.info("Redis Sentinel로 붙는다 master={} nodes={}",
                sentinel.getMaster(), sentinel.getNodes());
        RedisSentinelConfiguration config =
                new RedisSentinelConfiguration(sentinel.getMaster(), Set.copyOf(sentinel.getNodes()));
        if (hasText(sentinel.getUsername())) {
            config.setSentinelUsername(sentinel.getUsername());
        }
        if (hasText(sentinel.getPassword())) {
            config.setSentinelPassword(sentinel.getPassword());
        }
        applyAuth(config::setUsername, config::setPassword, properties);
        return config;
    }

    /** master 인증 정보. sentinel 자신의 인증과는 별개다. */
    private void applyAuth(java.util.function.Consumer<String> username,
                           java.util.function.Consumer<String> password,
                           RedisProperties properties) {
        if (hasText(properties.getUsername())) {
            username.accept(properties.getUsername());
        }
        if (hasText(properties.getPassword())) {
            password.accept(properties.getPassword());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isEmpty(List<String> nodes) {
        return nodes == null || nodes.stream().noneMatch(RedisConfig::hasText);
    }
}
