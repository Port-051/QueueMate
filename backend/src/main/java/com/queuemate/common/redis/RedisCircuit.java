package com.queuemate.common.redis;

import com.queuemate.common.metrics.QueueMateMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis가 죽어 있는 구간에 시도 자체를 멈춘다 (docs/07 §11).
 *
 * <p>failover는 실측 6초대다. 그동안 매칭 trigger 스레드 4개가 죽은 master를 계속 부르면
 * 호출마다 명령 타임아웃(2초)을 통째로 태운다. 어차피 실패가 확정된 구간이라 그 2초는
 * 아무것도 사기지 못하고, 그 사이 들어온 일감은 trigger 큐(512)를 채우다 버려진다.
 *
 * <p>세 상태를 가진다.
 * <ul>
 *   <li>{@code CLOSED} — 평소. 그냥 부른다.
 *   <li>{@code OPEN} — 죽었다고 본다. 부르지 않고 바로 실패로 돌린다.
 *   <li>{@code HALF_OPEN} — 살아났는지 딱 한 번 확인한다. 성공하면 CLOSED, 실패하면 OPEN.
 * </ul>
 *
 * <p>이것은 fail-closed(INV-10)를 어기지 않는다. 서킷이 열려 있으면 매칭을 만들지 않고
 * 물러날 뿐이고, 확인 없이 진행하는 경로를 만들지 않는다. 오히려 확인할 수 없는 구간에
 * 확인을 시도조차 하지 않으므로 더 엄격하다.
 *
 * <p>여러 스레드가 공유한다. 상태 전이는 CAS로 한 번만 일어나게 해서, 스레드 4개가 동시에
 * 실패해도 전이 로그와 지표가 네 번 찍히지 않는다.
 */
@Component
public class RedisCircuit {

