package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §5 TC-MATCH. 매칭 규칙 자체는 RealtimeMatchingIntegrationTest가 본다. */
class MatchRequestApiContractTest extends ApiContractTestSupport {

    private static final String MATCH_REQUESTS = "/api/v1/match-requests";

    @Test
    @DisplayName("TC-MATCH-01 매칭을 시작하면 QUEUED 4필드가 온다")
    void startQueuesTheRequest() {
        UUID alpha = alpha();

        ResponseEntity<String> response = post(MATCH_REQUESTS, alpha, lolCondition("MID"));

        assertStatus(response, 201);
        JsonNode view = body(response);
        assertTrue(view.hasNonNull("id"));
        assertEquals("QUEUED", view.path("status").asText());
        assertTrue(view.has("proposalId"));
        assertTrue(view.path("proposalId").isNull(), "대기 중에는 제안이 없다");
        assertNotNull(OffsetDateTime.parse(view.path("queuedAt").asText()));
        assertEquals(4, view.size());
        assertNull(response.getHeaders().getFirst("Location"));
    }

    @Test
    @DisplayName("TC-MATCH-02 keyCondition 값은 소문자와 공백을 정규화한다")
    void keyConditionValueIsNormalized() {
        UUID alpha = alpha();

        String body = """
                {
                  "game": "LOL",
                  "modeKey": "SOLO_DUO_RANKED",
                  "keyCondition": { "type": "position", "value": " mid " },
                  "voicePreference": "OPTIONAL",
                  "playPurpose": "RANK_UP"
                }
                """;

        assertStatus(post(MATCH_REQUESTS, alpha, body), 201);
    }

    @Test
    @DisplayName("TC-MATCH-03 enum 필드는 소문자를 안 봐준다. 같은 바디에서 규칙이 다르다")
    void enumFieldsAreCaseSensitive() {
        UUID alpha = alpha();

        String body = """
                {
                  "game": "LOL",
                  "modeKey": "SOLO_DUO_RANKED",
                  "keyCondition": { "type": "POSITION", "value": "MID" },
                  "voicePreference": "optional",
                  "playPurpose": "RANK_UP"
                }
                """;

        assertStatus(post(MATCH_REQUESTS, alpha, body), 400);
    }

    @Test
    @DisplayName("TC-MATCH-04 게임과 조건 종류가 어긋나면 400이다")
    void gameAndConditionTypeMustAgree() {
        UUID alpha = alpha();

        String body = """
                {
                  "game": "LOL",
                  "modeKey": "SOLO_DUO_RANKED",
                  "keyCondition": { "type": "ROLE", "value": "DUELIST" },
                  "voicePreference": "OPTIONAL",
                  "playPurpose": "RANK_UP"
                }
                """;

        assertError(post(MATCH_REQUESTS, alpha, body), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-MATCH-05 게임이 모르는 조건 값은 400이다")
    void unknownConditionValueIsRejected() {
        UUID alpha = alpha();

        assertError(post(MATCH_REQUESTS, alpha, lolCondition("JUNGLER")), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-MATCH-06 없는 모드는 404 UNKNOWN_GAME_MODE다")
    void unknownModeIsNotFound() {
        UUID alpha = alpha();

        String body = """
                {
                  "game": "LOL",
                  "modeKey": "ARAM",
                  "keyCondition": { "type": "POSITION", "value": "MID" },
                  "voicePreference": "OPTIONAL",
                  "playPurpose": "RANK_UP"
                }
                """;

        assertError(post(MATCH_REQUESTS, alpha, body), 404, "UNKNOWN_GAME_MODE");
    }

    @Test
    @DisplayName("TC-MATCH-07 이미 대기 중이면 409다")
    void secondRequestConflicts() {
        UUID alpha = alpha();
        post(MATCH_REQUESTS, alpha, lolCondition("MID"));

        assertError(post(MATCH_REQUESTS, alpha, lolCondition("TOP")),
                409, "ACTIVE_MATCH_REQUEST_EXISTS");
    }

    @Test
    @DisplayName("TC-MATCH-08 상태 조회는 생성과 같은 4필드다")
    void getReturnsSameShape() {
        UUID alpha = alpha();
        String id = body(post(MATCH_REQUESTS, alpha, lolCondition("MID"))).path("id").asText();

        JsonNode view = body(get(MATCH_REQUESTS + "/" + id, alpha));

        assertEquals(id, view.path("id").asText());
        assertEquals("QUEUED", view.path("status").asText());
        assertEquals(4, view.size());
    }

    @Test
    @DisplayName("TC-MATCH-09 남의 매칭 요청은 404다")
    void othersRequestIsNotFound() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String bravosRequest =
                body(post(MATCH_REQUESTS, bravo, lolCondition("TOP"))).path("id").asText();

        assertError(get(MATCH_REQUESTS + "/" + bravosRequest, alpha),
                404, "MATCH_REQUEST_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-MATCH-10 취소하면 다시 시작할 수 있다")
    void cancelFreesTheUser() {
        UUID alpha = alpha();
        String id = body(post(MATCH_REQUESTS, alpha, lolCondition("MID"))).path("id").asText();

        assertStatus(delete(MATCH_REQUESTS + "/" + id, alpha), 204);

        // guard까지 풀려야 재시작이 된다. Redis만 지우고 DB를 안 지우면 여기서 409가 난다.
        assertStatus(post(MATCH_REQUESTS, alpha, lolCondition("MID")), 201);
    }

    @Test
    @DisplayName("TC-MATCH-11 없는 매칭 요청을 취소하면 404다")
    void cancellingUnknownRequestIsNotFound() {
        UUID alpha = alpha();

        assertError(delete(MATCH_REQUESTS + "/" + NONE, alpha), 404, "MATCH_REQUEST_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-MATCH-12 토큰이 없으면 401이고 형태는 다른 4xx와 같다")
    void anonymousCannotQueue() {
        assertError(post(MATCH_REQUESTS, null, lolCondition("MID")), 401, "UNAUTHORIZED");
    }
}
