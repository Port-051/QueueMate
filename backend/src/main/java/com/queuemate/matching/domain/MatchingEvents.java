package com.queuemate.matching.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 매칭이 밖으로 알리는 사건들 (contracts/events.md).
 *
 * <p>매칭은 WebSocket을 직접 다루지 않는다. 전달은 realtime 패키지 소유다.
 * 여기서는 "무슨 일이 일어났는지"만 발행하고, 누구에게 어떻게 보낼지는 구독자가 정한다.
 *
 * <p>구독자는 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}를 쓴다.
 * 커밋되지 않은 제안을 사용자에게 먼저 알리면 존재하지 않는 제안을 수락하려 든다.
 */
public final class MatchingEvents {

    private MatchingEvents() {
    }

    /**
     * 제안이 만들어졌다. 참가자 전원에게 동시에 전달해야 한다 (docs/03 §8).
     * 이 이벤트가 늦으면 사용자는 TTL을 그냥 흘려보낸다.
     *
     * <p>event name: {@code MATCH_PROPOSAL_CREATED} / {@code RESERVATION_PROPOSAL_CREATED}
     */
    public record ProposalCreated(
            UUID proposalId,
            ProposalSourceType sourceType,
            List<UUID> userIds,
            OffsetDateTime expiresAt,
            OffsetDateTime scheduledStart
    ) {
        public ProposalCreated {
            userIds = List.copyOf(userIds);
        }

        /** contracts/events.md의 이벤트 이름. */
        public String eventName() {
            return sourceType == ProposalSourceType.RESERVATION
                    ? "RESERVATION_PROPOSAL_CREATED"
                    : "MATCH_PROPOSAL_CREATED";
        }
    }

    /**
     * 제안이 끝났다. 확정/거절/만료/취소를 하나로 묶어 보낸다.
     *
     * <p>event name: {@code MATCH_CONFIRMED} / {@code MATCH_PROPOSAL_EXPIRED} / {@code MATCH_CANCELLED}
     */
    public record ProposalSettled(
            UUID proposalId,
            ProposalSourceType sourceType,
            ProposalStatus status,
            List<UUID> userIds
    ) {
        public ProposalSettled {
            userIds = List.copyOf(userIds);
        }

        public String eventName() {
            return switch (status) {
                case CONFIRMED -> "MATCH_CONFIRMED";
                case EXPIRED -> "MATCH_PROPOSAL_EXPIRED";
                case DECLINED, CANCELLED -> "MATCH_CANCELLED";
                case PENDING -> throw new IllegalStateException("끝나지 않은 제안이다: " + proposalId);
            };
        }
    }
}
