package com.queuemate.matching.app;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.metrics.QueueMateMetrics;
import com.queuemate.common.redis.RedisCircuit;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.MatchBucket;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 매칭을 언제 돌릴지 정한다 (docs/03 §11).
 *
 * <p>대기열에 사람이 들어온 그 자리에서 매칭을 시도한다. 주기 tick으로 모든 게임×모드를
 * 훑던 방식을 대신한다. tick 방식은 아무도 기다리지 않는 모드까지 초당 한 번씩 Redis를 읽으면서
 * 정작 매칭 지연은 tick 간격만큼 생겼다. 새 대기자 없이 새 파티가 생길 수는 없으므로,
 * 대기열이 변한 순간 말고 매칭을 돌려야 할 이유가 없다.
 *
 * <p>큐를 채우는 경로는 둘뿐이고 둘 다 여기를 부른다. 새 요청({@link MatchRequestService})과
 * 깨진 제안의 복귀({@link RealtimeParticipants})다. 둘 다 커밋된 뒤에 부른다.
 * 커밋 전에 부르면 매처가 아직 보이지 않는 행을 찾는다.
 *
 * <p>매칭은 호출한 스레드에서 하지 않는다. 그렇게 하면 매칭 요청의 응답 시간이 대기열 길이에
 * 끌려가고, 늦게 온 사람이 자기 요청 처리 중에 남의 파티를 만들어 주게 된다.
 *
 * <p>Redis가 죽어 있는 구간에는 {@link RedisCircuit}이 시도 자체를 멈춘다. 매칭을 건너뛰는
 * 결과는 같지만, 건너뛰기까지 스레드마다 타임아웃을 태우지 않는다.
 *
 * <p>trigger를 잃어도(프로세스 종료, 큐 포화, Redis 일시 장애) 큐가 영영 멈추지 않도록
 * {@link MatchingScheduler}가 훨씬 긴 주기로 훑는다. 그쪽은 평소에 아무것도 찾지 못해야 정상이고,
 * {@code queuemate.match.proposal.created{source=SWEEP}}가 올라가면 event 경로가 새고 있다는 뜻이다.
 */
@Component
public class MatchTrigger {

    private static final Logger log = LoggerFactory.getLogger(MatchTrigger.class);

    private final RealtimeMatcher matcher;
    private final GameModeConfigProvider modes;
    private final QueueMateMetrics metrics;
    private final RedisCircuit circuit;
    private final boolean enabled;
    private final int maxProposalsPerRun;
    private final ThreadPoolExecutor workers;

    /**
     * 아직 돌지 않은 bucket. 같은 자리에 열 명이 몰려도 일감은 하나면 된다.
     *
     * <p>한 번 도는 동안 그 bucket에서 만들 수 있는 파티를 다 만들기 때문이다.
     */
    private final Set<MatchBucket> pending = ConcurrentHashMap.newKeySet();

