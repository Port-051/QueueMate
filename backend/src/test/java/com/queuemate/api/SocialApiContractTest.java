package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §9 TC-FRIEND, §10 TC-BLOCK. 관계 규칙 자체는 SocialIntegrationTest가 본다. */
class SocialApiContractTest extends ApiContractTestSupport {

    private static final String FRIENDS = "/api/v1/friends";
    private static final String REQUESTS = "/api/v1/friend-requests";
    private static final String BLOCKS = "/api/v1/blocks";

    private static String target(UUID userId) {
        return """
                { "targetUserId": "%s" }
                """.formatted(userId);
    }

    /** 알파와 브라보를 친구로 만든다. 요청 → 수락 두 단계를 HTTP로 통과시킨다. */
    private String befriend(UUID alpha, UUID bravo) {
        String requestId = body(post(REQUESTS, alpha, target(bravo))).path("id").asText();
        post(REQUESTS + "/" + requestId + "/accept", bravo, null);
        return requestId;
    }

    // ------------------------------------------------------------- 친구

    @Test
    @DisplayName("TC-FRIEND-01 친구가 없으면 빈 배열이다")
    void emptyFriendListIsEmptyArray() {
        UUID alpha = alpha();

        JsonNode friends = body(get(FRIENDS, alpha));

        assertTrue(friends.isArray());
        assertEquals(0, friends.size());
    }

    @Test
    @DisplayName("TC-FRIEND-02 친구 목록의 userId는 상대방 id다")
    void friendListCarriesCounterpartId() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        befriend(alpha, bravo);

        JsonNode friends = body(get(FRIENDS, alpha));

