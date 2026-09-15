package com.queuemate.common.redis;

import com.queuemate.common.metrics.QueueMateMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 복구 신호를 받고 나서 무엇을 확인하는가.
 *
 * <p>신호만 믿고 곧장 열면, 강등된 구 master를 보고 있는 동안 밀린 일감을 전부 쏟아
 * 전부 실패시킨다. 확인 한 번을 먼저 내보내는 이유가 그것이다.
 */
class RedisRecoveryCoordinatorTest {

    private RedisCircuit circuit;
    private RedisWriteProbe probe;
    private List<Object> published;
    private RedisRecoveryCoordinator coordinator;

    @BeforeEach
    void setUp() {
        circuit = new RedisCircuit(new QueueMateMetrics(new SimpleMeterRegistry()), 3, 5000);
        probe = mock(RedisWriteProbe.class);
        published = new ArrayList<>();
        ApplicationEventPublisher events = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent event) {
                published.add(event);
            }

            @Override
            public void publishEvent(Object event) {
                published.add(event);
            }
        };
        // 확인을 테스트 스레드에서 그대로 돌려 타이밍에 기대지 않는다.
        coordinator = new RedisRecoveryCoordinator(circuit, probe, events, sameThreadExecutor());
    }

    @Test
    @DisplayName("쓰기가 되면 닫고 복구를 알린다")
    void closesAndAnnouncesWhenWritable() {
        openCircuit();
        when(probe.writable()).thenReturn(true);

        coordinator.onSignal("pubsub");

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
        assertThat(published).singleElement()
                .isEqualTo(new RedisRecoveredEvent("pubsub"));
    }

    @Test
    @DisplayName("쓰기가 아직 안 되면 다시 닫고 아무에게도 알리지 않는다")
    void reopensWhenNotWritableYet() {
        // 강등된 구 master는 PING만 받는다. 여기서 열어 주면 밀린 일감이 전부 실패한다.
        openCircuit();
        when(probe.writable()).thenReturn(false);

        coordinator.onSignal("pubsub");

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.OPEN);
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("서킷이 닫혀 있으면 확인하지 않는다")
    void ignoresSignalWhenClosed() {
        coordinator.onSignal("pubsub");

        assertThat(published).isEmpty();
        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
    }

    @Test
    @DisplayName("여러 sentinel이 같은 신호를 보내도 확인은 한 번이다")
    void probesOnceForDuplicateSignals() {
        openCircuit();
        when(probe.writable()).thenReturn(true);

        coordinator.onSignal("pubsub");
        coordinator.onSignal("pubsub");
        coordinator.onSignal("pubsub");

        assertThat(published).hasSize(1);
    }

    private void openCircuit() {
        for (int i = 0; i < 3; i++) {
            circuit.recordFailure();
        }
        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.OPEN);
    }

    private static java.util.concurrent.ExecutorService sameThreadExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            private volatile boolean shutdown;

            @Override public void shutdown() { shutdown = true; }
            @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
            @Override public boolean isShutdown() { return shutdown; }
            @Override public boolean isTerminated() { return shutdown; }
            @Override public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }
            @Override public void execute(Runnable command) { command.run(); }
        };
    }
}
