package com.queuemate.common.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel이 master를 바꿨다고 알려 주는 채널을 듣는다 (docs/07 §11).
 *
 * <p>복구 여부를 아는 주체는 Sentinel이다. 앱이 타이머로 짐작하는 대신 그쪽이 말해 줄 때
 * 움직인다. 채널은 {@code +switch-master}이고 본문은
 * {@code <master-name> <old-ip> <old-port> <new-ip> <new-port>} 형식이다.
 *
 * <p>구독은 Sentinel 포트(26379)로 붙는다. 데이터용 연결은 master를 보므로 이 채널이 오지 않는다.
 * 그래서 Spring Data의 연결을 재사용하지 않고 Lettuce로 따로 붙는다.
 *
 * <p>Sentinel 노드 전부를 듣는다. 한 대만 들으면 하필 그 한 대가 죽었을 때 신호를 놓치는데,
 * 그 상황이 바로 신호가 가장 필요한 상황이다. 중복 신호는 서킷이 OPEN일 때만 반응하므로 해가 없다.
 *
 * <p>sentinel 설정이 없으면(단일 Redis 개발 환경) 아무것도 하지 않는다. 그쪽에는 들을 채널이 없다.
 */
@Component
public class SentinelFailoverListener {

    private static final Logger log = LoggerFactory.getLogger(SentinelFailoverListener.class);

    private static final String CHANNEL = "+switch-master";
    private static final String SOURCE = "pubsub";

    private final RedisProperties properties;
    private final RedisRecoveryCoordinator coordinator;
    private final List<RedisClient> clients = new ArrayList<>();
    private final List<StatefulRedisPubSubConnection<String, String>> connections = new ArrayList<>();

    public SentinelFailoverListener(RedisProperties properties, RedisRecoveryCoordinator coordinator) {
        this.properties = properties;
        this.coordinator = coordinator;
    }

    @PostConstruct
    void subscribe() {
        RedisProperties.Sentinel sentinel = properties.getSentinel();
        if (sentinel == null || sentinel.getMaster() == null || sentinel.getMaster().isBlank()
                || sentinel.getNodes() == null) {
            log.info("sentinel 설정이 없어 failover 채널을 듣지 않는다");
            return;
        }
        for (String node : sentinel.getNodes()) {
            if (node == null || node.isBlank()) {
                continue;
            }
            subscribeTo(node.trim(), sentinel.getMaster());
        }
    }

    private void subscribeTo(String node, String masterName) {
        try {
            RedisClient client = RedisClient.create(RedisURI.create("redis://" + node));
            StatefulRedisPubSubConnection<String, String> connection = client.connectPubSub();
            connection.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void message(String channel, String message) {
                    onSwitchMaster(masterName, message);
                }
            });
            connection.sync().subscribe(CHANNEL);
            clients.add(client);
            connections.add(connection);
            log.info("sentinel failover 채널 구독 node={} master={}", node, masterName);
        } catch (RuntimeException e) {
            // 한 대를 못 들어도 나머지로 신호가 온다. 여기서 기동을 막을 일이 아니다.
            // 전부 실패하면 서킷의 유지 시간이 대신 복구를 시도한다.
            log.warn("sentinel 채널을 구독하지 못했다 node={}", node, e);
        }
    }

    /** 우리가 감시하는 master의 교체만 받는다. 한 sentinel이 여러 master를 볼 수 있다. */
    void onSwitchMaster(String masterName, String message) {
        if (message == null || !message.startsWith(masterName + " ")) {
            return;
        }
        log.info("[failover] +switch-master 수신 {}", message);
        coordinator.onSignal(SOURCE);
    }

    @PreDestroy
    void close() {
        connections.forEach(StatefulRedisPubSubConnection::close);
        clients.forEach(RedisClient::shutdown);
    }
}
