package com.queuemate.matching.app;

import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Redis 대기열 재구성 (docs/07 §10).
 *
 * <p>Redis는 활성 매칭의 hot path지만 영속 진실이 아니다. Redis가 비워지거나
 * 재시작되면 DB의 QUEUED 요청을 근거로 대기열과 guard를 다시 세운다.
 *
 * <p>{@link #rebuild()}는 Redis가 통째로 날아갔을 때 운영자가 부르는 작업이고,
 * {@link #reconcile()}은 커밋 직후 유실된 Redis 작업을 되돌리는 주기 안전망이다.
 * 정상 상황에서 돌리면 이미 맞게 서 있는 것을 건드리지 않고 지나간다.
 */
@Service
public class MatchQueueRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(MatchQueueRecoveryService.class);

    private static final List<MatchRequestStatus> ACTIVE_STATUSES =
            List.of(MatchRequestStatus.QUEUED, MatchRequestStatus.PROPOSED);

    private final MatchRequestRepository requests;
    private final MatchQueueRepository queue;
    private final MatchConditionCodec codec;

    public MatchQueueRecoveryService(MatchRequestRepository requests, MatchQueueRepository queue,
                                     MatchConditionCodec codec) {
        this.requests = requests;
        this.queue = queue;
        this.codec = codec;
    }

    /**
     * DB의 QUEUED 요청으로 Redis 대기열을 다시 세운다.
     *
     * <p>최초 queuedAt을 그대로 쓰므로 복구 후에도 오래 기다린 사람이 앞자리를 지킨다.
     *
     * @return 복구 결과
     */
    @Transactional(readOnly = true)
    public RecoveryReport rebuild() {
        int restored = 0;
        int alreadyPresent = 0;
        int conflicted = 0;

        for (MatchRequest request : requests.findAllByStatusOrderByQueuedAtAsc(MatchRequestStatus.QUEUED)) {
            MatchCondition condition = codec.fromJson(request.getConditionJson());
            String queueKey = MatchingRedisKeys.queue(condition.game(), condition.modeKey());
            UUID userId = request.getUserId();

            if (queue.acquire(userId, request.getId(), queueKey, request.getQueuedAt().toInstant())) {
                restored++;
                continue;
            }
            // guard가 이미 있다. 그것이 이 요청이면 대기열 항목만 다시 넣으면 된다.
            if (queue.activeRequestOf(userId).filter(request.getId()::equals).isPresent()) {
                queue.requeue(queueKey, request.getId(), request.getQueuedAt().toInstant());
                alreadyPresent++;
            } else {
                // DB와 Redis가 서로 다른 요청을 가리킨다. 사람이 봐야 한다.
                log.warn("복구 중 guard 불일치 userId={} dbRequestId={} redisRequestId={}",
                        userId, request.getId(), queue.activeRequestOf(userId).orElse(null));
                conflicted++;
            }
        }
        RecoveryReport report = new RecoveryReport(restored, alreadyPresent, conflicted);
        log.info("대기열 재구성 완료 {}", report);
        return report;
    }

    /**
     * DB와 Redis의 어긋남을 양방향으로 되돌린다.
     *
     * <p>Redis 조작은 DB 커밋 뒤에 하므로, 커밋 직후 프로세스가 죽으면 Redis 작업이 유실된다.
     * 그때 남는 두 가지 흔적을 여기서 정리한다.
     * <ul>
     *   <li>DB는 끝났는데 guard만 남은 경우 &mdash; 그 사용자는 새 매칭을 영영 시작하지 못한다.
     *       {@code active-request}에는 TTL이 없어 저절로 사라지지도 않는다.</li>
     *   <li>DB는 대기 중인데 대기열에서 빠진 경우 &mdash; 후보에 오르지 않아 영영 매칭되지 않는다.</li>
     * </ul>
     *
     * @return 정리 결과
     */
    @Transactional(readOnly = true)
    public ReconcileReport reconcile() {
        RecoveryReport requeued = rebuild();

        int staleGuards = 0;
        Map<UUID, UUID> guards = queue.scanActiveRequests();
        if (!guards.isEmpty()) {
            Set<UUID> stillActive = requests
                    .findAllByIdInAndStatusIn(guards.values(), ACTIVE_STATUSES).stream()
                    .map(MatchRequest::getId)
                    .collect(Collectors.toSet());
            for (Map.Entry<UUID, UUID> guard : guards.entrySet()) {
                if (stillActive.contains(guard.getValue())) {
                    continue;
                }
                // DB에는 활성 요청이 없다. guard만 남아 이 사용자를 막고 있다.
                UUID userId = guard.getKey();
                UUID requestId = guard.getValue();
                log.warn("DB에 없는 활성 요청 guard를 제거한다 userId={} requestId={}", userId, requestId);
                queue.releaseAnywhere(userId, requestId);
                staleGuards++;
            }
        }
        ReconcileReport report = new ReconcileReport(requeued, staleGuards);
        if (staleGuards > 0 || requeued.restored() > 0 || requeued.conflicted() > 0) {
            log.info("DB-Redis 정합성 정리 {}", report);
        }
        return report;
    }

    /**
     * @param queues      대기열 재구성 결과
     * @param staleGuards 지운 유령 guard 수
     */
    public record ReconcileReport(RecoveryReport queues, int staleGuards) {
    }

    /**
     * @param restored       Redis에 새로 세운 요청 수
     * @param alreadyPresent guard가 이미 맞게 서 있어 대기열만 채운 수
     * @param conflicted     DB와 Redis가 어긋나 사람이 봐야 하는 수
     */
    public record RecoveryReport(int restored, int alreadyPresent, int conflicted) {
        public int total() {
            return restored + alreadyPresent + conflicted;
        }
    }
}
