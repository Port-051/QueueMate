package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §3 TC-USER. */
class UserApiContractTest extends ApiContractTestSupport {

    private static final String ME = "/api/v1/users/me";
    private static final String GAME_ACCOUNTS = "/api/v1/users/me/game-accounts";

    private static String linkBody(String game, String externalGameId, String region) {
        String regionField = region == null ? "null" : "\"" + region + "\"";
        return """
                { "game": "%s", "externalGameId": "%s", "region": %s }
                """.formatted(game, externalGameId, regionField);
    }

    @Test
    @DisplayName("TC-USER-01 내 프로필은 3필드뿐이다. 이메일을 흘리지 않는다")
    void profileCarriesThreeFieldsOnly() {
        UUID alpha = alpha();

        JsonNode profile = body(get(ME, alpha));

        assertEquals(alpha.toString(), profile.path("id").asText());
        assertEquals("alpha", profile.path("nickname").asText());
        assertTrue(profile.has("avatarUrl"));
        assertFalse(profile.has("email"), "마이페이지에 이메일이 필요하면 계약을 먼저 바꿔야 한다");
        assertEquals(3, profile.size());
    }

    @Test
    @DisplayName("TC-USER-02 토큰이 없으면 401이고 본문이 비어 있다")
    void anonymousIsUnauthorizedWithEmptyBody() {
        ResponseEntity<String> response = get(ME, null);

        // 필터 단계 401도 컨트롤러의 401과 같은 형태를 준다.
        // 클라이언트가 4xx를 한 가지 방법으로만 처리하면 되게 한다.
        assertError(response, 401, "UNAUTHORIZED");
    }

    @Test
    @DisplayName("TC-USER-03 깨진 토큰도 401이다")
    void garbageTokenIsUnauthorized() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("garbage.not.a.jwt");

        ResponseEntity<String> response = http.exchange(
                ME, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertStatus(response, 401);
    }

    @Test
    @DisplayName("TC-USER-04 닉네임만 바꾸면 아바타는 그대로다")
    void updatesNicknameOnly() {
        UUID alpha = alpha();

        JsonNode updated = body(patch(ME, alpha, """
                { "nickname": "알파2" }
                """));

        assertEquals("알파2", updated.path("nickname").asText());
        assertTrue(updated.path("avatarUrl").isNull());
    }

    @Test
    @DisplayName("TC-USER-05 닉네임이 1자면 400이다")
    void rejectsTooShortNickname() {
        UUID alpha = alpha();

        assertError(patch(ME, alpha, """
                { "nickname": "가" }
                """), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-USER-06 남이 쓰는 닉네임으로 바꾸면 409다")
    void rejectsTakenNickname() {
        UUID alpha = alpha();
        bravo();

        assertError(patch(ME, alpha, """
                { "nickname": "bravo" }
                """), 409, "NICKNAME_ALREADY_IN_USE");
    }

    @Test
    @DisplayName("TC-USER-07 avatarUrl에 null을 보내면 아바타가 지워진다")
    void explicitNullClearsAvatar() {
        UUID alpha = alpha();
        patch(ME, alpha, """
                { "avatarUrl": "https://cdn.queuemate.dev/avatars/alpha.png" }
                """);

        JsonNode cleared = body(patch(ME, alpha, """
                { "avatarUrl": null }
                """));

        assertTrue(cleared.path("avatarUrl").isNull(), "명시적 null은 삭제다");
    }

    @Test
    @DisplayName("TC-USER-07b avatarUrl 키를 아예 빼면 기존 값이 유지된다")
    void omittedAvatarIsUntouched() {
        UUID alpha = alpha();
        patch(ME, alpha, """
                { "avatarUrl": "https://cdn.queuemate.dev/avatars/alpha.png" }
                """);

        JsonNode kept = body(patch(ME, alpha, """
                { "nickname": "알파2" }
                """));

        // 키가 없는 것과 null인 것을 구분하지 못하면 여기서 아바타가 사라진다.
        assertEquals("https://cdn.queuemate.dev/avatars/alpha.png", kept.path("avatarUrl").asText());
        assertEquals("알파2", kept.path("nickname").asText());
    }

    @Test
    @DisplayName("TC-USER-07c nickname은 null로 비울 수 없다")
    void nicknameCannotBeCleared() {
        UUID alpha = alpha();

        assertError(patch(ME, alpha, """
                { "nickname": null }
                """), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-USER-08 연결된 계정이 없으면 빈 배열이다")
    void emptyGameAccountsIsEmptyArray() {
        UUID alpha = alpha();

        JsonNode accounts = body(get(GAME_ACCOUNTS, alpha));

        assertTrue(accounts.isArray());
        assertEquals(0, accounts.size());
    }

    @Test
    @DisplayName("TC-USER-09 계정을 연결하면 rankCode와 verifiedAt은 null로 시작한다")
    void linkedAccountStartsUnverified() {
        UUID alpha = alpha();

        ResponseEntity<String> response =
                post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR1", "KR"));

        assertStatus(response, 201);
        JsonNode account = body(response);
        assertEquals("LOL", account.path("game").asText());
        assertEquals("Alpha#KR1", account.path("externalGameId").asText());
        assertEquals("KR", account.path("region").asText());
        assertTrue(account.path("rankCode").isNull(), "rank는 서버가 나중에 채우는 파생 값이다");
        assertTrue(account.path("verifiedAt").isNull());
        // 201인데 Location이 없다. 클라이언트는 본문의 id를 읽어야 한다.
        assertNull(response.getHeaders().getFirst("Location"));
    }

    @Test
    @DisplayName("TC-USER-10 enum 밖의 게임도 {code, message} 형태로 400을 준다")
    void unknownGameEnumFollowsTheErrorContract() {
        UUID alpha = alpha();

        assertError(post(GAME_ACCOUNTS, alpha, linkBody("OVERWATCH", "Alpha#KR1", "KR")),
                400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-USER-11 externalGameId는 비거나 128자를 넘으면 400이다")
    void externalGameIdBoundaries() {
        UUID alpha = alpha();

        assertError(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "", "KR")),
                400, "VALIDATION_FAILED");
        assertError(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "a".repeat(129), "KR")),
                400, "VALIDATION_FAILED");
        assertStatus(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "a".repeat(128), "KR")), 201);
    }

