package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §7 TC-RESV. */
class ReservationApiContractTest extends ApiContractTestSupport {

    private static final String RESERVATIONS = "/api/v1/reservations";

    /** 30분 격자 위의 미래 시각. 만료 정리에 걸리지 않게 넉넉히 뒤로 둔다. */
    private static String slot(int halfHoursFromBase) {
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .truncatedTo(ChronoUnit.DAYS)
                .plusHours(21);
        return base.plusMinutes(30L * halfHoursFromBase).toString();
    }

    private static String reservation(String role, String from, String to, String playAmount) {
        return """
                {
                  "condition": {
                    "game": "VALORANT",
                    "modeKey": "COMPETITIVE",
                    "keyCondition": { "type": "ROLE", "value": "%s" },
                    "voicePreference": "REQUIRED",
                    "playPurpose": "RANK_UP"
                  },
                  "availableFrom": "%s",
                  "availableTo": "%s",
                  "playAmount": "%s"
                }
                """.formatted(role, from, to, playAmount);
    }

    private static String defaultReservation() {
        return reservation("DUELIST", slot(0), slot(4), "TWO_PLUS");
    }

    @Test
    @DisplayName("TC-RESV-01 예약을 만들면 ACTIVE이고 매칭 관련 필드는 전부 null이다")
    void createStartsActive() {
        UUID alpha = alpha();

        ResponseEntity<String> response = post(RESERVATIONS, alpha, defaultReservation());

        assertStatus(response, 201);
        JsonNode view = body(response);
        assertEquals("ACTIVE", view.path("status").asText());
        assertTrue(view.path("scheduledStart").isNull());
        assertTrue(view.path("proposalId").isNull());
        assertFalse(view.has("partyId"),
                "예약은 partyId를 싣지 않는다. proposalId로 제안을 거쳐 찾는다");
        assertEquals("TWO_PLUS", view.path("playAmount").asText());
        assertEquals("DUELIST", view.path("condition").path("keyCondition").path("value").asText());
    }

    @Test
    @DisplayName("TC-RESV-02 생성 응답에 createdAt이 실린다")
    void reservationViewCarriesCreatedAt() {
        UUID alpha = alpha();

        JsonNode view = body(post(RESERVATIONS, alpha, defaultReservation()));

        assertTrue(view.hasNonNull("createdAt"), "목록 정렬에 필요하다");
        OffsetDateTime.parse(view.path("createdAt").asText());
        assertEquals(9, view.size());
    }

