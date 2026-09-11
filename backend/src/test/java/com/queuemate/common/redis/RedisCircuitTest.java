package com.queuemate.common.redis;

import com.queuemate.common.metrics.QueueMateMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 죽은 Redis를 부르지 않는 규칙.
 *
 * <p>여기서 고정하려는 것은 두 가지다. 한 번의 느린 호출로 멀쩡한 Redis를 끊지 않는 것과,
 * 살아났는지 확인할 때 한 번만 내보내는 것이다. 확인을 전부 내보내면 아직 죽어 있을 때
 * 그 전부가 타임아웃을 태워, 서킷이 없을 때와 같아진다.
 */
class RedisCircuitTest {

    private static final int THRESHOLD = 3;
    private static final Duration OPEN = Duration.ofSeconds(5);
    private static final Instant T0 = Instant.parse("2026-09-12T00:00:00Z");

    private RedisCircuit circuit;

    @BeforeEach
    void setUp() {
        circuit = new RedisCircuit(
                new QueueMateMetrics(new SimpleMeterRegistry()), THRESHOLD, OPEN.toMillis());
    }

    @Test
    @DisplayName("평소에는 그냥 통과시킨다")
    void allowsWhenClosed() {
        assertThat(circuit.allowRequest(T0)).isTrue();
        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
    }

    @Test
    @DisplayName("문턱 전까지는 열리지 않는다")
    void staysClosedBelowThreshold() {
        for (int i = 0; i < THRESHOLD - 1; i++) {
            circuit.recordFailure(T0);
        }

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
        assertThat(circuit.allowRequest(T0)).isTrue();
    }

    @Test
    @DisplayName("사이에 성공이 끼면 연속이 아니다")
    void successResetsStreak() {
        // 느린 Lua 한 번과 진짜 장애를 가르는 지점이다.
        circuit.recordFailure(T0);
        circuit.recordFailure(T0);
        circuit.recordSuccess();
        circuit.recordFailure(T0);
        circuit.recordFailure(T0);

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
    }

    @Test
    @DisplayName("연속 실패가 문턱에 닿으면 시도를 멈춘다")
    void opensAtThreshold() {
        failToOpen();

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.OPEN);
        assertThat(circuit.allowRequest(T0)).isFalse();
    }

    @Test
    @DisplayName("유지 시간이 지나면 한 번만 확인을 내보낸다")
    void probesOnceAfterOpenDuration() {
        failToOpen();
        Instant after = T0.plus(OPEN).plusMillis(1);

        assertThat(circuit.allowRequest(after)).isTrue();
        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.HALF_OPEN);
        // 확인이 돌아오기 전까지 나머지는 계속 막힌다.
        assertThat(circuit.allowRequest(after)).isFalse();
    }

    @Test
    @DisplayName("확인이 성공하면 닫는다")
    void closesWhenProbeSucceeds() {
        failToOpen();
        circuit.allowRequest(T0.plus(OPEN).plusMillis(1));

        circuit.recordSuccess();

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
        assertThat(circuit.allowRequest(T0)).isTrue();
    }

    @Test
    @DisplayName("확인이 실패하면 문턱을 다시 세지 않고 곧장 다시 닫는다")
    void reopensImmediatelyWhenProbeFails() {
        failToOpen();
        Instant probeAt = T0.plus(OPEN).plusMillis(1);
        circuit.allowRequest(probeAt);

        circuit.recordFailure(probeAt);

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.OPEN);
        // 유지 시간은 확인이 실패한 시점부터 다시 센다.
        assertThat(circuit.allowRequest(probeAt.plus(OPEN).minusMillis(1))).isFalse();
        assertThat(circuit.allowRequest(probeAt.plus(OPEN).plusMillis(1))).isTrue();
    }

    @Test
    @DisplayName("복구 신호를 받으면 유지 시간을 기다리지 않는다")
    void recoverySignalSkipsWaiting() {
        failToOpen();

        circuit.onRecoverySignal("pubsub");

        // 닫지는 않는다. 강등된 구 master는 읽기만 되고 쓰기는 실패하므로 확인을 거쳐야 한다.
        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.HALF_OPEN);
    }

    @Test
    @DisplayName("닫혀 있을 때 온 복구 신호는 아무것도 바꾸지 않는다")
    void recoverySignalIgnoredWhenClosed() {
        circuit.onRecoverySignal("pubsub");

        assertThat(circuit.state()).isEqualTo(RedisCircuit.State.CLOSED);
    }

    @Test
    @DisplayName("스레드가 몰려도 확인은 하나만 나간다")
    void onlyOneProbeEscapesUnderConcurrency() throws Exception {
        // trigger 스레드가 4개라 실제로 동시에 몰린다. 전부 내보내면 서킷이 없는 것과 같다.
        failToOpen();
        Instant after = T0.plus(OPEN).plusMillis(1);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (circuit.allowRequest(after)) {
                    allowed.incrementAndGet();
                }
            });
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(1);
    }

    private void failToOpen() {
        for (int i = 0; i < THRESHOLD; i++) {
            circuit.recordFailure(T0);
        }
    }
}
