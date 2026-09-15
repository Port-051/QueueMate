package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * docs/15 §12 TC-X. 리소스별이 아니라 모든 엔드포인트에 공통으로 도는 계약.
 *
 * <p>여기가 이 묶음에서 가장 값이 크다. 새 엔드포인트가 인증 없이 열리거나,
 * 예외 핸들러를 안 타는 경로가 생기거나, 카탈로그에 없는 에러 코드가 늘면 여기서 잡힌다.
 */
class CrossCuttingApiContractTest extends ApiContractTestSupport {

    /** docs/14 §0.7 카탈로그. 여기에 없는 code가 나가면 문서가 낡은 것이다. */
    private static final Set<String> ERROR_CODES = Set.of(
            "VALIDATION_FAILED", "UNAUTHORIZED", "UNSUPPORTED_MEDIA_TYPE",
            "USER_NOT_FOUND", "UNKNOWN_GAME", "UNKNOWN_GAME_MODE",
            "MATCH_REQUEST_NOT_FOUND", "PROPOSAL_NOT_FOUND", "PARTY_NOT_FOUND",
            "RESERVATION_NOT_FOUND", "FRIENDSHIP_NOT_FOUND", "BLOCK_NOT_FOUND",
            "GAME_ACCOUNT_NOT_FOUND", "FRIEND_REQUEST_NOT_FOUND",
            "EMAIL_ALREADY_IN_USE", "NICKNAME_ALREADY_IN_USE",
            "EMAIL_OR_NICKNAME_ALREADY_IN_USE", "GAME_ACCOUNT_ALREADY_LINKED",
            "ACTIVE_MATCH_REQUEST_EXISTS", "MATCH_REQUEST_NOT_CANCELLABLE",
            "PROPOSAL_EXPIRED", "PROPOSAL_NOT_PENDING", "PROPOSAL_NOT_CONFIRMED",
            "PROPOSAL_NOT_FULLY_ACCEPTED", "PROPOSAL_MEMBER_MISMATCH",
            "PARTY_SIZE_MISMATCH", "PARTY_CLOSED", "PARTY_PLAYING", "ALREADY_LEFT",
            "BLOCKED_MEMBERS",
            "RESERVATION_NOT_EDITABLE", "RESERVATION_NOT_CANCELLABLE",
            "OVERLAPPING_RESERVATION",
            "SELF_FRIEND_REQUEST", "SELF_BLOCK", "SELF_REPORT",
            "ALREADY_FRIENDS", "REQUEST_ALREADY_PENDING", "INVERSE_REQUEST_PENDING",
            "FRIEND_REQUEST_NOT_PENDING", "BLOCKED_RELATION", "ALREADY_BLOCKED",
            "SIGNUP_RATE_EXCEEDED", "LOGIN_ATTEMPTS_EXCEEDED", "MATCHING_UNAVAILABLE");

