package com.queuemate.reservation.api;

import com.queuemate.common.api.MatchConditionRequest;
import com.queuemate.common.domain.PlayAmount;
import com.queuemate.common.security.CurrentUser;
import com.queuemate.matching.domain.KeyCondition;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.reservation.app.ReservationMatcher;
import com.queuemate.reservation.app.ReservationService;
import com.queuemate.reservation.domain.Reservation;
import com.queuemate.reservation.domain.ReservationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 예약 매칭 API (contracts/openapi.yaml /reservations). */
@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService service;
    private final ReservationMatcher matcher;

    public ReservationController(ReservationService service, ReservationMatcher matcher) {
        this.service = service;
        this.matcher = matcher;
    }

    @PostMapping
    public ResponseEntity<ReservationView> create(CurrentUser currentUser,
                                                  @Valid @RequestBody CreateReservationRequest body) {
        Reservation reservation = service.create(currentUser.userId(), toDomain(body.condition()),
                body.availableFrom(), body.availableTo(), body.playAmount());
        matchNow(reservation.getId());
        // 방금 매칭됐다면 상태가 PROPOSED로 바뀌었다. 저장 시점 객체가 아니라 다시 읽어 응답한다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(service.get(currentUser.userId(), reservation.getId())));
    }

    @GetMapping
    public List<ReservationView> list(CurrentUser currentUser) {
        return service.list(currentUser.userId()).stream().map(this::view).toList();
    }

    @GetMapping("/{id}")
    public ReservationView get(CurrentUser currentUser, @PathVariable UUID id) {
        return view(service.get(currentUser.userId(), id));
    }

    /**
     * 예약을 통째로 바꾼다.
     *
     * <p>네 필드가 전부 필수라 의미는 전체 교체다. 그래서 PUT이 정본이다.
     * PATCH는 먼저 붙은 클라이언트가 있어 당분간 같은 동작으로 함께 받는다.
     */
    @RequestMapping(method = {RequestMethod.PUT, RequestMethod.PATCH}, path = "/{id}")
    public ReservationView edit(CurrentUser currentUser, @PathVariable UUID id,
                                @Valid @RequestBody CreateReservationRequest body) {
        service.edit(currentUser.userId(), id, toDomain(body.condition()),
                body.availableFrom(), body.availableTo(), body.playAmount());
        matchNow(id);
        return view(service.get(currentUser.userId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(CurrentUser currentUser, @PathVariable UUID id) {
        service.cancel(currentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 등록 직후 바로 후보를 훑는다 (docs/04 §6).
     *
     * <p>여기서 실패해도 사용자의 등록은 이미 성공했다. 매칭 실패를 5xx로 돌려주면
     * 사용자는 실패한 줄 알고 다시 시도하다 겹침 409를 만난다. 놓친 조합은 주기 sweep이 잡는다.
     */
    private void matchNow(UUID reservationId) {
        try {
            matcher.tryMatchFor(reservationId);
        } catch (RuntimeException e) {
            log.warn("등록 직후 매칭에 실패했다. sweep이 다시 시도한다 reservationId={}",
                    reservationId, e);
        }
    }

    private ReservationView view(Reservation reservation) {
        MatchCondition condition = service.conditionOf(reservation);
        return new ReservationView(
                reservation.getId(),
                reservation.getStatus(),
                ConditionView.of(condition),
                reservation.getAvailableFrom(),
                reservation.getAvailableTo(),
                reservation.getPlayAmount(),
                reservation.getCreatedAt(),
                reservation.getScheduledStart(),
                // partyId는 싣지 않는다. 예약에는 party_id 컬럼이 없어 늘 null이 나갔다.
                // 실시간 매칭(MatchRequestView)도 proposalId만 주고 파티는 제안을 통해 찾는다.
                // 두 흐름의 규칙을 같게 둔다: proposalId → GET /proposals/{id} → partyId.
                reservation.getProposalId());
    }

    private static MatchCondition toDomain(MatchConditionRequest body) {
        KeyConditionType type;
        try {
            type = KeyConditionType.valueOf(
                    body.keyCondition().type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 조건 종류다: " + body.keyCondition().type(), e);
        }
        return new MatchCondition(body.game(), body.modeKey(),
                KeyCondition.of(body.game(), type, body.keyCondition().value()),
                body.voicePreference(), body.playPurpose());
    }

    /** openapi CreateReservationRequest. */
    public record CreateReservationRequest(
            @Valid @NotNull MatchConditionRequest condition,
            @NotNull OffsetDateTime availableFrom,
            @NotNull OffsetDateTime availableTo,
            @NotNull PlayAmount playAmount
    ) {
    }

    /** openapi ReservationView. */
    public record ReservationView(
            UUID id,
            ReservationStatus status,
            ConditionView condition,
            OffsetDateTime availableFrom,
            OffsetDateTime availableTo,
            PlayAmount playAmount,
            OffsetDateTime createdAt,
            OffsetDateTime scheduledStart,
            UUID proposalId
    ) {
    }

    /** openapi MatchCondition. */
    public record ConditionView(
            com.queuemate.common.domain.GameKey game,
            String modeKey,
            KeyConditionView keyCondition,
            com.queuemate.common.domain.VoicePreference voicePreference,
            com.queuemate.common.domain.PlayPurpose playPurpose
    ) {
        static ConditionView of(MatchCondition condition) {
            return new ConditionView(condition.game(), condition.modeKey(),
                    new KeyConditionView(condition.keyCondition().type(),
                            condition.keyCondition().value()),
                    condition.voicePreference(), condition.playPurpose());
        }

        public record KeyConditionView(KeyConditionType type, String value) {
        }
    }
}