        assertEquals(1, friends.size());
        assertEquals(bravo.toString(), friends.get(0).path("userId").asText());
        assertEquals("bravo", friends.get(0).path("nickname").asText());
        assertTrue(friends.get(0).hasNonNull("friendedAt"));
    }

    @Test
    @DisplayName("TC-FRIEND-03 요청을 보내면 direction이 SENT로 실린다")
    void sentRequestCarriesSentDirection() {
        UUID alpha = alpha();
        UUID bravo = bravo();

        ResponseEntity<String> response = post(REQUESTS, alpha, target(bravo));

        assertStatus(response, 201);
        JsonNode view = body(response);
        assertEquals("SENT", view.path("direction").asText());
        assertEquals("PENDING", view.path("status").asText());
        assertTrue(view.hasNonNull("createdAt"), "생성 응답에도 시각이 실려야 한다");
        assertEquals(bravo.toString(), view.path("counterpartUserId").asText());
        assertEquals("bravo", view.path("counterpartNickname").asText());
    }

    @Test
    @DisplayName("TC-FRIEND-04 targetUserId가 빠지면 400이다")
    void targetIsRequired() {
        UUID alpha = alpha();

        assertError(post(REQUESTS, alpha, "{}"), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-FRIEND-05 없는 사용자에게 요청하면 404다")
    void unknownTargetIsNotFound() {
        UUID alpha = alpha();

        assertError(post(REQUESTS, alpha, target(NONE)), 404, "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-FRIEND-06 자기 자신에게 요청하면 409다")
    void selfRequestConflicts() {
        UUID alpha = alpha();

        assertError(post(REQUESTS, alpha, target(alpha)), 409, "SELF_FRIEND_REQUEST");
    }

    @Test
    @DisplayName("TC-FRIEND-07·08 내가 보낸 중복과 상대가 보낸 중복은 코드가 다르다")
    void duplicateAndInverseHaveDifferentCodes() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        UUID charlie = charlie();

        post(REQUESTS, alpha, target(bravo));
        assertError(post(REQUESTS, alpha, target(bravo)), 409, "REQUEST_ALREADY_PENDING");

        post(REQUESTS, charlie, target(alpha));
        // 프론트는 이 코드를 받으면 "요청 보내기"가 아니라 "수락하기"를 띄워야 한다.
        assertError(post(REQUESTS, alpha, target(charlie)), 409, "INVERSE_REQUEST_PENDING");
    }

    @Test
    @DisplayName("TC-FRIEND-09·10 누가 차단했든 같은 BLOCKED_RELATION이다")
    void blockDirectionIsNotLeaked() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        UUID charlie = charlie();

        post(BLOCKS, alpha, target(bravo));
        assertError(post(REQUESTS, alpha, target(bravo)), 409, "BLOCKED_RELATION");

        post(BLOCKS, charlie, target(alpha));
        // 어느 쪽이 차단했는지 알려주면 차단 사실이 샌다.
        assertError(post(REQUESTS, alpha, target(charlie)), 409, "BLOCKED_RELATION");
    }

    @Test
    @DisplayName("TC-FRIEND-11·12 direction 기본값은 RECEIVED다")
    void directionDefaultsToReceived() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        post(REQUESTS, bravo, target(alpha));

        JsonNode received = body(get(REQUESTS, alpha));
        assertEquals(1, received.size());
        assertEquals("RECEIVED", received.get(0).path("direction").asText());
        assertEquals(bravo.toString(), received.get(0).path("counterpartUserId").asText());

        assertEquals(0, body(get(REQUESTS + "?direction=SENT", alpha)).size());
        assertEquals(1, body(get(REQUESTS + "?direction=SENT", bravo)).size());
    }

    @Test
    @DisplayName("TC-FRIEND-13 direction에 소문자를 넣으면 400이다")
    void directionIsCaseSensitive() {
        UUID alpha = alpha();

        assertError(get(REQUESTS + "?direction=received", alpha), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-FRIEND-14 수락 응답은 FriendRequestView가 아니라 FriendView다")
    void acceptReturnsFriendView() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String requestId = body(post(REQUESTS, alpha, target(bravo))).path("id").asText();

        ResponseEntity<String> response = post(REQUESTS + "/" + requestId + "/accept", bravo, null);

        assertStatus(response, 200);
        JsonNode view = body(response);
        assertFalse(view.has("direction"), "FriendRequestView가 오면 계약이 바뀐 것이다");
        assertEquals(alpha.toString(), view.path("userId").asText());
        // 생성 응답도 조회와 같은 값을 준다. @Generated(INSERT)가 이걸 보장한다.
        assertTrue(view.hasNonNull("friendedAt"), "생성 응답에도 시각이 실려야 한다");
        OffsetDateTime.parse(view.path("friendedAt").asText());
    }

    @Test
    @DisplayName("TC-FRIEND-15 보낸 사람은 자기 요청을 수락할 수 없다")
    void senderCannotAcceptOwnRequest() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String requestId = body(post(REQUESTS, alpha, target(bravo))).path("id").asText();

        assertError(post(REQUESTS + "/" + requestId + "/accept", alpha, null),
                404, "FRIEND_REQUEST_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-FRIEND-16 이미 처리된 요청을 또 수락하면 409다")
    void acceptingTwiceConflicts() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String requestId = befriend(alpha, bravo);

        assertError(post(REQUESTS + "/" + requestId + "/accept", bravo, null),
                409, "FRIEND_REQUEST_NOT_PENDING");
    }

    @Test
    @DisplayName("TC-FRIEND-17 거절은 204다")
    void declineReturnsNoContent() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String requestId = body(post(REQUESTS, alpha, target(bravo))).path("id").asText();

        assertStatus(post(REQUESTS + "/" + requestId + "/decline", bravo, null), 204);
        assertEquals(0, body(get(FRIENDS, alpha)).size());
    }

    @Test
    @DisplayName("TC-FRIEND-18·19 보낸 요청 취소는 발신자만 할 수 있다")
    void onlySenderCancels() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String requestId = body(post(REQUESTS, alpha, target(bravo))).path("id").asText();

        assertError(delete(REQUESTS + "/" + requestId, bravo), 404, "FRIEND_REQUEST_NOT_FOUND");
        assertStatus(delete(REQUESTS + "/" + requestId, alpha), 204);
    }

    @Test
    @DisplayName("TC-FRIEND-20 친구를 지우면 양쪽 목록에서 사라진다")
    void removingFriendClearsBothSides() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        befriend(alpha, bravo);

        assertStatus(delete(FRIENDS + "/" + bravo, alpha), 204);

        assertEquals(0, body(get(FRIENDS, alpha)).size());
        assertEquals(0, body(get(FRIENDS, bravo)).size());
    }

    @Test
    @DisplayName("TC-FRIEND-21 친구가 아닌 사람을 지우면 404다")
    void removingNonFriendIsNotFound() {
        UUID alpha = alpha();
        UUID bravo = bravo();

        assertError(delete(FRIENDS + "/" + bravo, alpha), 404, "FRIENDSHIP_NOT_FOUND");
    }

    // ------------------------------------------------------------- 차단

    @Test
    @DisplayName("TC-BLOCK-01 차단이 없으면 빈 배열이다")
    void emptyBlockListIsEmptyArray() {
        UUID alpha = alpha();

        assertEquals(0, body(get(BLOCKS, alpha)).size());
    }

    @Test
    @DisplayName("TC-BLOCK-02 차단 응답에는 avatarUrl이 없다")
    void blockViewHasNoAvatar() {
        UUID alpha = alpha();
        UUID charlie = charlie();

        ResponseEntity<String> response = post(BLOCKS, alpha, target(charlie));

        assertStatus(response, 201);
        JsonNode view = body(response);
        assertEquals(charlie.toString(), view.path("userId").asText());
        assertEquals("charlie", view.path("nickname").asText());
        assertFalse(view.has("avatarUrl"), "차단 목록에 얼굴을 띄우지 않는다");
        assertEquals(3, view.size());
        assertTrue(view.hasNonNull("blockedAt"), "생성 응답에도 시각이 실려야 한다");
        OffsetDateTime.parse(view.path("blockedAt").asText());
    }

    @Test
    @DisplayName("TC-BLOCK-03 targetUserId가 빠지면 400이다")
    void blockTargetIsRequired() {
        UUID alpha = alpha();

        assertError(post(BLOCKS, alpha, "{}"), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-BLOCK-04 없는 사용자를 차단하면 404다")
    void blockingUnknownUserIsNotFound() {
        UUID alpha = alpha();

        assertError(post(BLOCKS, alpha, target(NONE)), 404, "USER_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-BLOCK-05 자기 자신은 차단할 수 없다")
    void selfBlockConflicts() {
        UUID alpha = alpha();

        assertError(post(BLOCKS, alpha, target(alpha)), 409, "SELF_BLOCK");
    }

    @Test
    @DisplayName("TC-BLOCK-06 같은 사람을 두 번 차단하면 409다")
    void duplicateBlockConflicts() {
        UUID alpha = alpha();
        UUID charlie = charlie();
        post(BLOCKS, alpha, target(charlie));

        assertError(post(BLOCKS, alpha, target(charlie)), 409, "ALREADY_BLOCKED");
    }

    @Test
    @DisplayName("TC-BLOCK-07 차단하면 친구 관계와 대기 중 요청이 함께 사라진다")
    void blockingClearsFriendshipAndPendingRequests() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        UUID charlie = charlie();
        befriend(alpha, bravo);
        post(REQUESTS, alpha, target(charlie));

        assertStatus(post(BLOCKS, alpha, target(bravo)), 201);

        assertEquals(0, body(get(FRIENDS, alpha)).size(), "차단은 친구 관계를 끊는다");
        assertStatus(post(BLOCKS, alpha, target(charlie)), 201);
        assertEquals(0, body(get(REQUESTS + "?direction=SENT", alpha)).size(),
                "차단은 대기 중 요청도 정리한다");
    }

    @Test
    @DisplayName("TC-BLOCK-08·09 차단 해제는 차단한 적이 있어야 한다")
    void unblockRequiresAnExistingBlock() {
        UUID alpha = alpha();
        UUID charlie = charlie();

        assertError(delete(BLOCKS + "/" + charlie, alpha), 404, "BLOCK_NOT_FOUND");

        post(BLOCKS, alpha, target(charlie));
        assertStatus(delete(BLOCKS + "/" + charlie, alpha), 204);
    }

    @Test
    @DisplayName("TC-BLOCK-10 차단을 풀면 다시 친구 요청을 보낼 수 있다")
    void unblockReopensTheRelation() {
        UUID alpha = alpha();
        UUID charlie = charlie();
        post(BLOCKS, alpha, target(charlie));
        assertError(post(REQUESTS, alpha, target(charlie)), 409, "BLOCKED_RELATION");

        delete(BLOCKS + "/" + charlie, alpha);

        assertStatus(post(REQUESTS, alpha, target(charlie)), 201);
    }

    @Test
    @DisplayName("TC-BLOCK-11 토큰이 없으면 차단 목록도 401이다")
    void blocksRequireAuthentication() {
        assertError(get(BLOCKS, null), 401, "UNAUTHORIZED");
    }
}
