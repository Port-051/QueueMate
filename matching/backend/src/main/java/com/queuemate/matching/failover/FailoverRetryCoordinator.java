package com.queuemate.matching.failover;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.failover.FailoverRetryConfig.FailoverRetryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 서킷 + 재시도 큐 + 프로브 + 배출을 한 자리에 묶는다. <b>실험용 임시 기능이다</b>
 * (package-info 참고).
 *
 * <p><b>기존 코드가 아는 유일한 진입점이다.</b> {@code MatchTrigger} 가
 * {@link #execute(CreateMatchRequestCommand, Consumer)} 하나만 부른다.
 *
 * <h2>스레드 배치</h2>
 * <ul>
 *   <li>{@link #execute} — {@code matchingExecutor} 스레드(4~8개)가 부른다</li>
 *   <li>{@link #onRecoverySignal} — Sentinel pub/sub 스레드가 부른다.
 *       <b>거기서 일하지 않고 스케줄러에 넘긴다</b> — 구독 스레드를 붙잡으면 다음
 *       {@code +switch-master} 를 못 받는다</li>
 *   <li>프로브 / 배출 — 이 클래스가 만든 <b>단일 스레드</b> 스케줄러가 전부 처리한다.
 *       프로브가 둘로 늘어나지 않는 것이 여기서 구조적으로 보장된다</li>
 * </ul>
 * 세 종류의 스레드가 서킷 상태를 동시에 건드리므로 상태 전이는 {@link #circuitLock} 으로
 * 잠근다. 전이 하나가 몇 마이크로초라 락 경합을 걱정할 구간이 아니다.
 *
 * <h2>왜 ThreadLocal 이 안 되나</h2>
 * 실패한 요청을 "그 스레드에 들고 있다가 나중에 처리"하는 방법이 떠오르지만 둘 다 막힌다.
 * <b>다른 스레드가 못 읽는다</b> — 복구 신호는 pub/sub 스레드로 오는데 요청은 배정 스레드의
 * ThreadLocal 에 있다. 그리고 <b>붙잡고 기다리면 그 스레드가 묶인다</b> — 페일오버 동안
 * {@code matchingExecutor} 가 통째로 마른다. 큐는 그래서 스레드 밖에 있어야 한다.
 *
 * <h2>왜 인메모리 큐인가</h2>
 * <b>Redis 는 자기모순이다</b> — Redis 장애로 실패한 것을 Redis 에 저장할 수 없다.
 * Kafka/RabbitMQ 는 CLAUDE.md §3 이 추가를 금지하고, SQS 는 이 프로젝트에서 <b>앱 간</b>
 * 도메인 이벤트용이라 자기 작업 재시도에 네트워크 왕복과 수 초 지연을 붙일 이유가 없다
 * (재시도 시간 창이 TTL 60초뿐이다). 진행 중 매칭 상태를 DB 에 두는 것은 docs/11 #27 이
 * 금지하고, Spring Retry 는 기다리는 동안 {@code matchingExecutor} 스레드를 붙잡는다.
 *
 * <p>앱이 죽으면 큐가 사라지지만 그건 새 위험이 아니다. {@code claim-request.lua} 주석이
 * 이미 "자리만 잡고 배정 전에 앱이 죽으면 TTL 로 정리된다"를 감당하기로 한 범위다.
 */
@Slf4j
public class FailoverRetryCoordinator implements InitializingBean, DisposableBean {

    private final FailoverRetryProperties props;

    /**
     * 재시도 대기 큐. <b>덱인 이유는 앞으로 되돌려 넣어야</b> 하기 때문이다 — 프로브/배출이
     * 실패하면 꺼냈던 건을 원래 자리(맨 앞)로 돌려놔야 순서가 뒤집히지 않는다.
     * 크기 상한은 OOM 방지용이라 반드시 유한하다.
     */
    private final LinkedBlockingDeque<RetryEntry> queue;
    private final int queueCapacity;

    /** 상한/재시도 초과로 버린 건수. 실험 지표다. */
    private final AtomicLong dropped = new AtomicLong();

    private ScheduledExecutorService scheduler;

    /**
     * 실제 배정을 수행하는 함수. {@link #execute} 가 처음 불릴 때 한 번 세팅된다.
     *
     * <p><b>여기에 들고 있는 이유.</b> 배출/프로브는 스케줄러 스레드가 하는데 그때는
     * 호출부가 없다. {@code MatchTrigger} 를 주입받으면 서로를 참조하게 되고, 이 패키지를
     * 지울 때 {@code MatchTrigger} 쪽 수정이 한 줄로 안 끝난다 — <b>기존 파일 수정을
     * 최소화하는 것이 기능보다 우선</b>이라 이 모양을 골랐다.
     */
    private volatile Consumer<CreateMatchRequestCommand> assigner;

    // ---------------------------------------------------------------- 서킷 상태 (circuitLock 으로 보호)

    private final Object circuitLock = new Object();
    private final int failureThreshold;

    private CircuitState state = CircuitState.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAtMillis = 0;

    /** 페일오버 중 죽은 master 에 실제로 보낸 요청 수. 실험 6 의 핵심 지표다. */
    private long deadMasterAttempts = 0;

    public FailoverRetryCoordinator(FailoverRetryProperties props) {
        this.props = props;
        this.queueCapacity = Math.max(1, props.getQueueCapacity());
        this.queue = new LinkedBlockingDeque<>(this.queueCapacity);
        this.failureThreshold = Math.max(1, props.getFailureThreshold());
    }

    @Override
    public void afterPropertiesSet() {
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "failover-retry");
            // 데몬이어야 앱 종료를 막지 않는다. 큐가 남아도 TTL 이 정리한다
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(50, props.getDrainIntervalMs());
        scheduler.scheduleWithFixedDelay(this::tick, interval, interval, TimeUnit.MILLISECONDS);

        log.warn("[failover] 재시도 기능 켜짐 queueCapacity={} maxAttempts={} failureThreshold={} "
                        + "drainBatchSize={} drainIntervalMs={} openTimeoutMs={}",
                queueCapacity, props.getMaxAttempts(), failureThreshold,
                props.getDrainBatchSize(), interval, props.getOpenTimeoutMs());
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        log.warn("[failover] 재시도 기능 종료. 남은 큐={}건 (TTL 이 정리한다)", queue.size());
    }

    /**
     * 배정 한 건을 시도한다. 실패하면 큐로 넘긴다.
     *
     * <p><b>Redis 장애가 아닌 예외는 그대로 다시 던진다.</b> 그러면 지금까지처럼
     * {@code AsyncConfig} 의 uncaught 핸들러가 로그를 남긴다 — 기능을 켜도 그 경로의
     * 동작은 변하지 않는다.
     */
    public void execute(CreateMatchRequestCommand command, Consumer<CreateMatchRequestCommand> assign) {
        if (this.assigner == null) {
            // 매 요청 같은 값을 다시 쓸 이유가 없다. 처음 한 번만 세팅한다
            this.assigner = assign;
        }

        CircuitState current = state();
        if (current != CircuitState.CLOSED) {
            // OPEN / HALF_OPEN 이면 Redis 를 때리지 않는다. 시도 없이 큐로 보낸다.
            enqueue(RetryEntry.first(command), "서킷 " + current);
            return;
        }

        try {
            assign.accept(command);
            recordSuccess();
        } catch (RuntimeException e) {
            if (!isRedisFailure(e)) {
                throw e;
            }
            recordFailure(e);
            enqueue(RetryEntry.first(command), "배정 실패: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 복구 신호를 받았다. <b>이 설계의 핵심이다.</b>
     *
     * <p>일반적인 서킷 브레이커는 "OPEN 된 지 30초 지났으니 이제 살아났겠지"로 추측한다.
     * 여기서는 그 추측을 <b>인프라가 실제로 보낸 사실</b>(Sentinel 의
     * {@code +switch-master})로 대체한다. 추측이 빠르면 죽은 master 를 계속 때리고,
     * 느리면 이미 살아난 뒤에도 큐가 안 빈다. 이벤트에는 그 오차가 없다.
     *
     * @param source 어디서 온 신호인지. {@code pubsub}(SentinelRecoveryWatcher) 또는
     *               {@code open-timeout}(이 클래스의 시간 폴백). 폴링 경로는 제거됐다
     */
    public void onRecoverySignal(String source) {
        log.warn("[failover] 복구 신호 수신 source={} 서킷={} 큐={}건", source, state(), queue.size());
        ScheduledExecutorService current = scheduler;
        if (current != null && !current.isShutdown()) {
            current.execute(() -> probe(source));
        }
    }

    // ---------------------------------------------------------------- 스케줄러 스레드 전용

    /** 스케줄러 tick. 배출과 시간 폴백을 한 자리에서 처리한다. */
    private void tick() {
        try {
            if (queue.isEmpty()) {
                return;
            }
            switch (state()) {
                case CLOSED -> drainBatch();
                case OPEN -> {
                    long openTimeout = props.getOpenTimeoutMs();
                    if (openTimeout > 0 && openForMillis() >= openTimeout) {
                        // 이벤트가 오지 않는 판(단일 노드 등)을 위한 최후 안전장치다.
                        probe("open-timeout");
                    }
                }
                // 프로브는 이 스레드가 동기로 끝내므로 tick 에서 볼 일이 없다.
                case HALF_OPEN -> { }
            }
        } catch (Throwable t) {
            // 여기서 예외가 새면 scheduleWithFixedDelay 가 조용히 멈춘다. 반드시 삼킨다.
            log.error("[failover] 배출 tick 실패", t);
        }
    }

    /**
     * 프로브 한 건. <b>큐에서 실제 요청을 꺼내 배정을 시도한다.</b>
     *
     * <p><b>PING 을 쓰지 않는 이유.</b> 강등된 구 master 에 아직 붙어 있으면 PING 은
     * 성공하는데 쓰기는 {@code READONLY You can't write against a read only replica} 로
     * 실패한다. PING 으로 판정하면 서킷을 닫고 큐를 전부 배출했다가 전부 실패한다. 프로브는
     * 반드시 <b>쓰기 경로</b>를 검증해야 하고, 그렇다면 진짜 요청을 쓰는 것이 가장 정확하다
     * — 성공하면 그 건도 처리된 것이라 버려지는 시도가 없다.
     */
    private void probe(String source) {
        if (!tryHalfOpen("복구 신호(" + source + ")")) {
            return;
        }

        RetryEntry entry = queue.poll();
        if (entry == null) {
            closeWithoutProbe("큐가 비어 검증할 요청이 없다. 다음 실제 요청이 프로브가 된다");
            return;
        }

        if (attempt(entry, "probe")) {
            log.warn("[failover] 프로브 성공. 배출 시작 큐={}건", queue.size());
        }
        // 실패 처리(되돌리기 + OPEN 복귀)는 attempt() 안에서 끝난다.
    }

    /** 배치 하나만큼 배출한다. 사이 간격은 스케줄러 tick 주기가 만든다. */
    private void drainBatch() {
        int batch = Math.max(1, props.getDrainBatchSize());
        for (int i = 0; i < batch; i++) {
            RetryEntry entry = queue.poll();
            if (entry == null) {
                break;
            }
            if (!attempt(entry, "drain")) {
                // 서킷이 다시 열렸다. 남은 것은 다음 복구 신호를 기다린다
                break;
            }
        }
    }

    /**
     * 한 건을 배정한다.
     *
     * <p>배정 Lua 가 {@code -2}(claim TTL 이 먼저 끝남)를 돌려주는 경우는 여기서
     * <b>성공으로 보인다.</b> 그래도 맞다 — 만료 판단은 이미 Lua 가 해 주고 있고,
     * 우리가 할 일은 그 건을 큐에서 치우는 것뿐이다. 여기서 별도의 만료 판정을 두면
     * Lua 와 두 벌의 규칙이 생긴다.
     *
     * @return 계속 배출해도 되면 {@code true}
     */
    private boolean attempt(RetryEntry entry, String phase) {
        try {
            // 큐에 항목이 있다는 것은 execute() 가 최소 한 번 불렸다는 뜻이고,
            // execute() 는 첫머리에서 assigner 를 세팅한다. 여기서 null 일 수 없다.
            assigner.accept(entry.command());
            recordSuccess();
            log.info("[failover] 재배정 성공 phase={} userId={} attempts={} age={}ms",
                    phase, entry.command().getUserId(), entry.attempts(), entry.ageMillis());
            return true;

        } catch (RuntimeException e) {
            if (!isRedisFailure(e)) {
                // Redis 장애가 아니면 재시도할 이유가 없다. 로그만 남기고 버린다
                log.error("[failover] 재배정이 Redis 장애가 아닌 이유로 실패해 버린다 userId={}",
                        entry.command().getUserId(), e);
                return true;
            }
            recordFailure(e);
            requeue(entry);
            return false;
        }
    }

    // ---------------------------------------------------------------- 큐

    private void enqueue(RetryEntry entry, String reason) {
        if (queue.offerLast(entry)) {
            log.info("[failover] 재시도 큐 등록 userId={} 이유={} 큐={}건",
                    entry.command().getUserId(), reason, queue.size());
        } else {
            dropAndLog(entry, "큐가 가득 참(capacity=" + queueCapacity + ")");
        }
    }

    /** 되돌려 넣는다(맨 앞). 재시도 상한을 넘겼으면 버린다. */
    private void requeue(RetryEntry entry) {
        RetryEntry retried = entry.retried();
        if (retried.attempts() > Math.max(1, props.getMaxAttempts())) {
            dropAndLog(retried, "재시도 상한 " + props.getMaxAttempts() + "회 초과");
            return;
        }
        if (!queue.offerFirst(retried)) {
            dropAndLog(retried, "되돌리려는데 큐가 가득 참(capacity=" + queueCapacity + ")");
        }
    }

    private void dropAndLog(RetryEntry entry, String reason) {
        long n = dropped.incrementAndGet();
        log.warn("[failover] 재시도 요청을 버린다 userId={} attempts={} age={}ms 이유={} 누적버림={}",
                entry.command().getUserId(), entry.attempts(), entry.ageMillis(), reason, n);
    }

    // ---------------------------------------------------------------- 서킷 상태 기계

    private CircuitState state() {
        synchronized (circuitLock) {
            return state;
        }
    }

    /** OPEN 이 된 지 얼마나 지났나(ms). OPEN 이 아니면 0. */
    private long openForMillis() {
        synchronized (circuitLock) {
            return state == CircuitState.OPEN ? System.currentTimeMillis() - openedAtMillis : 0;
        }
    }

    /** 배정 한 건이 성공했다. HALF_OPEN 프로브의 성공도 여기로 온다. */
    private void recordSuccess() {
        synchronized (circuitLock) {
            consecutiveFailures = 0;
            if (state != CircuitState.CLOSED) {
                transitionTo(CircuitState.CLOSED, "배정 성공");
            }
        }
    }

    /** 배정 한 건이 Redis 장애로 실패했다. 죽은 master 를 실제로 때린 횟수이므로 지표로도 센다. */
    private void recordFailure(Throwable cause) {
        synchronized (circuitLock) {
            deadMasterAttempts++;
            consecutiveFailures++;

            if (state == CircuitState.HALF_OPEN) {
                transitionTo(CircuitState.OPEN, "프로브 실패: " + brief(cause));
                return;
            }
            if (state == CircuitState.CLOSED && consecutiveFailures >= failureThreshold) {
                transitionTo(CircuitState.OPEN, "연속 실패 " + consecutiveFailures + "회: " + brief(cause));
            }
        }
    }

    /**
     * 프로브 한 건을 통과시킬 수 있으면 HALF_OPEN 으로 바꾸고 {@code true}.
     *
     * <p>부르는 곳은 스케줄러 스레드 하나뿐이라 프로브가 둘로 늘어나지 않는다.
     * 그래도 상태 전이 자체는 다른 스레드와 겹치므로 여기서 잠근다.
     */
    private boolean tryHalfOpen(String reason) {
        synchronized (circuitLock) {
            if (state != CircuitState.OPEN) {
                return false;
            }
            transitionTo(CircuitState.HALF_OPEN, reason);
            return true;
        }
    }

    /**
     * 큐에 검증할 요청이 없을 때 OPEN 을 그냥 닫는다. 프로브는 쓰기 경로여야 하는데
     * ({@link #probe} 참고) 큐가 비면 쓸 거리가 없다. 그래서 닫아 두고 <b>다음 실제 요청이
     * 프로브가 되게</b> 한다 — 틀렸으면 그 한 건이 실패하며 다시 OPEN 이므로 손해는 하나다.
     */
    private void closeWithoutProbe(String reason) {
        synchronized (circuitLock) {
            if (state != CircuitState.CLOSED) {
                consecutiveFailures = 0;
                transitionTo(CircuitState.CLOSED, reason);
            }
        }
    }

    /**
     * 전이를 <b>전부 로그로 남긴다.</b> 실험에서 봐야 하는 것이 숫자가 아니라 타임라인이기
     * 때문이다 — "Sentinel 이 승격한 시각"과 "앱이 그걸 반영한 시각"의 간격이 이 로그의
     * 전이 시각으로 읽힌다. 로그 접두사 {@code [failover]} 로 grep 한다.
     */
    private void transitionTo(CircuitState next, String reason) {
        CircuitState prev = state;
        state = next;
        if (next == CircuitState.OPEN) {
            openedAtMillis = System.currentTimeMillis();
        }
        log.warn("[failover] 서킷 {} -> {} ({}) deadMasterAttempts={}",
                prev, next, reason, deadMasterAttempts);
    }

    private static String brief(Throwable cause) {
        return cause == null ? "원인 없음" : cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    // ---------------------------------------------------------------- 예외 판정

    /** 원인 사슬을 도는 깊이 상한. 자기 자신을 cause 로 갖는 예외에서 무한 루프를 막는다. */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * "이 예외가 Redis 장애인가"를 판정한다.
     *
     * <p><b>왜 전부 재시도하지 않나.</b> 배정 경로는 Redis 장애 말고도 터진다 —
     * "파티 배정 규칙이 없는 게임"({@code IllegalArgumentException}) 같은 것들이다.
     * 그런 것을 큐에 넣으면 상한까지 계속 실패하며 큐만 먹고 실제 장애 요청이 밀려난다.
     * <b>Redis 장애가 아니면 원래대로 예외를 다시 던져</b> {@code AsyncConfig} 의
     * uncaught 핸들러가 지금까지처럼 로그를 남기게 한다.
     *
     * <p>판정 대상이 넓은 이유는 Redis 경로가 <b>둘</b>이기 때문이다. Lettuce 는 Spring 이
     * {@link DataAccessException} 으로 옮겨 주지만, Redisson({@code RLock})은
     * {@code org.redisson.client.*} 예외를 그대로 던진다 — {@code PoolLock} 이 감싸 주는
     * 것은 "락을 못 잡음"뿐이고, 연결이 죽으면 {@code RedisTimeoutException} 이 날것으로
     * 올라온다. 그 둘을 <b>직접 import 하지 않고 패키지 이름</b>으로 보는 이유는, 버전이
     * 올라가며 예외 계층이 바뀌어도 이 판정이 조용히 틀리지 않게 하려는 것이다.
     */
    private static boolean isRedisFailure(Throwable throwable) {
        Throwable cause = throwable;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (matchesRedisFailure(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private static boolean matchesRedisFailure(Throwable cause) {
        if (cause instanceof DataAccessException
                || cause instanceof IOException
                || cause instanceof TimeoutException) {
            return true;
        }
        String name = cause.getClass().getName();
        return name.startsWith("io.lettuce.core.")
                || name.startsWith("org.redisson.client.")
                || name.startsWith("java.net.");
    }

    // ---------------------------------------------------------------- 값 타입

    /**
     * 배정 서킷의 상태.
     *
     * <p>일반적인 서킷 브레이커는 OPEN → HALF_OPEN 전환을 <b>시간으로 추측</b>한다
     * ("30초 지났으니 이제 살아났겠지"). 여기서는 그 추측을 <b>인프라 이벤트</b>
     * (Sentinel 의 {@code +switch-master})로 대체한다. 그것이 이 설계의 요점이다.
     * 시간 기반 전환은 이벤트가 오지 않는 판을 위한 폴백으로만 남긴다
     * ({@code queuemate.failover.retry.open-timeout-ms}).
     */
    enum CircuitState {

        /** 정상. 들어온 배정을 그 자리에서 시도한다. */
        CLOSED,

        /** Redis 를 때리지 않는다. 들어오는 배정 요청도 시도 없이 큐로 보낸다. */
        OPEN,

        /** 프로브 1건만 통과시킨다. 성공하면 CLOSED, 실패하면 다시 OPEN. */
        HALF_OPEN
    }

    /**
     * 재시도 큐에 담기는 한 건.
     *
     * @param command    원래 요청 그대로. 배정에 필요한 것이 전부 여기 있다
     * @param attempts   지금까지 시도한 횟수. 상한을 넘으면 버린다
     * @param enqueuedAt <b>처음</b> 큐에 들어온 시각(epoch millis). 재시도로 다시 넣어도
     *                   유지한다 — "얼마나 오래 기다렸나"의 기준이 60초 TTL 이기 때문이다
     */
    record RetryEntry(CreateMatchRequestCommand command, int attempts, long enqueuedAt) {

        static RetryEntry first(CreateMatchRequestCommand command) {
            return new RetryEntry(command, 0, System.currentTimeMillis());
        }

        RetryEntry retried() {
            return new RetryEntry(command, attempts + 1, enqueuedAt);
        }

        long ageMillis() {
            return System.currentTimeMillis() - enqueuedAt;
        }
    }
}
