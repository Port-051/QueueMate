package com.queuemate.matching.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SentinelServersConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson - 분산 락 전용.
 *
 * <p><b>데이터는 여기로 다루지 않는다.</b> 파티 HASH / needs ZSET / active-request HASH 는
 * 계속 {@code StringRedisTemplate} 과 Lua 가 담당한다. Redisson 은 {@code qm:lock:*} 키만
 * 만진다. 이 선을 넘으면 두 라이브러리가 같은 키를 다른 형식으로 쓰게 된다.
 *
 * <p><b>왜 spring-boot-starter 가 아니라 코어를 쓰나.</b> starter 의 자동 설정은 커넥션 풀을
 * 기본값(마스터 64 / 구독 50)으로 잡는다. 락만 쓰는 데 그만큼 필요 없다.
 *
 * <p><b>Lettuce 와 별개의 연결이다.</b> Spring Data Redis 쪽은 멀티플렉싱으로 연결 하나를
 * 공유하지만 Redisson 은 자기 풀을 따로 만든다. 둘이 서로의 연결을 쓰지 않는다.
 *
 * <p><b>단일 노드 / Sentinel 두 갈래다.</b> Lettuce 는 {@code spring.data.redis.sentinel.nodes}
 * 가 있으면 Spring Boot 자동 설정이 알아서 Sentinel 구성을 만들지만, Redisson 은 자동 설정을
 * 쓰지 않으므로 같은 프로퍼티를 여기서 직접 읽어 갈래를 나눈다. 한쪽만 Sentinel 로 바꾸면
 * claim 은 복구되는데 배정만 계속 실패하는, 겉보기로는 멀쩡한 상태가 된다.
 * 값이 비어 있으면 지금까지의 단일 노드 동작 그대로다 - 페일오버 전/후를 재빌드 없이
 * 환경 변수만으로 오갈 수 있게 하려는 것이다.
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.sentinel.master:}") String sentinelMaster,
            @Value("${queuemate.lock.sentinel.nodes:${spring.data.redis.sentinel.nodes:}}") String sentinelNodes) {

        Config config = new Config();

        // 기본 32. 락 획득/해제만 하는 앱에 그만큼 필요 없다.
        config.setNettyThreads(4);

        // 기본 코덱은 바이너리라 redis-cli 로 봐도 안 읽힌다.
        // 문자열로 두면 락 상태를 눈으로 확인할 수 있고, 혹시 데이터를 건드리게 돼도
        // StringRedisTemplate 이 쓴 것과 형식이 맞는다.
        config.setCodec(StringCodec.INSTANCE);

        if (sentinelNodes.isBlank()) {
            singleServer(config, host, port);
        } else {
            sentinelServers(config, sentinelMaster, sentinelNodes);
        }

        return Redisson.create(config);
    }

    /** 지금까지의 동작. master 주소가 고정이라 페일오버를 인지하지 못한다. */
    private void singleServer(Config config, String host, int port) {
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)

                // 기본 24 / 64. 락만 쓰므로 대폭 줄인다.
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)

                // 기본 1 / 50. RLock 은 락이 풀렸을 때 대기자에게 알리려고 pub/sub 을 쓴다.
                // 0 으로 두면 락 대기 자체가 동작하지 않으므로 최소한은 남긴다.
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2)

                // 명령 응답 대기 상한. application.yaml 의 Redis timeout 2s 와 맞춘다.
                .setTimeout(2000);
    }

    /** Sentinel 에게 현재 master 를 물어 붙는다. 페일오버 시 새 master 로 따라간다. */
    private void sentinelServers(Config config, String masterName, String nodes) {
        SentinelServersConfig sentinel = config.useSentinelServers()
                .setMasterName(masterName)

                // RLock 은 쓰기라 어차피 master 로 가지만, 명시해서 오해를 없앤다.
                // 승격 직후 replica 로 새는 읽기가 있으면 락 상태 판정이 흔들린다.
                .setReadMode(ReadMode.MASTER)

                // 풀 크기 근거는 단일 노드 판과 같다. Sentinel 판은 master/slave 별로 나뉜다.
                .setMasterConnectionMinimumIdleSize(2)
                .setMasterConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2)
                .setTimeout(2000)

                // 페일오버 중 재시도. 이 둘의 곱(3 x 500ms = 1.5s)이 실질 상한이고,
                // PoolLock.WAIT_MILLIS(3000) 안에 들어가야 락 획득 자체가 포기되지 않는다.
                .setRetryAttempts(3)
                .setRetryInterval(500)

                // Sentinel 에게 master 주소 변경을 확인하는 주기.
                // Redisson 쪽 복구 시간을 사실상 이 값이 지배한다 (EXPERIMENTS.md 실험 2).
                .setScanInterval(1000);

        for (String node : nodes.split(",")) {
            String trimmed = node.trim();
            if (!trimmed.isEmpty()) {
                sentinel.addSentinelAddress("redis://" + trimmed);
            }
        }
    }
}
