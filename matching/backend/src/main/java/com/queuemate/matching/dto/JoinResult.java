package com.queuemate.matching.dto;

import java.util.Optional;

/**
 * 매칭 요청 접수 시도의 결과. 컨트롤러가 {@link #status} 로 응답 코드를 정한다.
 *
 * <p>거절 갈래가 둘이라 {@code Optional<AcceptedRequest>} 로는 모자란다 — 둘 다 409 지만
 * 클라이언트가 할 일이 다르다. {@link Status#ALREADY_QUEUED} 면 상태 조회로 대기 화면을 복구하고,
 * {@link Status#IN_ROOM} 이면 방에서 나와야 한다.
 *
 * @param request 접수됐을 때만 있다. 거절이면 {@code null} 이다 — 꺼낼 때는 {@link #accepted()} 를 쓴다
 */
public record JoinResult(Status status, AcceptedRequest request) {

    public enum Status {

        /** 활성 요청 자리를 선점했다 */
        ACCEPTED,

        /** 이미 활성 요청이 있다 (INV-1) */
        ALREADY_QUEUED,

        /** 게시판 방에 들어가 있다. app:room 의 입장 표시 키가 있다 (docs/11 D-11 15번) */
        IN_ROOM
    }

    public static JoinResult accepted(String requestId, long queuedAt) {
        return new JoinResult(Status.ACCEPTED, new AcceptedRequest(requestId, queuedAt));
    }

    public static JoinResult alreadyQueued() {
        return new JoinResult(Status.ALREADY_QUEUED, null);
    }

    public static JoinResult inRoom() {
        return new JoinResult(Status.IN_ROOM, null);
    }

    /** 접수됐으면 그 요청, 거절됐으면 빈 값 */
    public Optional<AcceptedRequest> accepted() {
        return Optional.ofNullable(request);
    }
}