    private static final Logger log = LoggerFactory.getLogger(RedisCircuit.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final QueueMateMetrics metrics;

    /**
     * 연속 몇 번 실패해야 죽었다고 볼 것인가.
     *
     * <p>명령 타임아웃이 2초라 한 번의 실패는 이미 2초짜리 사건이다. 느린 Lua 한 번이나
     * 순간적인 네트워크 흔들림으로 열리면 멀쩡한 Redis를 스스로 끊는 셈이 된다.
     * 세 번 연속이면 우연으로 보기 어렵고, 스레드 4개가 병렬로 실패하므로 실제로는
     * failover 시작 2초 안팎에 열린다.
     */
    private final int failureThreshold;

    /**
     * OPEN을 유지하는 시간. 이 시간이 지나면 한 번 확인해 본다.
     *
     * <p>Sentinel의 down-after와 같은 5초다. 그보다 일찍 확인해 봐야 Sentinel이 아직
     * 죽었다는 판정조차 내리지 않은 시점이라 확인의 의미가 없다.
     *
     * <p>이 시간은 어디까지나 대비책이다. Sentinel이 붙어 있으면 {@link #onRecoverySignal}이
     * 이 시간을 기다리지 않고 깨운다. 단일 Redis로 띄운 개발 환경에는 그 신호가 없어서
     * 시간 말고 기댈 것이 없다.
     */
    private final Duration openDuration;

    private final AtomicReference<Snapshot> snapshot =
            new AtomicReference<>(new Snapshot(State.CLOSED, 0, Instant.EPOCH));

    public RedisCircuit(QueueMateMetrics metrics,
                        @Value("${queuemate.redis.circuit.failure-threshold:3}") int failureThreshold,
                        @Value("${queuemate.redis.circuit.open-ms:5000}") long openMs) {
        this.metrics = metrics;
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofMillis(openMs);
    }

    /**
     * 지금 Redis를 불러도 되는가.
     *
     * <p>OPEN이어도 유지 시간이 지났으면 HALF_OPEN으로 바꾸고 딱 한 번 통과시킨다.
     * 그 한 번이 돌아오기 전까지 다른 스레드는 계속 막힌다. 살아났는지 보려고 전부
     * 내보내면 아직 죽어 있을 때 그 전부가 타임아웃을 태운다.
     */
    public boolean allowRequest() {
        return allowRequest(Instant.now());
    }

    boolean allowRequest(Instant now) {
        Snapshot current = snapshot.get();
        switch (current.state) {
            case CLOSED:
                return true;
            case HALF_OPEN:
                // 확인은 한 번만 내보낸다. 이미 나간 확인의 결과를 기다린다.
                return false;
            case OPEN:
            default:
                if (now.isBefore(current.until)) {
                    return false;
                }
                // 시간이 됐다. 딱 한 스레드만 확인을 들고 나간다.
                return transition(current, new Snapshot(State.HALF_OPEN, current.failures, current.until),
                        "시간 경과");
        }
    }

    /** 호출이 성공했다. 확인 중이었다면 살아난 것이다. */
    public void recordSuccess() {
        Snapshot current = snapshot.get();
        if (current.state == State.CLOSED && current.failures == 0) {
            return;
        }
        if (transition(current, new Snapshot(State.CLOSED, 0, Instant.EPOCH), "호출 성공")
                && current.state != State.CLOSED) {
            log.info("[failover] Redis 서킷을 닫는다 previous={}", current.state);
        }
    }

    /** 호출이 실패했다. 문턱을 넘으면 그 다음부터는 시도하지 않는다. */
    public void recordFailure() {
        recordFailure(Instant.now());
    }

    void recordFailure(Instant now) {
        Snapshot current = snapshot.get();
        // 확인이 실패했다. 아직 죽어 있다. 문턱을 다시 세지 않고 곧장 닫는다.
        if (current.state == State.HALF_OPEN) {
            open(current, now, "확인 실패");
            return;
        }
        if (current.state == State.OPEN) {
            return;
        }
        int failures = current.failures + 1;
        if (failures < failureThreshold) {
            snapshot.compareAndSet(current, new Snapshot(State.CLOSED, failures, Instant.EPOCH));
            return;
        }
        open(current, now, "연속 실패 " + failures + "회");
    }

    /**
     * Sentinel이 master를 교체했다고 알려 왔다. 유지 시간을 기다리지 않고 확인으로 넘어간다.
     *
     * <p>신호를 받았다고 곧바로 닫지 않는다. 이 시점에는 아직 클라이언트가 새 master 주소를
     * 못 받았을 수 있고, 강등된 구 master는 읽기는 되고 쓰기만 실패한다. 확인을 거쳐야 한다.
     */
    public void onRecoverySignal(String source) {
        Snapshot current = snapshot.get();
        if (current.state != State.OPEN) {
            return;
        }
        if (transition(current, new Snapshot(State.HALF_OPEN, current.failures, current.until), source)) {
            log.info("[failover] 복구 신호 수신 source={}", source);
        }
    }

    public State state() {
        return snapshot.get().state;
    }

    private void open(Snapshot current, Instant now, String reason) {
        if (transition(current, new Snapshot(State.OPEN, failureThreshold, now.plus(openDuration)), reason)) {
            log.warn("[failover] Redis 서킷을 연다 reason={} 유지={}ms", reason, openDuration.toMillis());
        }
    }

    /** 전이는 한 번만 일어난다. 진 스레드는 이긴 스레드가 만든 상태를 그대로 따른다. */
    private boolean transition(Snapshot expected, Snapshot next, String reason) {
        if (!snapshot.compareAndSet(expected, next)) {
            return false;
        }
        if (expected.state != next.state) {
            metrics.redisCircuitTransition(expected.state.name(), next.state.name());
            log.debug("[failover] 서킷 전이 {} -> {} reason={}", expected.state, next.state, reason);
        }
        return true;
    }

    /** 상태·연속 실패 수·OPEN 만료 시각을 한 번에 바꾸기 위한 묶음. */
    private record Snapshot(State state, int failures, Instant until) {
    }
}