    /** 인증이 필요한 엔드포인트. SecurityConfig의 permitAll 목록 밖은 전부 여기 들어와야 한다. */
    private record Endpoint(HttpMethod method, String path, String body) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private static Stream<Endpoint> securedEndpoints() {
        String id = UUID.randomUUID().toString();
        return Stream.of(
                new Endpoint(HttpMethod.GET, "/api/v1/users/me", null),
                new Endpoint(HttpMethod.PATCH, "/api/v1/users/me", "{\"nickname\":\"새이름\"}"),
                new Endpoint(HttpMethod.GET, "/api/v1/users/me/game-accounts", null),
                new Endpoint(HttpMethod.POST, "/api/v1/users/me/game-accounts",
                        "{\"game\":\"LOL\",\"externalGameId\":\"A#KR1\",\"region\":\"KR\"}"),
                new Endpoint(HttpMethod.DELETE, "/api/v1/users/me/game-accounts/" + id, null),
                new Endpoint(HttpMethod.GET, "/api/v1/games", null),
                new Endpoint(HttpMethod.GET, "/api/v1/games/LOL/modes", null),
                new Endpoint(HttpMethod.GET, "/api/v1/games/LOL/match-schema", null),
                new Endpoint(HttpMethod.POST, "/api/v1/match-requests", lolCondition("MID")),
                new Endpoint(HttpMethod.GET, "/api/v1/match-requests/" + id, null),
                new Endpoint(HttpMethod.DELETE, "/api/v1/match-requests/" + id, null),
                new Endpoint(HttpMethod.GET, "/api/v1/proposals/" + id, null),
                new Endpoint(HttpMethod.POST, "/api/v1/proposals/" + id + "/accept", null),
                new Endpoint(HttpMethod.POST, "/api/v1/proposals/" + id + "/decline", null),
                new Endpoint(HttpMethod.GET, "/api/v1/reservations", null),
                new Endpoint(HttpMethod.GET, "/api/v1/reservations/" + id, null),
                new Endpoint(HttpMethod.PUT, "/api/v1/reservations/" + id,
                        "{\"playAmount\":\"ONE_GAME\"}"),
                new Endpoint(HttpMethod.DELETE, "/api/v1/reservations/" + id, null),
                new Endpoint(HttpMethod.GET, "/api/v1/parties/" + id, null),
                new Endpoint(HttpMethod.POST, "/api/v1/parties/" + id + "/ready", "{\"ready\":true}"),
                new Endpoint(HttpMethod.POST, "/api/v1/parties/" + id + "/leave", null),
                new Endpoint(HttpMethod.GET, "/api/v1/friends", null),
                new Endpoint(HttpMethod.DELETE, "/api/v1/friends/" + id, null),
                new Endpoint(HttpMethod.GET, "/api/v1/friend-requests", null),
                new Endpoint(HttpMethod.POST, "/api/v1/friend-requests",
                        "{\"targetUserId\":\"" + id + "\"}"),
                new Endpoint(HttpMethod.POST, "/api/v1/friend-requests/" + id + "/accept", null),
                new Endpoint(HttpMethod.POST, "/api/v1/friend-requests/" + id + "/decline", null),
                new Endpoint(HttpMethod.DELETE, "/api/v1/friend-requests/" + id, null),
                new Endpoint(HttpMethod.GET, "/api/v1/blocks", null),
                new Endpoint(HttpMethod.POST, "/api/v1/blocks", "{\"targetUserId\":\"" + id + "\"}"),
                new Endpoint(HttpMethod.DELETE, "/api/v1/blocks/" + id, null),
                new Endpoint(HttpMethod.GET, "/api/v1/recent-players", null),
                new Endpoint(HttpMethod.POST, "/api/v1/reports",
                        "{\"targetUserId\":\"" + id + "\",\"reason\":\"OTHER\"}"));
    }

    /** 바디를 받는 엔드포인트만. Content-Type과 깨진 JSON 케이스가 쓴다. */
    private static Stream<Endpoint> bodyEndpoints() {
        return securedEndpoints().filter(endpoint -> endpoint.body() != null);
    }

    @ParameterizedTest(name = "TC-X-01 {0}")
    @MethodSource("securedEndpoints")
    @DisplayName("TC-X-01 토큰이 없으면 401이고 본문이 없다")
    void everySecuredEndpointRejectsAnonymous(Endpoint endpoint) {
        ResponseEntity<String> response =
                send(endpoint.method(), endpoint.path(), null, endpoint.body());

        assertEquals(401, response.getStatusCode().value(),
                () -> endpoint + "가 인증 없이 열려 있다. SecurityConfig를 확인하라");
        assertEquals("UNAUTHORIZED", body(response).path("code").asText(),
                () -> endpoint + "의 401이 공통 오류 형태를 따르지 않는다");
    }

    @ParameterizedTest(name = "TC-X-02 {0}")
    @MethodSource("securedEndpoints")
    @DisplayName("TC-X-02 깨진 토큰도 401이다")
    void everySecuredEndpointRejectsGarbageToken(Endpoint endpoint) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("garbage.not.a.jwt");
        if (endpoint.body() != null) {
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        }

