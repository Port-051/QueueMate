package com.queuemate.matching.app;

import com.queuemate.matching.domain.MatchBucket;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.ProposalParticipants;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.infra.AfterCommit;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 실시간 제안의 원본(match_request)을 다룬다. */
@Component
public class RealtimeParticipants implements ProposalParticipants {

    private final MatchRequestRepository requests;
    private final MatchQueueRepository queue;
    private final MatchConditionCodec codec;
    private final MatchTrigger trigger;

    public RealtimeParticipants(MatchRequestRepository requests, MatchQueueRepository queue,
                                MatchConditionCodec codec, MatchTrigger trigger) {
        this.requests = requests;
        this.queue = queue;
        this.codec = codec;
        this.trigger = trigger;
    }

    @Override
    public ProposalSourceType sourceType() {
        return ProposalSourceType.REALTIME;
    }

    @Override
    public Optional<PartyPlan> planFor(List<UUID> sourceIds) {
        return requests.findAllById(sourceIds).stream()
                .findFirst()
                .map(request -> codec.fromJson(request.getConditionJson()))
                .map(condition -> new PartyPlan(condition.game(), condition.modeKey(), null));
    }

    @Override
    public void onConfirmed(List<UUID> sourceIds) {
        for (MatchRequest request : requests.findAllById(sourceIds)) {
            request.markMatched();
            MatchCondition condition = codec.fromJson(request.getConditionJson());
            UUID userId = request.getUserId();
            UUID requestId = request.getId();
            String bucketKey = MatchingRedisKeys.queue(MatchBucket.of(condition));
            AfterCommit.run(() -> queue.release(userId, requestId, bucketKey));
        }
    }

    @Override
    public void onBroken(List<UUID> sourceIds) {
        for (MatchRequest request : requests.findAllById(sourceIds)) {
            if (request.getStatus() != MatchRequestStatus.PROPOSED) {
                continue;
            }
            request.returnToQueue();
            MatchCondition condition = codec.fromJson(request.getConditionJson());
            MatchBucket bucket = MatchBucket.of(condition);
            String bucketKey = MatchingRedisKeys.queue(bucket);
            UUID requestId = request.getId();
            java.time.Instant queuedAt = request.getQueuedAt().toInstant();
            // 최초 대기 시각을 그대로 넣어 오래 기다린 사람이 앞자리를 지킨다 (docs/03 §8).
            // 돌아온 사람도 대기열이 변한 것이다. 다음 조합을 기다리게 두지 않는다.
            AfterCommit.run(() -> {
                queue.requeue(bucketKey, requestId, queuedAt);
                trigger.onQueued(bucket);
            });
        }
    }
}
