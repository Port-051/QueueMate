package com.queuemate.reservation.domain;

import com.queuemate.common.domain.PlayAmount;
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
 * 예약 매칭 요청 (docs/04 §2).
 *
 * <p>예약은 별도의 매칭 제품이 아니다. 기존 조건에 "플레이 가능한 시간"과 "플레이할 양"만 붙는다.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false)
    private String conditionJson;

    @Column(name = "available_from", nullable = false)
    private OffsetDateTime availableFrom;

    @Column(name = "available_to", nullable = false)
    private OffsetDateTime availableTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "play_amount", nullable = false)
    private PlayAmount playAmount;

    @Column(name = "scheduled_start")
    private OffsetDateTime scheduledStart;

    @Column(name = "proposal_id")
    private UUID proposalId;

    protected Reservation() {
    }

    private Reservation(UUID id, UUID userId, String conditionJson,
                        OffsetDateTime availableFrom, OffsetDateTime availableTo, PlayAmount playAmount) {
        this.id = id;
        this.userId = userId;
        this.status = ReservationStatus.ACTIVE;
        this.conditionJson = conditionJson;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
        this.playAmount = playAmount;
    }

    public static Reservation create(UUID userId, String conditionJson,
                                     OffsetDateTime availableFrom, OffsetDateTime availableTo,
                                     PlayAmount playAmount) {
        requireValidWindow(availableFrom, availableTo);
        return new Reservation(UUID.randomUUID(), userId, conditionJson,
                availableFrom, availableTo, playAmount);
    }

    /** ACTIVE일 때만 고칠 수 있다. 확정된 약속을 말없이 바꾸지 않는다 (docs/04 §10). */
    public void edit(String conditionJson, OffsetDateTime availableFrom, OffsetDateTime availableTo,
                     PlayAmount playAmount) {
        if (!status.isEditable()) {
            throw new IllegalStateException("지금은 수정할 수 없는 상태다: " + status);
        }
        requireValidWindow(availableFrom, availableTo);
        this.conditionJson = conditionJson;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
        this.playAmount = playAmount;
    }

    public void attachToProposal(UUID proposalId, OffsetDateTime scheduledStart) {
        transitionTo(ReservationStatus.PROPOSED);
        this.proposalId = proposalId;
        this.scheduledStart = scheduledStart;
    }

    public void returnToActive() {
        transitionTo(ReservationStatus.ACTIVE);
        this.proposalId = null;
        this.scheduledStart = null;
    }

    public void markMatched() {
        transitionTo(ReservationStatus.MATCHED);
    }

    public void cancel() {
        transitionTo(ReservationStatus.CANCELLED);
    }

    public void expire() {
        transitionTo(ReservationStatus.EXPIRED);
    }

    public void complete() {
        transitionTo(ReservationStatus.COMPLETED);
    }

    public TimeSlots.Window window() {
        return new TimeSlots.Window(availableFrom, availableTo);
    }

    private void transitionTo(ReservationStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("허용되지 않는 예약 상태 전이다: " + status + " -> " + next);
        }
        this.status = next;
    }

    private static void requireValidWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("플레이 가능한 시간은 필수다");
        }
        if (!TimeSlots.isAligned(from) || !TimeSlots.isAligned(to)) {
            throw new IllegalArgumentException("플레이 가능한 시간은 30분 단위여야 한다");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("시작이 끝보다 앞서야 한다");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getConditionJson() {
        return conditionJson;
    }

    public OffsetDateTime getAvailableFrom() {
        return availableFrom;
    }

    public OffsetDateTime getAvailableTo() {
        return availableTo;
    }

    public PlayAmount getPlayAmount() {
        return playAmount;
    }

    public OffsetDateTime getScheduledStart() {
        return scheduledStart;
    }

    public UUID getProposalId() {
        return proposalId;
    }
}
