package com.queuemate.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 함께 플레이할 파티 하나에 대한 제안 (docs/03 §8).
 *
 * <p>상대팀 개념은 없다. 한 제안은 언제나 "같이 할 사람들" 하나만 뜻한다.
 */
@Entity
@Table(name = "match_proposals")
public class MatchProposal {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    private ProposalSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProposalStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    protected MatchProposal() {
    }

    private MatchProposal(UUID id, ProposalSourceType sourceType, OffsetDateTime expiresAt) {
        this.id = id;
        this.sourceType = sourceType;
        this.status = ProposalStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public static MatchProposal pending(UUID id, ProposalSourceType sourceType, OffsetDateTime expiresAt) {
        if (id == null || sourceType == null || expiresAt == null) {
            throw new IllegalArgumentException("id, sourceType, expiresAt은 필수다");
        }
        return new MatchProposal(id, sourceType, expiresAt);
    }

    /**
     * 전원이 수락했다. INV-4에 따라 호출 전에 모든 참가자의 ACCEPTED를 확인해야 한다.
     * DB 제약이 CONFIRMED와 confirmed_at을 함께 요구하므로 같이 세운다.
     */
    public void confirm(OffsetDateTime now) {
        transitionTo(ProposalStatus.CONFIRMED);
        this.confirmedAt = now;
    }

    public void decline() {
        transitionTo(ProposalStatus.DECLINED);
    }

    public void expire() {
        transitionTo(ProposalStatus.EXPIRED);
    }

    public void cancel() {
        transitionTo(ProposalStatus.CANCELLED);
    }

    public boolean isExpiredAt(OffsetDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /** INV-5: 끝난 제안은 어떤 경로로도 되살아나지 않는다. */
    private void transitionTo(ProposalStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "허용되지 않는 제안 상태 전이다: " + status + " -> " + next);
        }
        this.status = next;
    }

    public UUID getId() {
        return id;
    }

    public ProposalSourceType getSourceType() {
        return sourceType;
    }

    public ProposalStatus getStatus() {
        return status;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }
}
