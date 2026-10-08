package com.queuemate.matching.failover;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 복구 신호를 받는다. <b>실험용 임시 기능이다</b> (package-info 참고).
 *
 * <h2>주 경로 — {@code +switch-master} 구독</h2>
 * Sentinel 은 페일오버를 마치면 {@code +switch-master} 채널에
 * {@code "<master이름> <구ip> <구port> <신ip> <신port>"} 를 publish 한다.
 * <b>이 채널은 Sentinel 포트(26379 등)에 있다. master 포트가 아니다.</b>
 * 그래서 앱의 기본 커넥션 팩토리로는 못 받고 <b>Sentinel 전용 커넥션</b>이 따로 필요하다.
 *
 * <h2>복구 계기는 두 겹이다 — 여기(주) + 코디네이터의 시간 폴백</h2>
 * <ul>
 *   <li><b>주 경로</b>: 이 클래스의 {@code +switch-master} 구독 →
 *       {@code coordinator.onRecoverySignal("pubsub")}</li>
 *   <li><b>폴백</b>: {@code FailoverRetryCoordinator} 의 {@code open-timeout-ms}.
 *       OPEN 이 그 시간을 넘기면 신호가 없어도 프로브를 던진다. Sentinel 을 안 쓰는
 *       판(단일 노드)에는 {@code +switch-master} 자체가 없으므로 이 폴백이 최후 안전장치다.</li>
 * </ul>
 *
 * <h2>Sentinel 폴링(백업 경로)은 <b>일부러</b> 없앴다 — 다시 넣지 마라</h2>
 * 예전에는 {@code SENTINEL get-master-addr-by-name} 을
 * {@code sentinel-poll-interval-ms} 주기로 물어 주소가 바뀌면
 * {@code onRecoverySignal("poll")} 을 내는 세 번째 경로가 있었다.
 * <b>실험 6 의 4회 실행 전부에서 이 경로가 신호를 한 번도 내지 못했다.</b>
 * <ul>
 *   <li>기동 로그가 매번 {@code [failover] Sentinel 폴링 시작 intervalMs=1000 현재master=null}
 *       이었다 — {@code readMasterAddress()} 가 <b>항상 {@code null}</b> 을 돌려줬다.
 *       (Spring Data 의 standalone 커넥션으로 Sentinel 포트에 {@code SENTINEL} 명령을
 *       치는 방식 자체가 원인으로 의심되나 <b>규명하지 못했다.</b>)</li>
 *   <li>{@code poll()} 은 {@code current == null} 이면 그냥 return 했으므로
 *       <b>영원히 신호가 나갈 수 없는 구조</b>였다.</li>
 *   <li>{@code 폴링이 master 변경을 감지했다} 로그가 전 실행에서 <b>0건</b>.
 *       복구는 전부 {@code source=pubsub} 또는 {@code open-timeout} 으로 처리됐다.</li>
 * </ul>
 * 즉 "돌지 않는 코드"였다. 고쳐서 남기는 것보다 <b>빼고 근거를 남기는 쪽</b>을 골랐다.
 * 다시 필요해지면 폴링을 되붙이기 전에 <b>{@code readMasterAddress()} 가 왜 {@code null}
 * 이었는지부터 규명해라.</b> 경위는 {@code redis-ha-lab/docs/failover-retry-guide.md} §6.
 *
 * <h2>왜 빈으로 노출하지 않고 직접 만드나</h2>
 * {@link LettuceConnectionFactory} 를 {@code @Bean} 으로 올리면
 * Spring Boot 의 {@code LettuceConnectionConfiguration} 이
 * {@code @ConditionalOnMissingBean(RedisConnectionFactory.class)} 때문에 <b>통째로 물러난다.</b>
 * 그러면 앱의 진짜 Redis 커넥션이 사라진다. 그래서 이 클래스가 자기 팩토리를
 * 필드로 들고 생명주기를 직접 관리한다. <b>이 패키지를 지워도 자동 설정에 흔적이 남지 않는
 * 이유가 이것이다.</b>
 *
 * <p>Sentinel 포트에 Spring Data 의 standalone 커넥션으로 붙는 것 자체는 실기로 확인했다 —
 * 실험 6 판 C 에서 {@code [failover] Sentinel 에 붙었다} 와
 * {@code [failover] 복구 신호 수신 source=pubsub} 이 실제로 찍혔다. 붙는 과정에서 예외가
 * 나면 주 경로만 죽고 시간 폴백으로 동작한다 — 초기화 실패가 앱 기동을 막지는 않는다.
 */
