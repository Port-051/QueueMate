package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §2 TC-AUTH. */
class AuthApiContractTest extends ApiContractTestSupport {

    private static String signupBody(String email, String password, String nickname) {
        return """
                { "email": "%s", "password": "%s", "nickname": "%s" }
                """.formatted(email, password, nickname);
    }

    private static String loginBody(String email, String password) {
        return """
                { "email": "%s", "password": "%s" }
                """.formatted(email, password);
    }

    private static String refreshBody(String token) {
        return """
                { "refreshToken": "%s" }
                """.formatted(token);
    }

    @Test
    @DisplayName("TC-AUTH-01 가입 응답은 프로필 3필드뿐이고 토큰을 주지 않는다")
    void signupReturnsProfileWithoutTokens() {
        ResponseEntity<String> response =
                post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));

        assertStatus(response, 201);
        JsonNode profile = body(response);
        assertTrue(profile.hasNonNull("id"));
        assertEquals("알파", profile.path("nickname").asText());
        assertTrue(profile.has("avatarUrl"), "avatarUrl은 null이라도 필드가 실려야 한다");
        assertTrue(profile.path("avatarUrl").isNull());
        // 가입 직후 바로 로그인시키려는 유혹을 막는다. 토큰은 login에서만 나온다.
        assertFalse(profile.has("accessToken"), "가입 응답에 토큰이 있으면 안 된다");
        assertFalse(profile.has("email"), "가입 응답에 이메일을 실지 않는다");
        assertEquals(3, profile.size(), "필드가 늘면 계약이 바뀐 것이다");
    }

    @Test
    @DisplayName("TC-AUTH-02 비밀번호가 8자 미만이면 400이고 어느 필드인지 알려 준다")
    void rejectsShortPassword() {
        ResponseEntity<String> response =
                post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", "short7c", "알파"));

        assertError(response, 400, "VALIDATION_FAILED");
        assertTrue(body(response).path("message").asText().startsWith("password"),
                "첫 필드 오류가 password여야 한다");
    }

    @Test
    @DisplayName("TC-AUTH-03 닉네임 길이 경계는 2자와 16자다")
    void nicknameBoundaries() {
        assertStatus(post("/api/v1/auth/signup", null,
                signupBody("one@queuemate.dev", PASSWORD, "가")), 400);
        assertStatus(post("/api/v1/auth/signup", null,
                signupBody("two@queuemate.dev", PASSWORD, "가".repeat(17))), 400);

        assertStatus(post("/api/v1/auth/signup", null,
                signupBody("three@queuemate.dev", PASSWORD, "가나")), 201);
        assertStatus(post("/api/v1/auth/signup", null,
                signupBody("four@queuemate.dev", PASSWORD, "가".repeat(16))), 201);
    }

    @Test
    @DisplayName("TC-AUTH-04 이메일 형식이 아니면 400이다")
    void rejectsMalformedEmail() {
        assertError(post("/api/v1/auth/signup", null,
                signupBody("not-an-email", PASSWORD, "알파")), 400, "VALIDATION_FAILED");
    }

    @Test
    @DisplayName("TC-AUTH-05 이메일이 겹치면 409 EMAIL_ALREADY_IN_USE")
    void rejectsDuplicateEmail() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));

        assertError(post("/api/v1/auth/signup", null,
                signupBody("alpha@queuemate.dev", PASSWORD, "다른닉")), 409, "EMAIL_ALREADY_IN_USE");
    }

    @Test
    @DisplayName("TC-AUTH-06 닉네임이 겹치면 409 NICKNAME_ALREADY_IN_USE")
    void rejectsDuplicateNickname() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));

        assertError(post("/api/v1/auth/signup", null,
                signupBody("other@queuemate.dev", PASSWORD, "알파")), 409, "NICKNAME_ALREADY_IN_USE");
    }

    @Test
    @DisplayName("TC-AUTH-07 로그인은 Bearer 토큰 쌍과 남은 초를 준다")
    void loginIssuesTokenPair() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));

        ResponseEntity<String> response =
                post("/api/v1/auth/login", null, loginBody("alpha@queuemate.dev", PASSWORD));

        assertStatus(response, 200);
        JsonNode token = body(response);
        assertEquals("Bearer", token.path("tokenType").asText());
        assertTrue(token.path("expiresIn").asLong() > 0, "expiresIn은 초 단위 양수다");
        assertFalse(token.path("accessToken").asText().isBlank());
        assertFalse(token.path("refreshToken").asText().isBlank());
    }

    @Test
    @DisplayName("TC-AUTH-08 없는 이메일과 틀린 비밀번호가 똑같은 401이다")
    void wrongPasswordAndUnknownEmailAreIndistinguishable() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));

        ResponseEntity<String> wrongPassword =
                post("/api/v1/auth/login", null, loginBody("alpha@queuemate.dev", "Wrong!passw0rd"));
        ResponseEntity<String> unknownEmail =
                post("/api/v1/auth/login", null, loginBody("nobody@queuemate.dev", PASSWORD));

        assertError(wrongPassword, 401, "UNAUTHORIZED");
        assertError(unknownEmail, 401, "UNAUTHORIZED");
        // 계정 존재 여부가 새면 안 된다. 친절하게 문구를 나누는 순간 이 단언이 깨진다.
        assertEquals(body(wrongPassword).path("message").asText(),
                body(unknownEmail).path("message").asText(),
                "두 실패의 메시지가 달라지면 계정 존재 여부가 샌다");
    }

    @Test
    @DisplayName("TC-AUTH-09 refresh는 refreshToken까지 새 값으로 바꾼다")
    void refreshRotatesBothTokens() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));
        JsonNode issued = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD)));
        String firstRefresh = issued.path("refreshToken").asText();

        ResponseEntity<String> response =
                post("/api/v1/auth/refresh", null, refreshBody(firstRefresh));

        assertStatus(response, 200);
        assertNotEquals(firstRefresh, body(response).path("refreshToken").asText(),
                "rotation이 없으면 탈취된 토큰이 계속 살아 있다");
    }

    @Test
    @DisplayName("TC-AUTH-10 쓴 refresh를 재사용하면 그 사용자의 토큰이 전부 죽는다")
    void reusedRefreshTokenRevokesEverything() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));
        JsonNode issued = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD)));
        String first = issued.path("refreshToken").asText();
        String second = body(post("/api/v1/auth/refresh", null, refreshBody(first)))
                .path("refreshToken").asText();

        assertStatus(post("/api/v1/auth/refresh", null, refreshBody(first)), 401);
        // 재사용 감지는 그 토큰 하나가 아니라 사용자 전체를 끊는다.
        assertStatus(post("/api/v1/auth/refresh", null, refreshBody(second)), 401);
    }

    @Test
    @DisplayName("TC-AUTH-11 access token을 refresh 자리에 넣으면 401이다")
    void accessTokenIsNotARefreshToken() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));
        String access = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD))).path("accessToken").asText();

        assertStatus(post("/api/v1/auth/refresh", null, refreshBody(access)), 401);
    }

    @Test
    @DisplayName("TC-AUTH-12 로그아웃은 넘긴 refresh 하나만 끊는다")
    void logoutDropsOnlyThatSession() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));
        String phone = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD))).path("refreshToken").asText();
        String desktop = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD))).path("refreshToken").asText();

        assertStatus(post("/api/v1/auth/logout", null, refreshBody(phone)), 204);

        assertStatus(post("/api/v1/auth/refresh", null, refreshBody(desktop)), 200);
    }

    @Test
    @DisplayName("TC-AUTH-13 형식이 깨진 refresh로 로그아웃하면 401이다")
    void logoutRejectsMalformedRefreshToken() {
        UUID alpha = alpha();

        // refresh token은 불투명 문자열이 아니라 JWT다. logout이 이걸 파싱해서
        // 주체를 꺼내므로, 아무 문자열이나 보내면 204가 아니라 401이 난다.
        // "로그아웃은 항상 성공한다"고 가정한 클라이언트는 여기서 걸린다.
        assertError(post("/api/v1/auth/logout", alpha,
                refreshBody("00000000-0000-0000-0000-000000000000.deadbeef")), 401, "UNAUTHORIZED");
    }

    @Test
    @DisplayName("TC-AUTH-13b 같은 refresh로 두 번 로그아웃하면 어떻게 되는지 고정한다")
    void logoutTwiceWithSameToken() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));
        String refresh = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD))).path("refreshToken").asText();
        assertStatus(post("/api/v1/auth/logout", null, refreshBody(refresh)), 204);

        // 서명은 여전히 유효하므로 파싱은 통과한다. 저장소에 없을 뿐이다.
        assertStatus(post("/api/v1/auth/logout", null, refreshBody(refresh)), 204);
    }

    @Test
    @DisplayName("TC-AUTH-14 access token이 만료돼도 로그아웃할 수 있다")
    void logoutDoesNotRequireAccessToken() {
        post("/api/v1/auth/signup", null, signupBody("alpha@queuemate.dev", PASSWORD, "알파"));
        String refresh = body(post("/api/v1/auth/login", null,
                loginBody("alpha@queuemate.dev", PASSWORD))).path("refreshToken").asText();

        // refresh token 자체가 자격 증명이다. access token을 못 쓰는 상황에서
        // 로그아웃까지 막히면 사용자가 세션을 끊을 방법이 없다.
        assertStatus(post("/api/v1/auth/logout", null, refreshBody(refresh)), 204);
        assertStatus(post("/api/v1/auth/refresh", null, refreshBody(refresh)), 401);
    }
}