        ResponseEntity<String> response = http.exchange(endpoint.path(), endpoint.method(),
                new HttpEntity<>(endpoint.body(), headers), String.class);

        assertEquals(401, response.getStatusCode().value(), endpoint::toString);
    }

    @Test
    @DisplayName("TC-X-03 공개 엔드포인트 4개는 인증 필터에 막히지 않는다")
    void publicEndpointsStayPublic() {
        List<Endpoint> open = List.of(
                new Endpoint(HttpMethod.POST, "/api/v1/auth/signup",
                        "{\"email\":\"x@queuemate.dev\",\"password\":\"" + PASSWORD
                                + "\",\"nickname\":\"엑스\"}"),
                new Endpoint(HttpMethod.POST, "/api/v1/auth/login",
                        "{\"email\":\"x@queuemate.dev\",\"password\":\"" + PASSWORD + "\"}"),
                new Endpoint(HttpMethod.POST, "/api/v1/auth/refresh",
                        "{\"refreshToken\":\"whatever\"}"),
                new Endpoint(HttpMethod.POST, "/api/v1/auth/logout",
                        "{\"refreshToken\":\"whatever\"}"));

        for (Endpoint endpoint : open) {
            ResponseEntity<String> response =
                    send(endpoint.method(), endpoint.path(), null, endpoint.body());
            // 공개 엔드포인트는 컨트롤러까지 도달해야 한다. refresh/logout은 가짜 토큰에
            // 401을 주지만 그건 컨트롤러의 판단이고, 여기서 보려는 건 "필터에 막혔나"다.
            // 둘 다 401이라 상태 코드로는 못 가른다. 400이 아닌 4xx 중 컨트롤러가
            // 만들어 낸 것인지를 code로 확인한다.
            int status = response.getStatusCode().value();
            assertTrue(status < 500, () -> endpoint + " → " + status);
            if (status == 401) {
                assertEquals("UNAUTHORIZED", body(response).path("code").asText());
            }
        }
    }

    @ParameterizedTest(name = "TC-X-04 {0}")
    @MethodSource("bodyEndpoints")
    @DisplayName("TC-X-04 Content-Type이 JSON이 아니면 415다")
    void wrongContentTypeIsUnsupportedMediaType(Endpoint endpoint) {
        UUID alpha = alpha();

        ResponseEntity<String> response =
                postWithoutContentType(endpoint.path(), alpha, endpoint.body());

        // POST가 아닌 엔드포인트는 405가 날 수 있으므로 415와 405를 함께 받는다.
        int status = response.getStatusCode().value();
        assertTrue(status == 415 || status == 405,
                () -> endpoint + " → " + status + " (415나 405를 기대했다)");
        if (status == 415) {
            assertEquals("UNSUPPORTED_MEDIA_TYPE", body(response).path("code").asText());
        }
    }

    @Test
    @DisplayName("TC-X-05 깨진 JSON은 400이다")
    void malformedJsonIsBadRequest() {
        UUID alpha = alpha();

        assertError(post("/api/v1/blocks", alpha, "{"), 400, "VALIDATION_FAILED");
        assertError(patch("/api/v1/users/me", alpha, "{\"nickname\":"), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-X-06 경로의 uuid 자리에 이상한 값이 오면 400이다")
    void invalidUuidPathVariableIsBadRequest() {
        UUID alpha = alpha();

        assertError(get("/api/v1/match-requests/not-a-uuid", alpha), 400, "VALIDATION_FAILED");
        assertError(get("/api/v1/reservations/not-a-uuid", alpha), 400, "VALIDATION_FAILED");
        assertError(get("/api/v1/parties/not-a-uuid", alpha), 400, "VALIDATION_FAILED");
        assertError(delete("/api/v1/blocks/not-a-uuid", alpha), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-X-07 4xx 본문은 {code, message} 2필드이고 code가 카탈로그 안에 있다")
    void everyHandledErrorFollowsTheContract() {
        UUID alpha = alpha();
        UUID bravo = bravo();

        List<ResponseEntity<String>> failures = List.of(
                post("/api/v1/blocks", alpha, "{\"targetUserId\":\"" + alpha + "\"}"),
                post("/api/v1/blocks", alpha, "{\"targetUserId\":\"" + NONE + "\"}"),
                post("/api/v1/blocks", alpha, "{}"),
                delete("/api/v1/blocks/" + bravo, alpha),
                delete("/api/v1/friends/" + bravo, alpha),
                post("/api/v1/friend-requests", alpha, "{\"targetUserId\":\"" + alpha + "\"}"),
                post("/api/v1/match-requests", alpha, lolCondition("JUNGLER")),
                get("/api/v1/match-requests/" + NONE, alpha),
                get("/api/v1/reservations/" + NONE, alpha),
                get("/api/v1/parties/" + NONE, alpha),
                get("/api/v1/proposals/" + NONE, alpha),
                post("/api/v1/reports", alpha,
                        "{\"targetUserId\":\"" + alpha + "\",\"reason\":\"OTHER\"}"));

        for (ResponseEntity<String> response : failures) {
            int status = response.getStatusCode().value();
            assertTrue(status >= 400 && status < 500, () -> "4xx를 기대했다: " + status);

            JsonNode error = body(response);
            assertEquals(2, error.size(),
                    () -> "에러 본문은 code와 message뿐이다: " + response.getBody());
            String code = error.path("code").asText();
            assertTrue(ERROR_CODES.contains(code),
                    () -> "카탈로그에 없는 code다: " + code + " — docs/14 §0.7을 갱신하라");
            assertTrue(error.path("message").asText().length() > 0);
        }
    }

    @Test
    @DisplayName("TC-X-08 201 응답에 Location 헤더가 붙지 않는다")
    void createdResponsesCarryNoLocationHeader() {
        UUID alpha = alpha();
        UUID bravo = bravo();

        List<ResponseEntity<String>> created = List.of(
                post("/api/v1/users/me/game-accounts", alpha,
                        "{\"game\":\"LOL\",\"externalGameId\":\"A#KR1\",\"region\":\"KR\"}"),
                post("/api/v1/match-requests", alpha, lolCondition("MID")),
                post("/api/v1/friend-requests", alpha, "{\"targetUserId\":\"" + bravo + "\"}"),
                post("/api/v1/blocks", alpha, "{\"targetUserId\":\"" + charlie() + "\"}"));

        for (ResponseEntity<String> response : created) {
            assertEquals(201, response.getStatusCode().value(), () -> "본문: " + response.getBody());
            // Location을 붙이기 시작하면 계약이 바뀐 것이다. 클라이언트는 본문의 id를 읽는다.
            assertNull(response.getHeaders().getFirst("Location"));
        }
    }

    @Test
    @DisplayName("TC-X-09 응답의 날짜는 전부 OffsetDateTime으로 파싱된다")
    void everyTimestampIsIsoOffsetDateTime() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        post("/api/v1/friend-requests", alpha, "{\"targetUserId\":\"" + bravo + "\"}");

        JsonNode queued = body(post("/api/v1/match-requests", alpha, lolCondition("MID")));
        OffsetDateTime.parse(queued.path("queuedAt").asText());

        JsonNode requests = body(get("/api/v1/friend-requests?direction=SENT", alpha));
        OffsetDateTime.parse(requests.get(0).path("createdAt").asText());

        // 생성 응답의 blockedAt은 지금 null이다 (docs/14 §11-11). 목록에서는 제대로 온다.
        post("/api/v1/blocks", alpha, "{\"targetUserId\":\"" + charlie() + "\"}");
        JsonNode blocks = body(get("/api/v1/blocks", alpha));
        OffsetDateTime.parse(blocks.get(0).path("blockedAt").asText());
    }
}
