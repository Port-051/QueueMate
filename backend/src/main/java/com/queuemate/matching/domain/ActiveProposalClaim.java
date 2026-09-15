package com.queuemate.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 한 사용자가 지금 묶여 있는 제안 (INV-2).
 *
 * <p>{@code user_id}가 PK다. 그래서 같은 사람이 두 제안에 묶이는 일은 저장 자체가 되지 않는다.
 *
 * <p>Redis atomic claim이 hot path의 진실이고 이 행이 영속 진실이다. INV-1을
 * Redis guard와 partial unique index 두 겹으로 지키는 것과 같은 구조다.
 * Redis 복제는 비동기라 failover 때 claim을 잃을 수 있고, 그때 이 행이 최후의 방어선이 된다.
 *
 * <p>제안이 끝나면 지운다. 활성인 동안에만 존재하는 행이다.
 */
@Entity
@Table(name = "active_proposal_claims")
public class ActiveProposalClaim {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "proposal_id", nullable = false)
    private UUID proposalId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected ActiveProposalClaim() {
    }

    private ActiveProposalClaim(UUID userId, UUID proposalId, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.proposalId = proposalId;
        this.expiresAt = expiresAt;
    }

    public static ActiveProposalClaim held(UUID userId, UUID proposalId, OffsetDateTime expiresAt) {
        if (userId == null || proposalId == null || expiresAt == null) {
            throw new IllegalArgumentException("userId, proposalId, expiresAt은 필수다");
        }
        return new ActiveProposalClaim(userId, proposalId, expiresAt);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
