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

import java.util.UUID;

/**
 * Redis 대기열 재구성 (docs/07 §10).
 *
 * <p>Redis는 활성 매칭의 hot path지만 영속 진실이 아니다. Redis가 비워지거나
 * 재시작되면 DB의 QUEUED 요청을 근거로 대기열과 guard를 다시 세운다.
 *
 * <p>운영자가 부르는 작업이다. 자동으로 주기 실행하지 않는다.
 * 정상 상황에서 돌리면 이미 서 있는 guard를 건드리지 않고 지나간다.
 */
@Service
public class MatchQueueRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(MatchQueueRecoveryService.class);

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
