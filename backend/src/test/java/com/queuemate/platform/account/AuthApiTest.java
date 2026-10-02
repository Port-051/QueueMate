package com.queuemate.platform.account;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인시키는 쿠키 · access 토큰의 모양 · 로그아웃 — {@code contracts/platform-api.md} "계정" · "access 토큰".
 *
 * <p><b>직접 가입 · 비밀번호 로그인은 없다</b>(2026-09-26 소유자 결정) — 로그인은 소셜로만 하고 그 흐름은 {@code SocialLoginApiTest} 가 본다.
 * 여기는 쿠키를 찍는 공통 자리({@code SessionCookies})를 재발급으로 부른다. 응답의 {@code userId} 는 DB 가 매긴 <b>사용자 번호</b>다 —
 * 번호는 JSON 에서 숫자라 {@code value(equalTo(…), Long.class)} 로 본다(Jackson 이 int 로 읽어 {@code value(long)} 과는 맞지 않는다).
 */
class AuthApiTest extends ApiTestSupport {

    @Test
    @DisplayName("직접 가입 · 비밀번호 로그인의 경로는 없다")
    void noPasswordEndpoints() throws Exception
    {
        for(String path : List.of("/api/v1/auth/signup", "/api/v1/auth/login"))
        {
            MvcResult result = mockMvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nickname", newNickname())))
                    .andReturn();
            // 매핑이 없다 — 404 이거나(정적 자원 처리) 405 다. 어느 쪽이든 2xx 가 아니고 쿠키를 주지 않는다
            assertThat(result.getResponse().getStatus()).isIn(404, 405);
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        }
    }

    @Test
    @DisplayName("로그인시키는 쿠키는 계약대로다 — 재발급이 주는 access · refresh 의 속성과, 그 access 로 내 프로필을 읽는다")
    void sessionCookiesFollowTheContract() throws Exception
    {
        String nickname = newNickname();
        Cookie oldRefresh = refreshCookieFor(nickname);
        Long userId = userIdOf(nickname);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nickname))
                // 본문은 둘뿐이다 — 토큰은 본문에 싣지 않고 로그인 아이디는 없다
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        String setCookie = refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("qm_access=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/");
        // 수명은 토큰 수명과 같다 — 기본값 PT15M (2026-09-23 소유자 결정. refresh 가 이어 준다)
        assertThat(setCookie).contains("Max-Age=" + Duration.ofMinutes(15).toSeconds());
        // 로컬 기본값은 Secure 를 붙이지 않고(COOKIE_SECURE=false), Domain 은 어디서든 붙이지 않는다(host-only)
        assertThat(setCookie).doesNotContain("Secure").doesNotContain("Domain");
        // refresh 쿠키도 같이 온다 — Path 와 Max-Age 만 다르다
        String refreshCookie = refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("qm_refresh=")).findFirst().orElseThrow();
        // Path 는 인증 경로 전부다 — 재발급 · 로그아웃이 둘 다 받는다(2026-10-02 소유자 결정 — 그 전에는 /api/v1/auth/refresh)
        assertThat(refreshCookie).contains("HttpOnly").contains("SameSite=Lax")
                .contains("Path=/api/v1/auth;")
                .contains("Max-Age=" + Duration.ofDays(7).toSeconds());
        assertThat(refreshCookie).doesNotContain("Secure").doesNotContain("Domain");
        // 옛 Path 의 refresh 쿠키를 같이 지운다 — 같은 이름의 쿠키가 둘 남지 않게(임시 — 2026-10-02 · 옛 쿠키가 다 사라지면 걷어낸다)
        assertThat(refreshed.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anySatisfy(value -> assertThat(value).startsWith("qm_refresh=;").contains("Path=/api/v1/auth/refresh;").contains("Max-Age=0"));
        refreshCookieOf(refreshed);

        Cookie cookie = refreshed.getResponse().getCookie("qm_access");
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nickname))
                // 칸은 다섯이다 — 로그인 아이디와 비밀번호 유무가 없어졌다(2026-09-26)
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("access 토큰은 RS256 이고 kid 가 있으며 클레임은 계약의 여섯 개뿐이다")
    void accessTokenShape() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);

        SignedJWT jwt = SignedJWT.parse(cookie.getValue());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("dev-1");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        // 닉네임처럼 바뀌는 값이 섞여 들어오지 않았는지도 같이 본다
        assertThat(claims.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti", "token_use");
        assertThat(claims.getIssuer()).isEqualTo("queuemate-platform");
        // sub 는 사용자 번호를 문자열로 찍은 것이다
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(userIdOf(nickname)));
        assertThat(claims.getStringClaim("token_use")).isEqualTo("access");
        assertThat(UUID.fromString(claims.getJWTID())).isNotNull();
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("로그아웃은 쿠키 둘을 Max-Age=0 으로 지운다. 로그인하지 않았어도 204 다")
    void logout() throws Exception
    {
        Cookie cookie = login(newNickname());

        for(MvcResult result : List.of(
                mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andReturn(),
                mockMvc.perform(post("/api/v1/auth/logout")).andReturn()))
        {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
            String access = setCookies.stream().filter(value -> value.startsWith("qm_access=")).findFirst().orElseThrow();
            String refresh = setCookies.stream().filter(value -> value.startsWith("qm_refresh=")).findFirst().orElseThrow();
            assertThat(access).startsWith("qm_access=;");
            assertThat(refresh).startsWith("qm_refresh=;");
            // 발급 때와 속성이 같아야 브라우저가 같은 쿠키로 보고 지운다 — Path 가 특히 그렇다
            assertThat(access).contains("Max-Age=0").contains("Path=/").contains("HttpOnly").contains("SameSite=Lax");
            assertThat(refresh).contains("Max-Age=0").contains("Path=/api/v1/auth;")
                    .contains("HttpOnly").contains("SameSite=Lax");
            // 옛 Path(/api/v1/auth/refresh) 의 refresh 쿠키도 지운다(임시 — 2026-10-02)
            assertThat(setCookies).anySatisfy(value -> assertThat(value).startsWith("qm_refresh=;")
                    .contains("Path=/api/v1/auth/refresh;").contains("Max-Age=0"));
        }
    }
}
