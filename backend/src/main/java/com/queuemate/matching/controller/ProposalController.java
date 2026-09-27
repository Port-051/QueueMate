package com.queuemate.matching.controller;

import com.queuemate.common.error.ErrorResponse;
import com.queuemate.common.security.CurrentUserId;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.service.ProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제안 수락 / 거절.
 *
 * <p><b>왜 REST 인가.</b> 수락은 알림이 아니라 <b>명령</b>이다. 서버가 거절할 수 있고
 * (만료됐다 / 다른 사람이 거절했다 / 이미 확정됐다) 요청자가 그 결과를 알아야 한다.
 * 그런 확인 응답은 대상 서버만 만들 수 있으므로 연결 전담 앱을 거치는 경로로는 대신할 수
 * 없다. 게다가 서버→클라 알림 경로인 Redis Pub/Sub 은 at-most-once 라, 명령을 태우면
 * 유실됐을 때 클라이언트가 알 방법이 없다.
 *
 * <p>나가는 알림({@code MATCH_CONFIRMED} 등)만 Pub/Sub 을 탄다. <b>들어오는 명령은 REST,
 * 나가는 통보는 Pub/Sub</b> 이다.
 *
 * <p><b>{@code proposalId} 는 partyId 다.</b> proposal 하나는 언제나 하나의 party 를
 * 뜻하므로(CLAUDE.md §1) 식별자를 따로 두지 않는다. {@code MATCH_PROPOSAL_CREATED}
 * 알림의 payload 에 실려 나간 그 값을 클라이언트가 그대로 되돌려 보낸다.
 *
 * <p><b>구현 상태.</b> 수락 집계(INV-4 / INV-5), 만료 스위퍼, 확정 뒷정리와 알림까지 붙었다.
 * 아직 없는 것은 {@code matching.outbox} 기록 → {@code ProposalConfirmed.fifo} 발행(파티를
 * DB 에 만드는 것은 app:platform 이다)과 {@code PartyClosed} 소비다 — {@link ProposalService} 참고.
 */
@RestController
@RequestMapping("/api/v1/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;

    /**
     * 제안을 수락한다.
     *
     * <p>수락하는 사람은 access 토큰의 {@code sub} 다 (2026-09-27 — 그 전에는 임시 {@code ?userId=} 여서
     * 남의 번호로 수락할 수 있었다).
     */
    @PostMapping("/{proposalId}/accept")
    public ResponseEntity<?> accept(@PathVariable String proposalId,
                                    @CurrentUserId String userId) {

        ProposalResult result = proposalService.accept(proposalId, userId);

        return switch (result)
        {
            // 돌려줄 것이 없다. 내 수락이 반영됐다는 사실은 이 응답 코드가 말하고,
            // 제안이 확정됐는지는 MATCH_CONFIRMED 알림이 말한다.
            //
            // 수락 진행상황("5명 중 3명")을 본문에 싣지 않는 이유: 그 숫자가 바뀔 때
            // 알려 주는 이벤트가 계약에 없다 (contracts/README.md "미해결 계약 구멍").
            // 한 번 받고 영영 안 바뀌는 숫자는 안 보여주는 것보다 나쁘다.
            //
            // 알림을 놓쳤을 때의 복구는 조회로 한다 (MatchingController#getMatchRequest).
            //
            // ALREADY_RESPONDED 를 여기 묶는 이유: 수락에서 이 값은 "이미 확정된 제안에
            // 또 수락이 왔다"는 뜻이다(accept-proposal.lua). 확정이 유효한데 재전송한
            // 쪽만 실패로 받으면, 응답이 유실돼 자동 재시도한 사용자에게 오류가 뜬다.
            // 같은 명령을 두 번 보내 결과가 같으면 성공으로 돌려준다.
            //
            // 확정 알림을 두 번 보내지 않으려고 스크립트가 값을 갈라 놓은 것이지
            // 호출이 실패했다는 뜻이 아니다 — 거절 쪽 ALREADY_RESPONDED 는 409 그대로다
            case ACCEPTED, CONFIRMED, ALREADY_RESPONDED -> ResponseEntity.noContent().build();

            // 수락을 눌렀는데 그사이 다른 참가자가 거절해 제안이 깨진 경우다
            case DECLINED -> conflict(proposalId, "다른 참가자가 거절한 제안입니다: ");

            case NOT_A_MEMBER -> forbidden(proposalId);

            // 제안이 없다 — 정원 미달 · 만료 · 다른 참가자의 거절. 클라이언트가 할 일은
            // 전부 같다 (대기 화면 복귀 후 상태 조회)
            case NOT_FOUND -> notFound(proposalId);
        };
    }

    /**
     * 제안을 거절한다.
     *
     * <p>수락과 같이 204 다. 계약({@code contracts/openapi.yaml})은 200 으로 적혀 있으나
     * 본문 스키마가 없어, 취소 엔드포인트와 같은 형태로 맞췄다.
     * 불일치는 {@code contracts/README.md} 에 기록한다.
     *
     * <p>거절하는 사람은 access 토큰의 {@code sub} 다(2026-09-27). {@code ?requestId=} 는 그대로 받는다 — 임시 식별이 아니라
     * "내가 아는 그 요청이 맞는지"를 보는 값이다.
     */
    @PostMapping("/{proposalId}/decline")
    public ResponseEntity<?> decline(@PathVariable String proposalId,
                                     @CurrentUserId String userId) {

        ProposalResult result = proposalService.decline(proposalId, userId);

        return switch (result) {
            case DECLINED -> ResponseEntity.noContent().build();

            // 수락해 놓고 거절을 누른 경우에만 온다. 거절 재시도는 여기로 오지 않는다 —
            // 거절이 제안 흔적을 지우므로 재시도는 NOT_FOUND 다 (decline-proposal.lua)
            case ALREADY_RESPONDED -> conflict(proposalId, "이미 응답한 제안입니다: ");

            // 거절을 눌렀는데 그사이 전원 수락으로 확정된 경우다.
            // 확정은 되돌릴 수 없다 (INV-5)
            case ACCEPTED, CONFIRMED -> conflict(proposalId, "이미 확정된 제안입니다: ");

            case NOT_A_MEMBER -> forbidden(proposalId);

            // 이미 깨진 제안이다. 같은 거절의 재시도도 여기로 온다 — 거절은 멱등이 아니라
            // 첫 호출만 204 이고 재시도는 404 다. 클라이언트가 할 일은 두 경우 같다
            // (대기 화면 복귀). 근거는 decline-proposal.lua 의 "멱등성" 절에 있다
            case NOT_FOUND -> notFound(proposalId);
        };
    }

    /** 409 — 지금 상태와 맞지 않는다. 상태가 바뀌면 다시 시도할 수 있다는 뜻이다 */
    private ResponseEntity<?> conflict(String proposalId, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                "PROPOSAL_CONFLICT", message + proposalId));
    }

    private ResponseEntity<?> notFound(String proposalId) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                "PROPOSAL_NOT_FOUND", "진행 중인 제안이 없습니다: " + proposalId));
    }

    /**
     * 남의 제안에 응답하려 한 경우.
     *
     * <p>404 가 아니라 403 인 것은, 제안이 존재한다는 사실 자체는 숨길 값이 없기 때문이다.
     * 2026-09-27 부터 userId 가 토큰에서 나오므로, 이 갈래는 남의 제안 id 를 넣은 경우에만 온다.
     */
    private ResponseEntity<?> forbidden(String proposalId) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(
                "NOT_PROPOSAL_MEMBER", "이 제안의 참가자가 아닙니다: " + proposalId));
    }
}
