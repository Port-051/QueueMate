package com.queuemate.common.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 어떤 메시지를 복구 신호로 받아들일 것인가.
 *
 * <p>한 sentinel이 여러 master를 감시할 수 있다. 남의 master 교체를 우리 복구로 읽으면
 * 아직 죽어 있는 Redis에 일감을 쏟는다.
 */
class SentinelFailoverListenerTest {

    private static final String MASTER = "queuemate";

    @Test
    @DisplayName("우리 master 교체면 복구 신호로 넘긴다")
    void forwardsOwnMasterSwitch() {
        RedisRecoveryCoordinator coordinator = mock(RedisRecoveryCoordinator.class);

        listener(coordinator).onSwitchMaster(MASTER, "queuemate 172.20.0.2 6379 172.20.0.3 6379");

        verify(coordinator, times(1)).onSignal("pubsub");
    }

    @Test
    @DisplayName("다른 master 교체는 무시한다")
    void ignoresOtherMasters() {
        RedisRecoveryCoordinator coordinator = mock(RedisRecoveryCoordinator.class);
        SentinelFailoverListener listener = listener(coordinator);

        listener.onSwitchMaster(MASTER, "other-service 172.20.0.2 6379 172.20.0.3 6379");
        // 이름이 접두사로만 겹치는 경우도 우리 것이 아니다.
        listener.onSwitchMaster(MASTER, "queuemate-staging 172.20.0.2 6379 172.20.0.3 6379");
        listener.onSwitchMaster(MASTER, null);

        verify(coordinator, never()).onSignal(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("sentinel 설정이 없으면 아무것도 구독하지 않는다")
    void doesNothingWithoutSentinel() {
        // 단일 Redis로 띄운 개발 환경이다. 들을 채널이 없고, 없다고 기동을 막지도 않는다.
        RedisProperties properties = new RedisProperties();
        properties.setHost("localhost");

        new SentinelFailoverListener(properties, mock(RedisRecoveryCoordinator.class)).subscribe();
    }

    private SentinelFailoverListener listener(RedisRecoveryCoordinator coordinator) {
        RedisProperties properties = new RedisProperties();
        RedisProperties.Sentinel sentinel = new RedisProperties.Sentinel();
        sentinel.setMaster(MASTER);
        sentinel.setNodes(List.of("sentinel-1:26379"));
        properties.setSentinel(sentinel);
        return new SentinelFailoverListener(properties, coordinator);
    }
}
