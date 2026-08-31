package com.queuemate.party.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 확정된 proposal 하나에서 나온 파티. 사용자가 직접 만들 수 없다.
 * proposal_id에 unique 제약이 걸려 있어 한 proposal은 파티 하나만 만든다 (INV-4).
 */
@Entity
@Table(name = "parties")
public class Party {

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @Column(name = "game_key", nullable = false, updatable = false)
    private String gameKey;

    @Column(name = "mode_key", nullable = false, updatable = false)
    private String modeKey;

    @Column(name = "target_size", nullable = false, updatable = false)
    private int targetSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyStatus status;

    @Column(name = "scheduled_start", updatable = false)
    private OffsetDateTime scheduledStart;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    protected Party() {
    }

    private Party(UUID id, UUID proposalId, String gameKey, String modeKey,
                  int targetSize, OffsetDateTime scheduledStart) {
        this.id = id;
        this.proposalId = proposalId;
        this.gameKey = gameKey;
        this.modeKey = modeKey;
        this.targetSize = targetSize;
        this.scheduledStart = scheduledStart;
        this.status = PartyStatus.OPEN;
    }

    public static Party of(UUID proposalId, String gameKey, String modeKey,
                           int targetSize, OffsetDateTime scheduledStart) {
        // id를 애플리케이션이 만든다. DB default를 쓰면 insert 전에 id를 알 수 없어
        // 같은 트랜잭션에서 party_members를 채울 때 한 번 더 조회해야 한다.
        return new Party(UUID.randomUUID(), proposalId, gameKey, modeKey, targetSize, scheduledStart);
    }

    /** 전원 준비면 READY, 아니면 OPEN. 진행/종료 상태는 여기서 건드리지 않는다. */
    public void refreshReadiness(boolean allReady) {
        if (status == PartyStatus.PLAYING || status == PartyStatus.CLOSED) {
            return;
        }
        status = allReady ? PartyStatus.READY : PartyStatus.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public String getGameKey() {
        return gameKey;
    }

    public String getModeKey() {
        return modeKey;
    }

    public int getTargetSize() {
        return targetSize;
    }

    public PartyStatus getStatus() {
        return status;
    }

    public OffsetDateTime getScheduledStart() {
        return scheduledStart;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }
}
