package com.queuemate.matching.controller;

import com.queuemate.common.error.ErrorResponse;
import com.queuemate.common.security.CurrentUserId;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.dto.AcceptedRequest;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.dto.JoinResult;
import com.queuemate.matching.dto.MatchRequestResponse;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchQueryService;
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
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/match-requests")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchConditionValidator matchConditionValidator;
    private final MatchRequestService matchRequestService;
    private final MatchTrigger matchTrigger;
    private final MatchCancelService matchCancelService;
    private final MatchQueryService matchQueryService;


    /**
     * 매칭을 시작한다. <b>요청한 사람은 access 토큰의 {@code sub} 다</b> — 본문의 {@code userId} 는 받지 않는다
     * (2026-09-27. 그 전에는 본문의 필수 필드였고 남의 번호로 요청할 수 있었다).
     */
    @PostMapping
    public ResponseEntity<?> createMatchRequest(@CurrentUserId String userId,
                                                @RequestBody @Valid CreateMatchRequestCommand request) {

        request.setUserId(userId);

        if (!matchConditionValidator.validate(request)) {
            return ResponseEntity.badRequest().body(ErrorResponse.of(
                    "INVALID_MATCH_CONDITION",
                    "지원하지 않는 매칭 조건입니다: " + request.getGame() + " / " + request.getModeKey()));
        }

        JoinResult result = matchRequestService.join(request);
        return switch (result.status()) {
            case ALREADY_QUEUED -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                    "ALREADY_QUEUED", "이미 진행 중인 매칭 요청이 있습니다"));
            // 한 사용자는 자동 매칭 대기와 게시판 방 중 한 번에 하나만 할 수 있다 (docs/11 D-11 15번)
            case IN_ROOM -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                    "IN_ROOM", "파티방에 들어가 있는 동안에는 매칭을 시작할 수 없습니다"));
            case ACCEPTED -> {
                matchTrigger.trigger(request);

                // 접수 응답도 조회와 같은 모양이다. requestId 를 돌려줘야 클라이언트가 취소를 부를 수 있다
                AcceptedRequest accepted = result.request();
                yield ResponseEntity.status(HttpStatus.CREATED)
                        .body(MatchRequestResponse.queued(accepted.requestId(), accepted.queuedAt()));
            }
        };
    }

    /**
     * 이 사용자의 매칭 요청이 지금 어떤 상태인지 답한다.
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
     * 게다가 Redis Pub/Sub 은 구독자가 없으면 그대로 버린다 — 페이지를 나가 있는 동안 발행된
     * 알림은 사라지고, 다시 들어와도 오지 않는다. 계약이 "놓친 상태는 REST 로 복구한다"고
     * 정해 둔 그 REST 가 이것이다.
     *
     * <h2>왜 userId 로 찾나</h2>
     * <b>활성 요청이 애초에 사용자 단위로 저장된다</b> ({@code qm:user:active-request:{userId}},
     * INV-1). {@code requestId} 는 그 HASH 안에 든 값이지 찾는 열쇠가 아니다.
     *
     * <p>그리고 이 조회가 가장 필요한 순간이 <b>페이지를 새로 열었을 때</b>인데, 그때
     * 클라이언트는 {@code requestId} 를 잃은 상태다. 그 값을 요구하면 정작 필요할 때 못 쓰는
     * API 가 된다. 취소({@code DELETE})가 {@code requestId} 를 받는 것은 <b>쓰기</b>라서다 —
     * 늦게 도착한 취소가 그 사이 새로 만든 요청을 지우면 안 되기 때문이고, 조회에는 그 위험이 없다.
     *
     * <p><b>계약에 없는 형태다.</b> 원본 계약({@code GET /match-requests/{requestId}})과 경로가
     * 다르므로 queueMate 본 저장소에서 contract 변경 커밋이 필요하다 (CLAUDE.md §5).
     * 그때까지의 불일치는 {@code contracts/README.md} 에 적어 두었다.
     *
     * <h2>무엇을 답하나</h2>
     * <ul>
     *   <li>{@code IDLE} — 활성 요청이 없다. 취소·만료로 빠진 경우도 여기로 온다
     *       (키를 지우므로 "원래 없었다"와 구분되지 않는다)
     *   <li>{@code QUEUED} — 기다리는 중. 파티가 잡혔으면 {@code target}/{@code memberCount} 로
     *       "3/5명"을 그릴 수 있다
     *   <li>{@code PROPOSED} — 제안이 떠 있다. {@code expiresAt} 으로 남은 시간을,
     *       {@code isAccepted} 로 내가 이미 눌렀는지를 안다
     *   <li>{@code MATCHED} — 확정됐다. 파티 상세는 app:platform 이 답한다
     * </ul>
     *
     * <p>파티 상세(누가 같이 있는지)는 여기서 답하지 않는다. 진행 중인 매칭 상태만 이 앱의
     * 소유이고, 확정된 파티는 {@code app:platform} 의 것이다 (CLAUDE.md §9).
     *
     * <p><b>"나"는 access 토큰의 {@code sub} 다</b> (2026-09-27 — 그 전에는 임시 쿼리 파라미터 {@code ?userId=} 였다).
     * 경로는 그대로 {@code GET /api/v1/match-requests} 다 — {@code /me} 로 옮기는 것은 계약(원본)과 같이 정할 일이라
     * 이번에 하지 않았다 ({@code contracts/README.md} #5).
     */
    @GetMapping
    public ResponseEntity<MatchRequestResponse> getMatchRequest(@CurrentUserId String userId) {

        return ResponseEntity.ok(matchQueryService.find(userId));
    }

    /**
     * 매칭 요청을 취소한다.
     *
     * 경로의 requestId는 "내가 아는 그 요청이 맞는지" 확인하는 용도다.
     * 늦게 도착한 취소가 그 사이 새로 만든 요청을 지우면 안 되므로,
     * 저장된 값과 같을 때만 지운다(compare-and-delete).
     *
     * 누구의 요청인지는 access 토큰의 {@code sub} 다 (2026-09-27 — 그 전에는 임시 {@code ?userId=}).
     */
    @DeleteMapping("/{requestId}")
    public ResponseEntity<?> cancelMatchRequest(@PathVariable String requestId,
                                                @CurrentUserId String userId) {

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
