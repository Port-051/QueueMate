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

import java.time.Duration;
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
 * <p>둘째, 명령 타임아웃에 기본값을 준다. Lettuce 기본값 60초는 failover 6초짜리 장애를
 * 분 단위 정지로 바꾼다. 자동 설정은 값이 없으면 그 60초를 그대로 쓴다.
 *
 * <p>셋째, <b>읽기는 반드시 master에서 한다</b>. replica 읽기를 켜면 복제 지연 동안
 * {@code active-request}/{@code active-proposal} guard가 낡은 값을 돌려주고, 그 순간
 * INV-1과 INV-2가 조용히 깨진다. 성능을 이유로 이 설정을 바꾸면 안 된다는 것을
 * 코드에 남겨 둔다.
 */
@Configuration
public class RedisConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    /**
     * 설정이 없을 때 쓰는 명령 타임아웃.
     *
     * <p>Lettuce 기본값은 60초다. 그대로 두면 failover 동안 매칭 trigger 스레드 4개가
     * 각각 최대 60초를 죽은 master에 매달린 채 보내고, 그 사이 들어온 일감이 큐(512)를 채우다
     * 넘치면 버려진다. down-after 5초 + 선출·승격이 끝나는 데 실측 6초대이므로
     * 그보다 짧게 끊고 다시 물어보는 편이 낫다.
     *
     * <p>2초로 둔 근거는 이 서비스가 감당할 수 있는 재시도 간격이다. 제안 TTL이 20초라
     * 2초 타임아웃이면 한 요청이 실패를 확인하고 물러나기까지 예산의 10%만 쓴다.
     * 더 짧게 잡으면 평소의 느린 Lua 한 번을 장애로 오인한다.
     */
    static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(2);

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        Duration timeout = properties.getTimeout() == null
                ? DEFAULT_COMMAND_TIMEOUT
                : properties.getTimeout();
        LettuceClientConfiguration client =
                LettuceClientConfiguration.builder()
                        // 복제 지연이 곧 guard 오작동이다. 읽기를 replica로 보내지 않는다.
                        .readFrom(ReadFrom.MASTER)
                        .commandTimeout(timeout)
                        .build();
        log.info("Redis 명령 타임아웃 {}ms", timeout.toMillis());
        return new LettuceConnectionFactory(topologyOf(properties), client);
    }

    /** 테스트가 직접 부를 수 있게 열어 둔다. 어떤 토폴로지를 고르는지가 이 클래스의 전부다. */
    RedisConfiguration topologyOf(RedisProperties properties) {
        RedisProperties.Sentinel sentinel = properties.getSentinel();
        if (sentinel == null || !hasText(sentinel.getMaster()) || isEmpty(sentinel.getNodes())) {
            if (!hasText(properties.getHost())) {
                throw new IllegalStateException("Redis 접속 설정이 없다. REDIS_HOST 또는 Sentinel 설정이 필요하다");
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