    @Test
    @DisplayName("TC-RESV-03 30분 격자를 벗어난 시각은 400이다")
    void rejectsUnalignedMinute() {
        UUID alpha = alpha();
        String unaligned = OffsetDateTime.parse(slot(0)).plusMinutes(15).toString();

        assertError(post(RESERVATIONS, alpha, reservation("DUELIST", unaligned, slot(4), "TWO_PLUS")),
                400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-RESV-04 초가 0이 아니면 400이다")
    void rejectsNonZeroSeconds() {
        UUID alpha = alpha();
        String withSeconds = OffsetDateTime.parse(slot(0)).plusSeconds(30).toString();

        assertError(post(RESERVATIONS, alpha, reservation("DUELIST", withSeconds, slot(4), "TWO_PLUS")),
                400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-RESV-05 끝이 시작보다 앞이면 400이다")
    void rejectsInvertedWindow() {
        UUID alpha = alpha();

        assertError(post(RESERVATIONS, alpha, reservation("DUELIST", slot(4), slot(0), "TWO_PLUS")),
                400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-RESV-06 playAmount가 빠지면 400이다")
    void playAmountIsRequired() {
        UUID alpha = alpha();
        String missing = """
                {
                  "condition": {
                    "game": "VALORANT",
                    "modeKey": "COMPETITIVE",
                    "keyCondition": { "type": "ROLE", "value": "DUELIST" },
                    "voicePreference": "REQUIRED",
                    "playPurpose": "RANK_UP"
                  },
                  "availableFrom": "%s",
                  "availableTo": "%s"
                }
                """.formatted(slot(0), slot(4));

        assertError(post(RESERVATIONS, alpha, missing), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-RESV-07 없는 모드는 404다")
    void unknownModeIsNotFound() {
        UUID alpha = alpha();
        String body = defaultReservation().replace("\"COMPETITIVE\"", "\"ARAM\"");

        assertError(post(RESERVATIONS, alpha, body), 404, "UNKNOWN_GAME_MODE");
    }

    @Test
    @DisplayName("TC-RESV-08 시간이 겹치면 409다")
    void overlappingWindowConflicts() {
        UUID alpha = alpha();
        post(RESERVATIONS, alpha, defaultReservation());

        assertError(post(RESERVATIONS, alpha, reservation("SENTINEL", slot(2), slot(6), "TWO_PLUS")),
                409, "OVERLAPPING_RESERVATION");
    }

    @Test
    @DisplayName("TC-RESV-09 게임이 달라도 시간이 겹치면 막힌다")
    void overlapIgnoresGame() {
        UUID alpha = alpha();
        post(RESERVATIONS, alpha, defaultReservation());

        String pubg = """
                {
                  "condition": {
                    "game": "PUBG",
                    "modeKey": "SQUAD",
                    "keyCondition": { "type": "PLAY_STYLE", "value": "BALANCED" },
                    "voicePreference": "OPTIONAL",
                    "playPurpose": "FUN"
                  },
                  "availableFrom": "%s",
                  "availableTo": "%s",
                  "playAmount": "ONE_GAME"
                }
                """.formatted(slot(2), slot(6));

        assertError(post(RESERVATIONS, alpha, pubg), 409, "OVERLAPPING_RESERVATION");
    }

    @Test
    @DisplayName("TC-RESV-10 경계에 딱 붙는 예약은 겹치지 않는다")
    void adjacentWindowsDoNotOverlap() {
        UUID alpha = alpha();
        post(RESERVATIONS, alpha, defaultReservation());

        // 판정식이 [from, to) 반열림이다. 끝시각과 시작시각이 같으면 통과한다.
        assertStatus(post(RESERVATIONS, alpha, reservation("SENTINEL", slot(4), slot(8), "TWO_PLUS")),
                201);
    }

    @Test
    @DisplayName("TC-RESV-10b 취소한 예약은 그 시간대를 막지 않는다")
    void cancelledReservationReleasesTheWindow() {
        UUID alpha = alpha();
        String id = body(post(RESERVATIONS, alpha, defaultReservation())).path("id").asText();
        assertStatus(delete(RESERVATIONS + "/" + id, alpha), 204);

        // 시간을 점유하는 상태는 ACTIVE/PROPOSED/MATCHED 셋뿐이다.
        assertStatus(post(RESERVATIONS, alpha, defaultReservation()), 201);
    }

    @Test
    @DisplayName("TC-RESV-11 내 예약만 목록에 나온다")
    void listReturnsOnlyMine() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        post(RESERVATIONS, alpha, defaultReservation());
        post(RESERVATIONS, alpha, reservation("SENTINEL", slot(4), slot(8), "ONE_GAME"));
        post(RESERVATIONS, bravo, defaultReservation());

        JsonNode mine = body(get(RESERVATIONS, alpha));

        assertTrue(mine.isArray());
        assertEquals(2, mine.size());
    }

    @Test
    @DisplayName("TC-RESV-12 남의 예약 상세는 404다")
    void othersReservationIsNotFound() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String bravos = body(post(RESERVATIONS, bravo, defaultReservation())).path("id").asText();

        assertError(get(RESERVATIONS + "/" + bravos, alpha), 404, "RESERVATION_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-RESV-13 PUT은 전체 교체다. 한 필드만 보내면 400이다")
    void putRequiresEveryField() {
        UUID alpha = alpha();
        String id = body(post(RESERVATIONS, alpha, defaultReservation())).path("id").asText();

        assertError(put(RESERVATIONS + "/" + id, alpha, """
                { "playAmount": "ONE_GAME" }
                """), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-RESV-13b PATCH도 같은 동작으로 함께 받는다")
    void patchIsKeptAsAnAlias() {
        UUID alpha = alpha();
        String id = body(post(RESERVATIONS, alpha, defaultReservation())).path("id").asText();

        // 먼저 붙은 클라이언트를 깨지 않기 위해 남겨 둔 별칭이다.
        assertStatus(patch(RESERVATIONS + "/" + id, alpha,
                reservation("SENTINEL", slot(0), slot(4), "ONE_GAME")), 200);
    }

    @Test
    @DisplayName("TC-RESV-14 시간을 그대로 두고 조건만 바꾸는 수정은 통과한다")
    void editingConditionOnlyIsNotSelfOverlap() {
        UUID alpha = alpha();
        String id = body(post(RESERVATIONS, alpha, defaultReservation())).path("id").asText();

        // 겹침 검사에서 자기 자신을 빼지 않으면 여기서 409가 난다.
        ResponseEntity<String> response = put(RESERVATIONS + "/" + id, alpha,
                reservation("SENTINEL", slot(0), slot(4), "ONE_GAME"));

        assertStatus(response, 200);
        JsonNode view = body(response);
        assertEquals("SENTINEL", view.path("condition").path("keyCondition").path("value").asText());
        assertEquals("ONE_GAME", view.path("playAmount").asText());
    }

    @Test
    @DisplayName("TC-RESV-15 남의 예약은 수정도 404다")
    void editingOthersReservationIsNotFound() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String bravos = body(post(RESERVATIONS, bravo, defaultReservation())).path("id").asText();

        assertError(put(RESERVATIONS + "/" + bravos, alpha, defaultReservation()),
                404, "RESERVATION_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-RESV-16 예약을 취소하면 204다")
    void cancelReturnsNoContent() {
        UUID alpha = alpha();
        String id = body(post(RESERVATIONS, alpha, defaultReservation())).path("id").asText();

        assertStatus(delete(RESERVATIONS + "/" + id, alpha), 204);
        assertEquals("CANCELLED", body(get(RESERVATIONS + "/" + id, alpha)).path("status").asText());
    }

    @Test
    @DisplayName("TC-RESV-17 이미 취소한 예약을 또 취소해도 204다")
    void cancelIsIdempotent() {
        UUID alpha = alpha();
        String id = body(post(RESERVATIONS, alpha, defaultReservation())).path("id").asText();
        delete(RESERVATIONS + "/" + id, alpha);

        assertStatus(delete(RESERVATIONS + "/" + id, alpha), 204);
    }

    @Test
    @DisplayName("TC-RESV-18 없는 예약을 취소하면 404다")
    void cancellingUnknownReservationIsNotFound() {
        UUID alpha = alpha();

        assertError(delete(RESERVATIONS + "/" + NONE, alpha), 404, "RESERVATION_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-RESV-19 조건의 게임과 조건 종류가 어긋나면 400이다")
    void conditionTypeMustMatchGame() {
        UUID alpha = alpha();
        String mismatched = defaultReservation().replace("\"ROLE\"", "\"POSITION\"");

        assertError(post(RESERVATIONS, alpha, mismatched), 400, "VALIDATION_FAILED");
    }
}
