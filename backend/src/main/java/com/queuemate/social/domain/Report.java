package com.queuemate.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 서버가 음성/채팅을 저장하지 않으므로 category와 설명, 식별자만 남는다 (docs/13). */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "target_user_id", nullable = false, updatable = false)
    private UUID targetUserId;

    @Column(name = "party_id")
    private UUID partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ReportReason reason;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Report() {
    }

    private Report(UUID id, UUID reporterId, UUID targetUserId, UUID partyId,
                   ReportReason reason, String description) {
        this.id = id;
        this.reporterId = reporterId;
        this.targetUserId = targetUserId;
        this.partyId = partyId;
        this.reason = reason;
        this.description = description;
    }

    public static Report create(UUID reporterId, UUID targetUserId, UUID partyId,
                                ReportReason reason, String description) {
        return new Report(UUID.randomUUID(), reporterId, targetUserId, partyId, reason, description);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public ReportReason getReason() {
        return reason;
    }
}
