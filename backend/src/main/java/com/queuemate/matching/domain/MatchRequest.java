package com.queuemate.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 실시간 매칭 요청의 영속 기록 (docs/06 match_requests).
 *
 * <p>매칭 hot path는 Redis가 담당하고, 이 테이블은 source of truth이자 복구 근거다.
 * DB의 partial unique index가 INV-1을 한 번 더 지킨다.
 */
@Entity
@Table(name = "match_requests")
public class MatchRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "match_type", nullable = false, updatable = false)
    private String matchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchRequestStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false, updatable = false)
    private String conditionJson;

    @Column(name = "queued_at", nullable = false, updatable = false)
    private OffsetDateTime queuedAt;

    @Column(name = "proposal_id")
    private UUID proposalId;

    protected MatchRequest() {
    }

    private MatchRequest(UUID id, UUID userId, String conditionJson, OffsetDateTime queuedAt) {
        this.id = id;
        this.userId = userId;
        this.matchType = "REALTIME";
        this.status = MatchRequestStatus.QUEUED;
        this.conditionJson = conditionJson;
        this.queuedAt = queuedAt;
    }

    /**
     * id를 애플리케이션이 만든다. Redis guard에 requestId를 먼저 써야 하므로
     * DB insert를 기다려 id를 받을 수 없다.
     */
    public static MatchRequest queue(UUID userId, String conditionJson, OffsetDateTime queuedAt) {
        return new MatchRequest(UUID.randomUUID(), userId, conditionJson, queuedAt);
    }

    /** 제안에 묶인다. QUEUED에서만 가능하다. */
    public void attachToProposal(UUID proposalId) {
        transitionTo(MatchRequestStatus.PROPOSED);
        this.proposalId = proposalId;
    }

    /** 제안이 깨져 다시 대기로 돌아간다. 최초 queuedAt은 그대로 두어 aging을 잃지 않는다. */
    public void returnToQueue() {
        transitionTo(MatchRequestStatus.QUEUED);
        this.proposalId = null;
    }

    public void markMatched() {
        transitionTo(MatchRequestStatus.MATCHED);
    }

    public void cancel() {
        transitionTo(MatchRequestStatus.CANCELLED);
    }

    public void expire() {
        transitionTo(MatchRequestStatus.EXPIRED);
    }

    private void transitionTo(MatchRequestStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "허용되지 않는 요청 상태 전이다: " + status + " -> " + next);
        }
        this.status = next;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getMatchType() {
        return matchType;
    }

    public MatchRequestStatus getStatus() {
        return status;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public OffsetDateTime getQueuedAt() {
        return queuedAt;
    }

    public UUID getProposalId() {
        return proposalId;
    }
}
