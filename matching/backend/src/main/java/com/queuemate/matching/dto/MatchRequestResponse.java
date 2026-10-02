package com.queuemate.matching.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.queuemate.matching.domain.MatchRequestStatus;

import static com.queuemate.matching.domain.MatchRequestStatus.*;

/**
 * 매칭 요청 하나의 상태. 요청 접수(201)와 상태 조회(200)가 같은 모양을 쓴다 —
 * 둘 다 "이 사람의 매칭 요청이 지금 어떤 상태인가"에 답하고, 계약에도 {@code MatchRequestView}
 * 하나뿐이다 (contracts/openapi.yaml).
 *
 * <p><b>갈래마다 채워지는 칸이 다르다.</b> {@link #status} 만 항상 있고 나머지는 그 갈래에서
 * 뜻이 있을 때만 채운다. 비는 칸은 {@code @JsonInclude(NON_NULL)} 이 응답에서 통째로 빼므로,
 * 클라이언트는 {@code status} 로 갈래를 정하고 있는 필드만 읽으면 된다.
 *
 * <p><b>값이 없을 수 있는 칸은 원시 타입이 아니라 래퍼다.</b> Redis HASH 는 필드가 없으면
 * {@code null} 을 돌려주는데, 조회가 그것 때문에 터지면 사용자는 아무 상태도 못 본다.
 * 예를 들어 {@code queuedAt} 은 나중에 추가한 필드라 그 전에 큐에 들어간 요청에는 없다.
 *
 * <p>정적 팩토리를 두는 이유는 호출부가 {@code null} 을 여러 개 나열하지 않게 하려는 것이다.
 * 어느 갈래에 무엇이 필요한지가 이 파일 한 곳에 모인다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchRequestResponse(

        /** 어느 갈래인지. 이것만 항상 있다 */
        MatchRequestStatus status,

        String requestId,

        /** 줄 선 시각(epoch millis). 대기 시간은 클라이언트가 이 값으로 구한다 */
        Long queuedAt,

        /** 배정된 파티. 확정되면 그대로 proposalId 이기도 하다 */
        String partyId,

        /** QUEUED 에서 "3/5명" 을 그리는 값 */
        Integer target,
        Integer memberCount,

        /** PROPOSED 의 시한(epoch millis). 남은 시간은 클라이언트가 구한다 */
        Long expiresAt,

        /** PROPOSED 에서 내가 이미 수락했는지. 돌아온 사람이 볼 화면이 이것으로 갈린다 */
        Boolean isAccepted) {

    /** 활성 요청이 없다 — 아무것도 안 하는 중이다 */
    public static MatchRequestResponse idle() {
        return new MatchRequestResponse(IDLE, null, null, null, null, null, null, null);
    }

    /** 접수 직후 — 아직 배정 전이다 */
    public static MatchRequestResponse queued(String requestId, Long queuedAt) {
        return new MatchRequestResponse(QUEUED, requestId, queuedAt, null, null, null, null, null);
    }

    /** 파티는 잡혔고 아직 모으는 중 */
    public static MatchRequestResponse queued(String requestId, Long queuedAt,
                                              String partyId, Integer target, Integer memberCount) {
        return new MatchRequestResponse(QUEUED, requestId, queuedAt, partyId, target, memberCount, null, null);
    }

    /** 제안이 떠 있다 */
    public static MatchRequestResponse proposed(String requestId, Long queuedAt,
                                                String partyId, Long expiresAt, boolean isAccepted) {
        return new MatchRequestResponse(PROPOSED, requestId, queuedAt, partyId, null, null, expiresAt, isAccepted);
    }

    /** 확정됐다. 파티 상세는 app:platform 이 답한다 */
    public static MatchRequestResponse matched(String requestId, Long queuedAt, String partyId) {
        return new MatchRequestResponse(MATCHED, requestId, queuedAt, partyId, null, null, null, null);
    }
}
