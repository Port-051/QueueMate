package com.queuemate.reservation.app;

import com.queuemate.common.domain.PlayAmount;
import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.infra.AfterCommit;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.reservation.domain.Reservation;
import com.queuemate.reservation.domain.ReservationStatus;
import com.queuemate.reservation.infra.ReservationRepository;
import com.queuemate.reservation.infra.ReservationSlotIndex;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 예약 등록/수정/취소 (docs/04).
 *
 * <p>INV-9를 두 겹으로 지킨다. DB의 EXCLUDE 제약이 ACTIVE/PROPOSED 겹침을 막고,
 * 여기서 MATCHED까지 포함해 한 번 더 본다. 이미 약속을 잡은 시간대에 다른 약속을 만들 수 없다.
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> TIME_OCCUPYING = List.of(
            ReservationStatus.ACTIVE, ReservationStatus.PROPOSED, ReservationStatus.MATCHED);
    private static final String OVERLAP_CODE = "OVERLAPPING_RESERVATION";

    private final ReservationRepository reservations;
    private final ReservationSlotIndex slots;
    private final GameModeConfigProvider modes;
    private final MatchConditionCodec codec;
    private final ProposalService proposals;

    public ReservationService(ReservationRepository reservations, ReservationSlotIndex slots,
                              GameModeConfigProvider modes, MatchConditionCodec codec,
                              ProposalService proposals) {
        this.reservations = reservations;
        this.slots = slots;
        this.modes = modes;
        this.codec = codec;
        this.proposals = proposals;
    }

    @Transactional
    public Reservation create(UUID userId, MatchCondition condition,
                              OffsetDateTime availableFrom, OffsetDateTime availableTo,
                              PlayAmount playAmount) {
        requireActiveMode(condition);
        Reservation reservation = Reservation.create(
                userId, codec.toJson(condition), availableFrom, availableTo, playAmount);
        requireNoOverlap(userId, availableFrom, availableTo, reservation.getId());

        save(reservation);
        UUID id = reservation.getId();
        AfterCommit.run(() ->
                slots.index(id, condition.game(), condition.modeKey(), availableFrom, availableTo));
        return reservation;
    }

    /** ACTIVE인 예약만 고칠 수 있다. 슬롯 색인은 지웠다가 다시 넣는다 (docs/07 §8). */
    @Transactional
    public Reservation edit(UUID userId, UUID reservationId, MatchCondition condition,
                            OffsetDateTime availableFrom, OffsetDateTime availableTo,
                            PlayAmount playAmount) {
        Reservation reservation = owned(userId, reservationId);
        if (!reservation.getStatus().isEditable()) {
            throw new ConflictException("RESERVATION_NOT_EDITABLE",
                    "지금은 수정할 수 없는 상태다: " + reservation.getStatus());
        }
        requireActiveMode(condition);
        requireNoOverlap(userId, availableFrom, availableTo, reservationId);

        MatchCondition previous = codec.fromJson(reservation.getConditionJson());
        OffsetDateTime previousFrom = reservation.getAvailableFrom();
        OffsetDateTime previousTo = reservation.getAvailableTo();

        reservation.edit(codec.toJson(condition), availableFrom, availableTo, playAmount);
        save(reservation);
        // 색인 교체는 커밋 뒤에 한 번에 한다. 먼저 지우고 롤백되면 DB에는 예약이 남는데
        // 색인만 사라져 그 예약이 영영 후보에 오르지 않는다.
        AfterCommit.run(() -> {
            slots.remove(reservationId, previous.game(), previous.modeKey(), previousFrom, previousTo);
            slots.index(reservationId, condition.game(), condition.modeKey(), availableFrom, availableTo);
        });
        return reservation;
    }

    /**
     * 예약을 취소한다.
     *
     * <p>제안 중인 예약도 취소할 수 있다. 예약은 미래의 약속이라 사용자가 철회할 수 있어야 한다.
     * 다만 그 경우 제안 전체를 먼저 파기해서, 나머지 참가자가 이미 사라진 사람과
     * 파티를 확정하려다 실패하는 일이 없게 한다 (docs/04 §10).
     */
    @Transactional
    public void cancel(UUID userId, UUID reservationId) {
        Reservation reservation = owned(userId, reservationId);
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return;
        }
        if (reservation.getStatus() == ReservationStatus.PROPOSED) {
            // 제안을 닫으면 이 예약도 ACTIVE로 돌아온다. 그 뒤에 취소한다.
            proposals.cancelForWithdrawnSource(reservation.getProposalId());
        }
        if (!reservation.getStatus().canTransitionTo(ReservationStatus.CANCELLED)) {
            throw new ConflictException("RESERVATION_NOT_CANCELLABLE",
                    "지금은 취소할 수 없는 상태다: " + reservation.getStatus());
        }
        MatchCondition condition = codec.fromJson(reservation.getConditionJson());
        OffsetDateTime from = reservation.getAvailableFrom();
        OffsetDateTime to = reservation.getAvailableTo();
        AfterCommit.run(() ->
                slots.remove(reservationId, condition.game(), condition.modeKey(), from, to));
        reservation.cancel();
    }

    /**
     * 플레이 가능 시간이 지나 버린 예약을 만료시킨다.
     *
     * <p>정리하지 않으면 이미 지난 시간대가 계속 후보로 올라오고, 그 시간대에
     * 새 예약을 잡으려는 사용자가 겹침으로 거절당한다.
     *
     * @return 만료시킨 예약 수
     */
    @Transactional
    public int expireOverdue(OffsetDateTime now) {
        List<Reservation> overdue = reservations
                .findAllByStatusAndAvailableToLessThanEqual(ReservationStatus.ACTIVE, now);
        for (Reservation reservation : overdue) {
            MatchCondition condition = codec.fromJson(reservation.getConditionJson());
            UUID id = reservation.getId();
            OffsetDateTime from = reservation.getAvailableFrom();
            OffsetDateTime to = reservation.getAvailableTo();
            AfterCommit.run(() ->
                    slots.remove(id, condition.game(), condition.modeKey(), from, to));
            reservation.expire();
        }
        return overdue.size();
    }

    @Transactional(readOnly = true)
    public List<Reservation> list(UUID userId) {
        return reservations.findAllByUserIdOrderByAvailableFromAsc(userId);
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID userId, UUID reservationId) {
        return owned(userId, reservationId);
    }

    /** 저장한 조건을 도메인으로 돌려준다. 컨트롤러가 응답을 그릴 때 쓴다. */
    public MatchCondition conditionOf(Reservation reservation) {
        return codec.fromJson(reservation.getConditionJson());
    }

    private void save(Reservation reservation) {
        try {
            reservations.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // reservations_no_active_overlap
            throw new ConflictException(OVERLAP_CODE, "같은 시간대에 이미 예약이 있다");
        }
    }

    private void requireNoOverlap(UUID userId, OffsetDateTime from, OffsetDateTime to, UUID excludedId) {
        if (!reservations.findOverlapping(userId, TIME_OCCUPYING, from, to, excludedId).isEmpty()) {
            throw new ConflictException(OVERLAP_CODE, "같은 시간대에 이미 예약이 있다");
        }
    }

    private Reservation owned(UUID userId, UUID reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> notFound(reservationId));
        if (!reservation.getUserId().equals(userId)) {
            throw notFound(reservationId);
        }
        return reservation;
    }

    private void requireActiveMode(MatchCondition condition) {
        modes.findActive(condition.game(), condition.modeKey())
                .orElseThrow(() -> new NotFoundException("UNKNOWN_GAME_MODE",
                        "지원하지 않는 게임 모드다: " + condition.game() + "/" + condition.modeKey()));
    }

    private static NotFoundException notFound(UUID reservationId) {
        return new NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없다: " + reservationId);
    }
}
