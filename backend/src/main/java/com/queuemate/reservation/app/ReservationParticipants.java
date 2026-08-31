package com.queuemate.reservation.app;

import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.ProposalParticipants;
import com.queuemate.matching.domain.ProposalSourceType;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.reservation.domain.Reservation;
import com.queuemate.reservation.domain.ReservationStatus;
import com.queuemate.reservation.infra.ReservationRepository;
import com.queuemate.reservation.infra.ReservationSlotIndex;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 예약 제안의 원본(reservation)을 다룬다. */
@Component
public class ReservationParticipants implements ProposalParticipants {

    private final ReservationRepository reservations;
    private final ReservationSlotIndex slots;
    private final MatchConditionCodec codec;

    public ReservationParticipants(ReservationRepository reservations, ReservationSlotIndex slots,
                                   MatchConditionCodec codec) {
        this.reservations = reservations;
        this.slots = slots;
        this.codec = codec;
    }

    @Override
    public ProposalSourceType sourceType() {
        return ProposalSourceType.RESERVATION;
    }

    @Override
    public Optional<PartyPlan> planFor(List<UUID> sourceIds) {
        return reservations.findAllById(sourceIds).stream()
                .findFirst()
                .map(reservation -> {
                    MatchCondition condition = codec.fromJson(reservation.getConditionJson());
                    return new PartyPlan(condition.game(), condition.modeKey(),
                            reservation.getScheduledStart());
                });
    }

    @Override
    public void onConfirmed(List<UUID> sourceIds) {
        for (Reservation reservation : reservations.findAllById(sourceIds)) {
            reservation.markMatched();
            // 확정된 시간대는 더 이상 후보가 아니다.
            removeFromIndex(reservation);
        }
    }

    @Override
    public void onBroken(List<UUID> sourceIds) {
        for (Reservation reservation : reservations.findAllById(sourceIds)) {
            if (reservation.getStatus() != ReservationStatus.PROPOSED) {
                continue;
            }
            // 조건과 시간이 그대로이므로 다시 후보가 된다 (docs/04 §8).
            reservation.returnToActive();
        }
    }

    private void removeFromIndex(Reservation reservation) {
        MatchCondition condition = codec.fromJson(reservation.getConditionJson());
        OffsetDateTime from = reservation.getAvailableFrom();
        OffsetDateTime to = reservation.getAvailableTo();
        slots.remove(reservation.getId(), condition.game(), condition.modeKey(), from, to);
    }
}