@Slf4j
public class SentinelRecoveryWatcher implements InitializingBean, DisposableBean {

    /** Sentinel 이 페일오버 완료를 알리는 채널. 이름이 고정이다. */
    private static final String SWITCH_MASTER_CHANNEL = "+switch-master";

    private final FailoverRetryCoordinator coordinator;
    private final String masterName;
    private final String sentinelNodes;

    private LettuceConnectionFactory sentinelFactory;
    private RedisMessageListenerContainer container;

    public SentinelRecoveryWatcher(FailoverRetryCoordinator coordinator,
                                   String masterName,
                                   String sentinelNodes) {
        this.coordinator = coordinator;
        this.masterName = masterName;
        this.sentinelNodes = sentinelNodes == null ? "" : sentinelNodes;
    }

    @Override
    public void afterPropertiesSet() {
        if (sentinelNodes.isBlank()) {
            log.warn("[failover] Sentinel 노드가 없어 +switch-master 구독을 건너뛴다. "
                    + "복구 계기는 open-timeout-ms 시간 폴백 하나만 남는다");
            return;
        }

        sentinelFactory = connectToAnySentinel();
        if (sentinelFactory == null) {
            log.warn("[failover] 어떤 Sentinel 에도 붙지 못했다 nodes={}. 시간 폴백만 동작한다",
                    sentinelNodes);
            return;
        }

        startSubscription();
    }

    @Override
    public void destroy() {
        if (container != null) {
            try {
                container.destroy();
            } catch (Exception e) {
                log.debug("[failover] 구독 컨테이너 종료 실패: {}", e.toString());
            }
        }
        if (sentinelFactory != null) {
            sentinelFactory.destroy();
        }
    }

    // ---------------------------------------------------------------- 주 경로

    private void startSubscription() {
        try {
            container = new RedisMessageListenerContainer();
            container.setConnectionFactory(sentinelFactory);
            container.afterPropertiesSet();
            container.addMessageListener(
                    (message, pattern) -> onSwitchMaster(new String(message.getBody(), StandardCharsets.UTF_8)),
                    new ChannelTopic(SWITCH_MASTER_CHANNEL));
            container.start();
            log.warn("[failover] {} 구독 시작", SWITCH_MASTER_CHANNEL);
        } catch (RuntimeException e) {
            log.warn("[failover] {} 구독 실패: {}. open-timeout 시간 폴백으로 대체한다",
                    SWITCH_MASTER_CHANNEL, e.toString());
            container = null;
        }
    }

    /** 본문은 {@code "<master이름> <구ip> <구port> <신ip> <신port>"} 다. */
    private void onSwitchMaster(String body) {
        log.warn("[failover] {} 수신: {}", SWITCH_MASTER_CHANNEL, body);
        String[] parts = body.trim().split("\\s+");
        if (parts.length >= 5 && !parts[0].equals(masterName)) {
            // 한 Sentinel 이 여러 master 를 감시할 수 있다. 내 것이 아니면 무시한다
            return;
        }
        coordinator.onRecoverySignal("pubsub");
    }

    // ---------------------------------------------------------------- 연결

    /**
     * Sentinel 목록을 앞에서부터 시도해 붙는 데 성공한 것 하나를 쓴다.
     *
     * <p>Sentinel 을 셋 띄우는 이유가 하나가 죽어도 되게 하려는 것이므로, 첫 번째만
     * 보고 포기하면 그 의미가 없어진다.
     */
    private LettuceConnectionFactory connectToAnySentinel() {
        for (String node : sentinelNodes.split(",")) {
            String trimmed = node.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.lastIndexOf(':');
            if (colon <= 0) {
                log.warn("[failover] Sentinel 주소 형식이 아니다: {}", trimmed);
                continue;
            }

            LettuceConnectionFactory factory = null;
            try {
                String host = trimmed.substring(0, colon);
                int port = Integer.parseInt(trimmed.substring(colon + 1));

                factory = new LettuceConnectionFactory(
                        new RedisStandaloneConfiguration(host, port),
                        LettuceClientConfiguration.builder()
                                .commandTimeout(Duration.ofMillis(2000))
                                .build());
                factory.afterPropertiesSet();
                factory.start();

                try (RedisConnection connection = factory.getConnection()) {
                    connection.commands().ping();
                }
                log.warn("[failover] Sentinel 에 붙었다: {}", trimmed);
                return factory;

            } catch (Exception e) {
                log.warn("[failover] Sentinel 연결 실패 {}: {}", trimmed, e.toString());
                if (factory != null) {
                    factory.destroy();
                }
            }
        }
        return null;
    }
}
