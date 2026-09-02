package com.queuemate.matching.api;

import com.queuemate.common.api.MatchConditionRequest;
import com.queuemate.common.security.CurrentUser;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.domain.KeyCondition;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.domain.MatchRequestStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 실시간 매칭 요청 API (contracts/openapi.yaml /match-requests). */
@RestController
@RequestMapping("/api/v1/match-requests")
public class MatchController {

    private final MatchRequestService service;

    public MatchController(MatchRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MatchRequestView> create(CurrentUser currentUser,
                                                   @Valid @RequestBody MatchConditionRequest body) {
        MatchRequest request = service.start(currentUser.userId(), toDomain(body));
        return ResponseEntity.status(HttpStatus.CREATED).body(MatchRequestView.of(request));
    }

    @GetMapping("/{id}")
    public MatchRequestView get(CurrentUser currentUser, @PathVariable UUID id) {
        return MatchRequestView.of(service.get(currentUser.userId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(CurrentUser currentUser, @PathVariable UUID id) {
        service.cancel(currentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 계약의 문자열 조건을 도메인으로 옮긴다.
     * 게임에 맞지 않는 종류나 값이면 여기서 400으로 끝난다.
     */
    private static MatchCondition toDomain(MatchConditionRequest body) {
        KeyConditionType type = parseType(body.keyCondition().type());
        return new MatchCondition(
                body.game(),
                body.modeKey(),
                KeyCondition.of(body.game(), type, body.keyCondition().value()),
                body.voicePreference(),
                body.playPurpose());
    }

    private static KeyConditionType parseType(String raw) {
        try {
            return KeyConditionType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 조건 종류다: " + raw, e);
        }
    }

    /** openapi MatchRequestView. */
    public record MatchRequestView(
            UUID id,
            MatchRequestStatus status,
            OffsetDateTime queuedAt,
            UUID proposalId
    ) {
        static MatchRequestView of(MatchRequest request) {
            return new MatchRequestView(
                    request.getId(), request.getStatus(), request.getQueuedAt(), request.getProposalId());
        }
    }
}
