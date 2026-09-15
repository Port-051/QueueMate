package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §11 TC-MISC. */
class MiscApiContractTest extends ApiContractTestSupport {

    private static final String RECENT = "/api/v1/recent-players";
    private static final String REPORTS = "/api/v1/reports";

    private static String report(UUID target, String reason, String description) {
        String descriptionField = description == null ? "null" : "\"" + description + "\"";
        return """
                { "targetUserId": "%s", "reason": "%s", "description": %s, "partyId": null }
                """.formatted(target, reason, descriptionField);
    }

    @Test
    @DisplayName("TC-MISC-01 이력이 없으면 빈 배열이다")
    void emptyRecentPlayersIsEmptyArray() {
        UUID alpha = alpha();

        JsonNode players = body(get(RECENT, alpha));

        assertTrue(players.isArray());
        assertEquals(0, players.size());
    }

    @Test
    @DisplayName("TC-MISC-03 limit=0은 400이다")
    void limitBelowRangeIsBadRequest() {
        UUID alpha = alpha();

        assertError(get(RECENT + "?limit=0", alpha), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-MISC-04 limit=51도 400이다")
    void limitAboveRangeIsBadRequest() {
        UUID alpha = alpha();

        assertError(get(RECENT + "?limit=51", alpha), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-MISC-05 limit 경계 1과 50은 통과한다")
    void limitBoundariesPass() {
        UUID alpha = alpha();

        assertStatus(get(RECENT + "?limit=1", alpha), 200);
        assertStatus(get(RECENT + "?limit=50", alpha), 200);
    }

    @Test
    @DisplayName("TC-MISC-06 신고는 201인데 본문이 없다")
    void reportReturnsCreatedWithoutBody() {
        UUID alpha = alpha();
        UUID charlie = charlie();

        ResponseEntity<String> response =
                post(REPORTS, alpha, report(charlie, "ABUSIVE_LANGUAGE", "파티에서 욕설을 반복했다"));

        assertStatus(response, 201);
        // 201인데 id를 안 준다. 신고 상태 조회나 중복 방지를 하려면 계약을 바꿔야 한다.
        assertNull(response.getBody(), "신고 응답에는 본문이 없다");
    }

    @Test
    @DisplayName("TC-MISC-07 reason이 빠지면 400이다")
    void reasonIsRequired() {
        UUID alpha = alpha();
        UUID charlie = charlie();

        assertError(post(REPORTS, alpha, """
                { "targetUserId": "%s" }
                """.formatted(charlie)), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-MISC-08 description이 1000자를 넘으면 400이다")
    void descriptionHasUpperBound() {
        UUID alpha = alpha();
        UUID charlie = charlie();

        assertError(post(REPORTS, alpha, report(charlie, "OTHER", "가".repeat(1001))),
                400, "VALIDATION_FAILED");
        assertStatus(post(REPORTS, alpha, report(charlie, "OTHER", "가".repeat(1000))), 201);
    }

    @Test
    @DisplayName("TC-MISC-09 enum 밖의 사유는 {code, message} 형태로 400이다")
    void unknownReasonIsRejected() {
        UUID alpha = alpha();
        UUID charlie = charlie();

        assertError(post(REPORTS, alpha, report(charlie, "SPAM", null)), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-MISC-10 자기 자신은 신고할 수 없다")
    void selfReportConflicts() {
        UUID alpha = alpha();

        assertError(post(REPORTS, alpha, report(alpha, "OTHER", null)), 409, "SELF_REPORT");
    }

    @Test
    @DisplayName("TC-MISC-11 같은 사람을 몇 번이든 신고할 수 있다")
    void duplicateReportsAreAllowed() {
        UUID alpha = alpha();
        UUID charlie = charlie();
        post(REPORTS, alpha, report(charlie, "HARASSMENT", null));

        // 중복 신고를 막는 코드가 없다. 막을 생각이면 여기서부터 정해야 한다.
        assertStatus(post(REPORTS, alpha, report(charlie, "HARASSMENT", null)), 201);
    }

    @Test
    @DisplayName("TC-MISC-12 없는 사용자를 신고하면 404다")
    void reportingUnknownUserIsNotFound() {
        UUID alpha = alpha();

        assertError(post(REPORTS, alpha, report(NONE, "OTHER", null)), 404, "USER_NOT_FOUND");
    }
}
