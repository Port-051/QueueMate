package com.queuemate.matching.controller;

import com.queuemate.common.error.ErrorResponse;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.dto.MatchRequestResponse;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.MatchTrigger;
import com.queuemate.matching.validation.MatchConditionValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/match-requests")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchConditionValidator matchConditionValidator;
    private final MatchRequestService matchRequestService;
    private final MatchTrigger matchTrigger;
    private final MatchCancelService matchCancelService;

    @PostMapping
    public ResponseEntity<?> createMatchRequest(@RequestBody @Valid CreateMatchRequestCommand request) {

        if (!matchConditionValidator.validate(request)) {
            return ResponseEntity.badRequest().body(ErrorResponse.of(
                    "INVALID_MATCH_CONDITION",
                    "지원하지 않는 매칭 조건입니다: " + request.getGame() + " / " + request.getModeKey()));
        }

        Optional<String> requestId = matchRequestService.join(request);
        if (requestId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                    "ALREADY_QUEUED", "이미 진행 중인 매칭 요청이 있습니다"));
        }

        matchTrigger.trigger(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MatchRequestResponse(requestId.get(), MatchRequestStatus.QUEUED));
    }

    /**
     * 매칭 요청의 현재 상태를 조회한다. <b>아직 구현되지 않았다 — 501 을 돌려준다.</b>
     *
     * <h2>왜 필요한가</h2>
     * 알림(SSE)은 <b>사건</b>을 전한다. "방금 이렇게 됐다"는 말은 하지만 "지금 이렇다"는
     * 말은 못 한다. 그래서 <b>새로 접속한 클라이언트는 아무것도 알 수 없다.</b>
     *
     * <p>앱을 껐다 켜거나 새로고침하면 이렇게 어긋난다.
     * <pre>
     * 서버:  이 사람 매칭 대기 중, 파티에도 들어가 있음
     * 클라:  아무것도 모름 → "매칭 시작" 버튼을 그림 → 누르면 409 ALREADY_QUEUED
     * </pre>
     * 클라이언트가 상태를 로컬에 들고 있는 것으로는 못 푼다. 다른 기기에서는 없고,
     * 서버에서 이미 만료됐는데 클라만 "대기 중"으로 아는 경우가 더 나쁘다.
     * <b>물어볼 곳이 있어야 한다.</b>
     *
     * <p>알림은 휘발성이라 재전송 보장이 없다는 것도 같은 결론으로 간다 — 계약이
     * "놓친 상태는 REST 로 복구한다"고 정해 두었는데 그 REST 가 이것이다.
     *
     * <h2>구현할 때 같이 정해야 하는 것</h2>
     * <b>requestId 를 모르는 경우를 덮지 못한다.</b> 응답을 못 받았거나 클라이언트가 그 값을
     * 잃으면, 취소({@code DELETE /match-requests/{requestId}})도 이 조회도 부를 수 없다.
     * 파티에 배정되고 나면 claim 의 TTL 도 떼이므로(claim-request.lua) 스스로 빠져나올
     * 길이 없어진다.
     *
     * <p>그래서 <b>userId 로 찾는 판</b>이 함께 필요하다 — {@code GET /match-requests?userId=}
     * 같은 모양이다. 계약에는 없으므로 queueMate 본 저장소에서 contract 변경 커밋을
     * 먼저 만들어야 한다 (CLAUDE.md §5).
     *
     * <p>응답 형태도 계약과 맞춰야 한다. 계약의 {@code MatchRequestView} 는
     * {@code {id, status, queuedAt, proposalId}} 인데 구현의
     * {@link com.queuemate.matching.dto.MatchRequestResponse} 는 {@code {requestId, status}} 뿐이다
     * (contracts/README.md 불일치 표 4번). {@code queuedAt} / {@code proposalId} 를 채우려면
     * proposal 구현이 먼저다.
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<?> getMatchRequest(@PathVariable String requestId,
                                             @RequestParam String userId) {

        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(ErrorResponse.of(
                "NOT_IMPLEMENTED", "매칭 요청 조회는 아직 구현되지 않았습니다"));
    }

    /**
     * 매칭 요청을 취소한다.
     *
     * 경로의 requestId는 "내가 아는 그 요청이 맞는지" 확인하는 용도다.
     * 늦게 도착한 취소가 그 사이 새로 만든 요청을 지우면 안 되므로,
     * 저장된 값과 같을 때만 지운다(compare-and-delete).
     *
     * userId는 JWT를 붙이기 전까지만 쓰는 임시 파라미터다.
     */
    @DeleteMapping("/{requestId}")
    public ResponseEntity<?> cancelMatchRequest(@PathVariable String requestId,
                                                @RequestParam String userId) {

        CancelResult result = matchCancelService.cancel(userId, requestId);

        return switch (result) {
            case CANCELLED, CANCELLED_AND_PARTY_CLOSED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "MATCH_REQUEST_NOT_FOUND", "진행 중인 매칭 요청이 없습니다"));
            case REQUEST_MISMATCH -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                    "MATCH_REQUEST_MISMATCH", "이미 종료된 매칭 요청입니다: " + requestId));
        };
    }
}
