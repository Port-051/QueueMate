package com.queuemate.matching.app;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.common.error.ServiceUnavailableException;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.infra.AfterCommit;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 실시간 매칭 요청의 생성/취소/조회 (docs/03).
 *
 * <p>INV-1을 두 겹으로 지킨다. DB의 partial unique index가 영속 진실이고,
 * Redis guard가 매칭 hot path의 진실이다. 둘 중 하나라도 거절하면 409다.
 */
@Service
public class MatchRequestService {

    private static final List<MatchRequestStatus> ACTIVE_STATUSES =
            List.of(MatchRequestStatus.QUEUED, MatchRequestStatus.PROPOSED);
    private static final String DUPLICATE_CODE = "ACTIVE_MATCH_REQUEST_EXISTS";

    private final MatchRequestRepository requests;
    private final MatchQueueRepository queue;
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final Clock clock;

    @Autowired
    public MatchRequestService(MatchRequestRepository requests, MatchQueueRepository queue,
                               GameModeConfigProvider modes, MatchConditionCodec codec) {
        this(requests, queue, modes, codec, Clock.systemUTC());
    }

    MatchRequestService(MatchRequestRepository requests, MatchQueueRepository queue,
                        GameModeConfigProvider modes, MatchConditionCodec codec, Clock clock) {
        this.requests = requests;
        this.queue = queue;
        this.modes = modes;
        this.codec = codec;
        this.clock = clock;
    }

    /**
     * 매칭 대기를 시작한다.
     *
     * @throws ConflictException           이미 활성 요청이 있는 경우
     * @throws ServiceUnavailableException Redis를 쓸 수 없는 경우. fail-closed 한다 (INV-10)
     */
    @Transactional
    public MatchRequest start(UUID userId, MatchCondition condition) {
        requireActiveMode(condition);

        OffsetDateTime now = OffsetDateTime.now(clock);
        MatchRequest request = MatchRequest.queue(userId, codec.toJson(condition), now);
        try {
            requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException e) {
            // match_requests_one_active_per_user_idx
            throw new ConflictException(DUPLICATE_CODE, "이미 진행 중인 매칭 요청이 있다");
        }

        String queueKey = queueKeyOf(condition);
        boolean acquired;
        try {
            acquired = queue.acquire(userId, request.getId(), queueKey, now.toInstant());
        } catch (DataAccessException e) {
            throw new ServiceUnavailableException("MATCHING_UNAVAILABLE", "매칭을 시작할 수 없다", e);
        }
        if (!acquired) {
            throw new ConflictException(DUPLICATE_CODE, "이미 진행 중인 매칭 요청이 있다");
        }
        releaseGuardIfRolledBack(userId, request.getId(), queueKey);
        return request;
    }

    /** 대기를 취소한다. 이미 취소된 요청에 다시 불러도 성공으로 본다. */
    @Transactional
    public void cancel(UUID userId, UUID requestId) {
        // 매처가 같은 요청을 PROPOSED로 바꾸는 중일 수 있다. 잠그고 상태를 본다.
        MatchRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> notFound(requestId));
        if (!request.getUserId().equals(userId)) {
            throw notFound(requestId);
        }
        if (request.getStatus() == MatchRequestStatus.CANCELLED) {
            return;
        }
        if (request.getStatus() != MatchRequestStatus.QUEUED) {
            // 제안이 떠 있는 동안에는 취소가 아니라 거절로 처리해야 다른 참가자도 정리된다.
            throw new ConflictException("MATCH_REQUEST_NOT_CANCELLABLE",
                    "지금은 취소할 수 없는 상태다: " + request.getStatus());
        }
        request.cancel();
        String queueKey = queueKeyOf(codec.fromJson(request.getConditionJson()));
        // 커밋된 뒤에 푼다. 롤백됐는데 guard만 풀리면 DB에는 활성 요청이 남은 채
        // 같은 사용자가 하나 더 만들 수 있다.
        AfterCommit.run(() -> queue.release(userId, requestId, queueKey));
    }

    @Transactional(readOnly = true)
    public MatchRequest get(UUID userId, UUID requestId) {
        return ownedRequest(userId, requestId);
    }

    private MatchRequest ownedRequest(UUID userId, UUID requestId) {
        MatchRequest request = requests.findById(requestId)
                .orElseThrow(() -> notFound(requestId));
        if (!request.getUserId().equals(userId)) {
            // 남의 요청이 존재한다는 사실 자체를 알리지 않는다.
            throw notFound(requestId);
        }
        return request;
    }

    private GameModeConfig requireActiveMode(MatchCondition condition) {
        return modes.findActive(condition.game(), condition.modeKey())
                .orElseThrow(() -> new NotFoundException("UNKNOWN_GAME_MODE",
                        "지원하지 않는 게임 모드다: " + condition.game() + "/" + condition.modeKey()));
    }

    /**
     * 커밋되지 않으면 Redis guard도 되돌린다. 그러지 않으면 DB에는 요청이 없는데
     * Redis만 사용자를 잠근 채로 남아 그 사용자는 영영 매칭을 시작하지 못한다.
     */
    private void releaseGuardIfRolledBack(UUID userId, UUID requestId, String queueKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    queue.release(userId, requestId, queueKey);
                }
            }
        });
    }

    private static NotFoundException notFound(UUID requestId) {
        return new NotFoundException("MATCH_REQUEST_NOT_FOUND", "매칭 요청을 찾을 수 없다: " + requestId);
    }

    private static String queueKeyOf(MatchCondition condition) {
        return MatchingRedisKeys.queue(condition.game(), condition.modeKey());
    }
}