    @Test
    @DisplayName("TC-USER-12 같은 (게임, 계정)을 두 번 연결하면 409다")
    void rejectsExactDuplicateLink() {
        UUID alpha = alpha();
        post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR1", "KR"));

        assertError(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR1", "KR")),
                409, "GAME_ACCOUNT_ALREADY_LINKED");
    }

    @Test
    @DisplayName("TC-USER-13 같은 게임에 다른 계정은 여러 개 연결된다")
    void allowsMultipleAccountsPerGame() {
        UUID alpha = alpha();
        post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR1", "KR"));

        // 유니크 제약이 (user_id, provider_game, external_game_id)라 게임당 1계정이 아니다.
        // "게임당 하나"를 원한다면 제약부터 바꿔야 한다.
        assertStatus(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR2", "KR")), 201);
        assertEquals(2, body(get(GAME_ACCOUNTS, alpha)).size());
    }

    @Test
    @DisplayName("TC-USER-14 연결을 해제하면 목록에서 사라진다")
    void unlinkRemovesAccount() {
        UUID alpha = alpha();
        String id = body(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR1", "KR")))
                .path("id").asText();

        assertStatus(delete(GAME_ACCOUNTS + "/" + id, alpha), 204);

        assertEquals(0, body(get(GAME_ACCOUNTS, alpha)).size());
    }

    @Test
    @DisplayName("TC-USER-15 남의 계정을 지우려 하면 403이 아니라 404다")
    void unlinkingOthersAccountIsNotFound() {
        UUID alpha = alpha();
        UUID bravo = bravo();
        String bravosAccount = body(post(GAME_ACCOUNTS, bravo, linkBody("LOL", "Bravo#KR1", "KR")))
                .path("id").asText();

        // 403이면 "그 id가 존재한다"는 사실이 샌다.
        assertError(delete(GAME_ACCOUNTS + "/" + bravosAccount, alpha),
                404, "GAME_ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-USER-16 같은 계정을 두 번 지우면 두 번째는 404다")
    void unlinkIsNotIdempotent() {
        UUID alpha = alpha();
        String id = body(post(GAME_ACCOUNTS, alpha, linkBody("LOL", "Alpha#KR1", "KR")))
                .path("id").asText();
        delete(GAME_ACCOUNTS + "/" + id, alpha);

        assertError(delete(GAME_ACCOUNTS + "/" + id, alpha), 404, "GAME_ACCOUNT_NOT_FOUND");
    }
}
