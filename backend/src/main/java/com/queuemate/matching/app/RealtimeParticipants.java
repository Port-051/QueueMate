package com.queuemate.matching.app;

import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.ProposalParticipants;
import com.queuemate.matching.domain.ProposalSourceType;
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

    public RealtimeParticipants(MatchRequestRepository requests, MatchQueueRepository queue,
                                MatchConditionCodec codec) {
        this.requests = requests;
        this.queue = queue;
        this.codec = codec;
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
            queue.release(request.getUserId(), request.getId(),
                    MatchingRedisKeys.queue(condition.game(), condition.modeKey()));
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
            // 최초 대기 시각을 그대로 넣어 오래 기다린 사람이 앞자리를 지킨다 (docs/03 §8).
            queue.requeue(MatchingRedisKeys.queue(condition.game(), condition.modeKey()),
                    request.getId(), request.getQueuedAt().toInstant());
        }
    }
}
