package com.queuemate.matching.failover;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 재시도 기능의 설정 + 빈 등록. <b>실험용 임시 기능이다</b> (package-info 참고).
 *
 * <p><b>{@code queuemate.failover.retry.enabled=true} 일 때만 이 클래스가 산다.</b>
 * 꺼져 있으면 여기 있는 빈이 하나도 안 만들어지고, {@code MatchTrigger} 의
 * {@code ObjectProvider} 는 빈 상태로 남아 기존 동작 그대로 흘러간다.
 * 조건을 {@code @Bean} 마다가 아니라 <b>클래스에</b> 건 이유는, 하나라도 빠뜨리면
 * "꺼도 뭔가 남는" 상태가 되기 때문이다.
 *
 * <p>기능을 지울 때는 이 패키지 폴더를 통째로 지우면 된다.
 * 절차는 {@code redis-ha-lab/docs/failover-retry-guide.md}.
 */
@Configuration
@EnableConfigurationProperties(FailoverRetryConfig.FailoverRetryProperties.class)
@ConditionalOnProperty(prefix = "queuemate.failover.retry", name = "enabled", havingValue = "true")
public class FailoverRetryConfig {

    @Bean
    public FailoverRetryCoordinator failoverRetryCoordinator(FailoverRetryProperties props) {
        return new FailoverRetryCoordinator(props);
    }

    /**
     * Sentinel 주소는 {@code RedissonConfig} 와 <b>같은 프로퍼티</b>를 읽는다.
     *
     * <p>여기만 따로 두면 "락은 Sentinel 인데 복구 신호는 안 온다" 같은,
     * 겉보기로는 멀쩡한 상태가 만들어진다.
     */
    @Bean
    public SentinelRecoveryWatcher sentinelRecoveryWatcher(
            FailoverRetryCoordinator coordinator,
            @Value("${spring.data.redis.sentinel.master:mymaster}") String sentinelMaster,
            @Value("${queuemate.lock.sentinel.nodes:${spring.data.redis.sentinel.nodes:}}") String sentinelNodes) {

        return new SentinelRecoveryWatcher(coordinator, sentinelMaster, sentinelNodes);
    }

    /**
     * 재시도 기능의 설정값.
     *
     * <p>모든 값에 코드 기본값이 있다. {@code application.yaml} 의
     * {@code queuemate.failover} 블록을 통째로 지워도 컴파일과 기동이 깨지지 않는다 —
     * 지울 때 yaml 을 먼저 지우든 자바를 먼저 지우든 순서를 신경 쓰지 않게 하려는 것이다.
     */
    @Getter
    @Setter
    @ConfigurationProperties(prefix = "queuemate.failover.retry")
    public static class FailoverRetryProperties {

        /** 이 기능 전체의 on/off. <b>기본 꺼짐.</b> 위 {@code @ConditionalOnProperty} 가 읽는 값이다. */
        private boolean enabled = false;

        /**
         * 재시도 큐의 크기 상한. <b>OOM 방지용이라 반드시 유한해야 한다.</b>
         *
         * <p>넘치면 새로 들어온 요청을 버린다. 버려도 60 초 TTL 이 활성 요청을 정리하므로
         * 사용자는 "매칭이 안 잡힘" 상태로 끝나고, 그건 이 기능이 없을 때와 같다.
         */
        private int queueCapacity = 5000;

        /** 한 요청을 최대 몇 번까지 다시 시도할지. 무한 재시도로 큐가 안 비는 것을 막는다. */
        private int maxAttempts = 5;

        /** 연속 실패가 이 횟수에 닿으면 CLOSED → OPEN. */
        private int failureThreshold = 3;

        /** 한 번에 배출할 건수. 방금 승격된 master 에 밀린 요청이 한꺼번에 몰리는 것을 막는다. */
        private int drainBatchSize = 20;

        /** 배출 배치 사이의 간격(ms). 스케줄러 tick 주기이기도 하다. */
        private long drainIntervalMs = 200;

        /**
         * OPEN 상태가 이 시간을 넘기면 <b>복구 신호가 없어도</b> 프로브를 한 번 던진다.
         *
         * <p>이벤트 기반이 이 설계의 요점이지만, Sentinel 을 안 쓰는 판(단일 노드)에서는
         * {@code +switch-master} 자체가 없다. 그때 큐가 영원히 안 비는 것을 막는 최후 안전장치다.
         * 0 이하면 시간 폴백을 끄고 이벤트만 기다린다 — 실험에서 "시간 기반 vs 이벤트 기반"을
         * 가르는 축이 이 값이다 (EXPERIMENTS.md 실험 6).
         */
        private long openTimeoutMs = 10_000;
    }
}