    public MatchTrigger(RealtimeMatcher matcher, GameModeConfigProvider modes,
                        QueueMateMetrics metrics, RedisCircuit circuit,
                        @Value("${queuemate.matching.auto-trigger:true}") boolean enabled,
                        @Value("${queuemate.matching.max-proposals-per-run:20}")
                        int maxProposalsPerRun,
                        @Value("${queuemate.matching.trigger-threads:4}") int threads,
                        @Value("${queuemate.matching.trigger-queue:512}") int queueCapacity) {
        this.matcher = matcher;
        this.modes = modes;
        this.metrics = metrics;
        this.circuit = circuit;
        this.enabled = enabled;
        this.maxProposalsPerRun = maxProposalsPerRun;
        this.workers = new ThreadPoolExecutor(
                threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), namedThreads());
    }

    /**
     * 이 bucket에 대기자가 생겼다. 커밋된 뒤에 부른다.
     *
     * <p>같은 bucket 일감이 이미 잡혀 있으면 새로 잡지 않는다. 일감을 잡을 자리가 없으면
     * 버리고 안전망에 맡긴다. 여기서 호출자를 기다리게 하면 대기열이 길 때 매칭 요청
     * 응답이 같이 느려져, 가장 붐빌 때 가장 나쁘게 동작한다.
     */
    public void onQueued(MatchBucket bucket) {
        if (!enabled) {
            return;
        }
        if (!pending.add(bucket)) {
            return;
        }
        try {
            workers.execute(() -> {
                // 일감을 집은 시점에 표식을 푼다. 도는 동안 새로 온 사람은 다음 일감을 만든다.
                pending.remove(bucket);
                drainAround(bucket);
            });
        } catch (RejectedExecutionException e) {
            pending.remove(bucket);
            metrics.matchTriggerDropped();
            log.warn("매칭 trigger를 버렸다. 안전망 sweep까지 밀린다 bucket={}", bucket.suffix());
        }
    }

    /**
     * 멈춰 선 큐가 없는지 훑는다. {@link MatchingScheduler}가 긴 주기로 부른다.
     *
     * @return 이번에 만든 제안 수. 0이 아니면 event 경로가 무언가를 놓쳤다는 뜻이다
     */
    public int sweepStalledQueues() {
        int created = 0;
        for (GameKey game : GameKey.values()) {
            for (GameModeConfig config : modes.activeModes(game)) {
                created += drainMode(config);
            }
        }
        if (created > 0) {
            log.warn("안전망 sweep이 제안을 만들었다. event trigger가 유실되고 있다 count={}", created);
        }
        return created;
    }

    /** 이 bucket에서 더 만들 조합이 없을 때까지, 다만 상한을 두고 돈다. */
    private void drainAround(MatchBucket bucket) {
        for (int i = 0; i < maxProposalsPerRun; i++) {
            // 실패가 이미 확정된 구간이면 부르지 않는다. 불러 봐야 타임아웃만 태운다.
            if (!circuit.allowRequest()) {
                metrics.redisCircuitShortCircuited();
                return;
            }
            Optional<UUID> proposalId;
            try {
                proposalId = matcher.tryMatchAround(bucket);
                circuit.recordSuccess();
            } catch (DataAccessException e) {
                // Redis나 DB가 잠시 흔들렸다. 새 매칭을 억지로 만들지 않는다 (INV-10).
                circuit.recordFailure();
                log.warn("매칭을 건너뛴다 bucket={}", bucket.suffix(), e);
                return;
            } catch (RuntimeException e) {
                // 일감 스레드에서 예외가 새면 그 다음 일감까지 조용히 사라진다.
                log.error("매칭 중 예상치 못한 실패 bucket={}", bucket.suffix(), e);
                return;
            }
            if (proposalId.isEmpty()) {
                return;
            }
            metrics.proposalCreated(QueueMateMetrics.MatchSource.TRIGGER);
        }
        // 상한까지 채웠다. 남은 조합은 이 파티들이 빠진 뒤 다음 trigger가 본다.
        log.info("한 번에 만들 수 있는 제안 상한에 걸렸다 bucket={} limit={}",
                bucket.suffix(), maxProposalsPerRun);
    }

    private int drainMode(GameModeConfig config) {
        int created = 0;
        for (int i = 0; i < maxProposalsPerRun; i++) {
            if (!circuit.allowRequest()) {
                metrics.redisCircuitShortCircuited();
                return created;
            }
            try {
                if (matcher.tryMatch(config.game(), config.modeKey()).isEmpty()) {
                    circuit.recordSuccess();
                    return created;
                }
                circuit.recordSuccess();
            } catch (DataAccessException e) {
                circuit.recordFailure();
                log.warn("매칭을 건너뛴다 game={} mode={}", config.game(), config.modeKey(), e);
                return created;
            }
            metrics.proposalCreated(QueueMateMetrics.MatchSource.SWEEP);
            created++;
        }
        return created;
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "match-trigger-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
