package com.queuemate.matching.failover;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Lettuce 클라이언트 옵션. <b>실험용 임시 기능이다</b> (package-info 참고).
 *
 * <p><b>재시도 기능과 독립된 플래그다</b>({@code queuemate.failover.lettuce.enabled}).
 * 실험 2 는 "클라이언트 옵션만" 바꿨을 때의 복구 시간을 재야 하므로 두 축을 따로
 * 켜고 끌 수 있어야 한다.
 *
 * <h2>어떤 훅을 쓰는가 — Spring Boot 4 에서 바뀐 부분</h2>
 * {@code redis-ha-lab/docs/app-config-snippets.md} §1-3 은
 * {@code org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer}
 * 로 적어 두었는데, <b>Spring Boot 4.1.1 에서는 그 경로가 없다.</b> 확인한 사실은 둘이다.
 * <ol>
 *   <li>패키지가 {@code org.springframework.boot.data.redis.autoconfigure} 로 옮겨졌다
 *       (별도 모듈 {@code spring-boot-data-redis} 로 분리됐다).</li>
 *   <li>같은 패키지에 {@link LettuceClientOptionsBuilderCustomizer} 가 <b>새로 생겼다.</b></li>
 * </ol>
 * 여기서는 새로 생긴 쪽을 쓴다. {@code LettuceClientConfigurationBuilderCustomizer} 는
 * Boot 가 {@code clientOptions(...)} 를 <b>이미 채워 넣은 뒤에</b> 실행되므로, 거기서
 * {@code builder.clientOptions(...)} 를 부르면 Boot 가 만든 옵션(SSL 설정 등)을
 * <b>통째로 덮어쓴다.</b> {@code LettuceClientOptionsBuilderCustomizer} 는 Boot 가 쓰는
 * 그 빌더를 그대로 받아 마지막에 손대므로 덮어쓸 위험이 없다.
 *
 * <h2>{@code TimeoutOptions} 는 여기서 손대지 않는다</h2>
 * Boot 4.1.1 의 {@code LettuceConnectionConfiguration.createClientOptions()} 가
 * {@code TimeoutOptions.enabled()} 를 이미 넣고 있다(바이트코드로 확인했고, 판 C 실측에서
 * 실패 요청이 2,034 / 2,005 / 2,005ms 로 {@code timeout: 2s} 가 그대로 걸리는 것을 봤다).
 * 같은 값을 다시 넣어 봐야 달라지는 것이 없어 지웠다.
 * <b>실험 2 축 1 에서 "TimeoutOptions 를 켰더니 빨라졌다"는 결과를 기대하지 마라.</b>
 *
 * <h2><b>미검증 축이다 — 실험에서 이 클래스를 켠 적이 없다</b></h2>
 * 지금까지의 모든 실험은 {@code FAILOVER_LETTUCE_ENABLED=false} 로 돌았다. 즉 아래
 * {@code REJECT_COMMANDS} 와 {@code connectTimeout} 은 <b>의미 있는 레버지만 아직
 * 아무 데이터도 없다.</b> 남겨 둔 것은 다음 실험에서 켜 보기 위해서다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "queuemate.failover.lettuce", name = "enabled", havingValue = "true")
public class LettuceFailoverOptionsConfig {

    @Bean
    public LettuceClientOptionsBuilderCustomizer failoverLettuceClientOptions(
            @Value("${queuemate.failover.lettuce.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${queuemate.failover.lettuce.reject-when-disconnected:true}") boolean reject)
    {
        log.warn("[failover] Lettuce 페일오버 옵션 켜짐 connectTimeoutMs={} rejectWhenDisconnected={}",
                connectTimeoutMs, reject);

        return builder -> {
            // (1) 끊긴 동안 명령을 어떻게 할지.
            //     기본값(DEFAULT)은 명령을 큐에 쌓는다 - 요청 스레드가 묶이고
            //     톰캣 스레드 풀이 마르면서 장애가 매칭 밖으로 번진다.
            //     REJECT_COMMANDS 는 즉시 실패시킨다. HikariCP connection-timeout: 300 이나
            //     PoolLock 의 짧은 대기와 같은 판단이다 - 확인 못 한 요청은 붙잡지 않는다.
            if (reject) {
                builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
            }

            // (2) 새 master 로 붙는 시도 자체의 상한. 길면 다음 Sentinel 로 넘어가는 것이 늦어진다.
            builder.socketOptions(SocketOptions.builder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .keepAlive(true)
                    .build());
        };
    }
}
