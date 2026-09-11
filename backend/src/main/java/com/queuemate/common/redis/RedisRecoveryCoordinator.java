package com.queuemate.common.redis;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 복구 신호를 받아 확인까지 마친 뒤에야 정상으로 되돌린다 (docs/07 §11).
 *
 * <p>보통의 서킷은 OPEN → HALF_OPEN 전환을 시간으로 짐작한다("30초 지났으니 해보자").
 * 그 숫자에는 근거가 없다. 빠르면 죽은 서버를 계속 때리고, 늦으면 이미 살아난 뒤에도
 * 매칭이 멈춰 있다. 복구 여부를 실제로 아는 주체는 Sentinel이므로 그쪽 신호를 쓴다.
 *
 * <p>신호를 받아도 곧바로 열지 않는다. {@code +switch-master} 시점에는 클라이언트가 아직
 * 새 master 주소를 못 받았을 수 있다. 쓰기를 한 번 해 보고, 성공해야 닫는다.
 *
 * <p>확인은 한 스레드에서만 돈다. 신호가 여러 sentinel에서 동시에 와도 확인은 한 번이다.
 */
@Component
public class RedisRecoveryCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RedisRecoveryCoordinator.class);

    private final RedisCircuit circuit;
    private final RedisWriteProbe probe;
    private final ApplicationEventPublisher events;
    private final ExecutorService prober;

    // 생성자가 둘이면 Spring이 기본 생성자를 찾다 실패한다. 쓸 것을 지목해 둔다.
    @Autowired
    public RedisRecoveryCoordinator(RedisCircuit circuit, RedisWriteProbe probe,
                                    ApplicationEventPublisher events) {
        this(circuit, probe, events, Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "redis-recovery-probe");
            thread.setDaemon(true);
            return thread;
        }));
    }

    RedisRecoveryCoordinator(RedisCircuit circuit, RedisWriteProbe probe,
                             ApplicationEventPublisher events, ExecutorService prober) {
        this.circuit = circuit;
        this.probe = probe;
        this.events = events;
        this.prober = prober;
    }

    /**
     * 복구를 알리는 신호가 왔다.
     *
     * <p>서킷이 열려 있을 때만 의미가 있다. 평소에 온 신호는 확인할 것이 없다.
     */
    public void onSignal(String source) {
        if (circuit.state() != RedisCircuit.State.OPEN) {
            return;
        }
        circuit.onRecoverySignal(source);
        try {
            prober.execute(() -> verify(source));
        } catch (RejectedExecutionException e) {
            // 종료 중이다. 서킷은 유지 시간이 지나면 스스로 확인한다.
            log.debug("[failover] 확인을 맡길 수 없다 source={}", source);
        }
    }

    /** 테스트가 확인 절차만 따로 돌릴 수 있게 열어 둔다. */
    void verify(String source) {
        if (probe.writable()) {
            circuit.recordSuccess();
            log.info("[failover] 쓰기 확인 성공, 정상으로 되돌린다 source={}", source);
            events.publishEvent(new RedisRecoveredEvent(source));
            return;
        }
        // 아직이다. 서킷을 다시 닫고 유지 시간만큼 기다린다. 여기서 큐 전체를 쏟았다면
        // 그 전부가 실패했을 것이다.
        circuit.recordFailure();
        log.info("[failover] 아직 쓰기를 받지 않는다, 다시 기다린다 source={}", source);
    }

    Executor prober() {
        return prober;
    }

    @PreDestroy
    void shutdown() {
        prober.shutdownNow();
    }
}
