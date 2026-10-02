package com.queuemate.matching.web;

import com.queuemate.common.security.TestJwt;
import com.queuemate.common.security.TokenClaims;
import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.redisKeys.SharedKeys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2026-09-27 — 임시 식별 {@code ?userId=} 를 쿠키 {@code qm_access}(RS256 JWT) 검증으로 바꾸고 {@code Origin} 검사를 넣은 것.
 * 테스트 키 쌍은 {@link TestJwt} 이고 공개 키는 모든 컨텍스트에 자동으로 들어간다.
 *
 * <p>Redis(DB 15)가 필요하다 — 매칭 요청이 실제로 접수되는지까지 본다({@link ConcurrencyTestSupport} 와 같은 준비).
 */
@AutoConfigureMockMvc
class AuthenticationApiTest extends ConcurrencyTestSupport {

    /** 검증기가 거절하지 않는 모드 — 티어 · 포지션을 보지 않는다 */
    private static final String MODE = "NORMAL_AUTH_TEST";

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedMode() {
        redis.opsForHash().putAll("qm:gameconfig:LOL:" + MODE, Map.of(
                "targetPartySize", "5",
                "positionUniqueness", "false",
                "tierRule", "NONE"));
    }

    private static String body(String userIdInBody) {
        return """
                {"userId":"%s","game":"LOL","modeKey":"%s",
                 "keyCondition":{"type":"POSITION","value":"NONE"},
                 "voicePreference":"REQUIRED","playPurpose":"FUN"}""".formatted(userIdInBody, MODE);
    }

    private ResultActions getStatus(Cookie cookie) throws Exception {
        return mockMvc.perform(get("/api/v1/match-requests").cookie(cookie));
    }

    private static void expectUnauthenticated(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ── 인증 ──────────────────────────────────────────────────────

    @Test
    void 쿠키가_없으면_401() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/api/v1/match-requests")));
        expectUnauthenticated(mockMvc.perform(post("/api/v1/proposals/p1/accept")));
    }

    @Test
    void 옛_userId_파라미터만으로는_401() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/api/v1/match-requests").param("userId", "42")));
    }

    @Test
    void Authorization_헤더는_받지_않는다() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/api/v1/match-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwt.accessToken("42"))));
    }

    @Test
    void 만료된_토큰은_401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String token = TestJwt.token(c -> c.subject("42").issuedAt(past.minusSeconds(900)).expiresAt(past));
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, token)));
    }

    @Test
    void 다른_키로_서명한_토큰은_401() throws Exception {
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, TestJwt.tokenSignedByOtherKey("42"))));
    }

    @Test
    void token_use_가_access_가_아니면_401() throws Exception {
        String token = TestJwt.token(c -> c.subject("42").claim(TokenClaims.TOKEN_USE, "social_signup"));
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, token)));
    }

    @Test
    void token_use_가_없으면_401() throws Exception {
        String token = TestJwt.token(c -> c.subject("42").claims(claims -> claims.remove(TokenClaims.TOKEN_USE)));
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, token)));
    }

    @Test
    void sub_가_숫자가_아니면_401() throws Exception {
        String token = TestJwt.token(c -> c.subject("u1"));
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, token)));
    }

    @Test
    void iss_가_다르면_401() throws Exception {
        String token = TestJwt.token(c -> c.subject("42").issuer("someone-else"));
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, token)));
    }

    @Test
    void 깨진_쿠키는_401() throws Exception {
        expectUnauthenticated(getStatus(new Cookie(TokenClaims.ACCESS_COOKIE, "not-a-jwt")));
    }

    @Test
    void 정상_토큰이면_상태를_조회한다() throws Exception {
        getStatus(TestJwt.cookie("42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
    }

    // ── 헬스 체크 — actuator 를 루트에 뒀다(2026-10-02 · platform · notification 과 같은 모양) ──

    @Test
    void health_live_는_인증_없이_UP_이고_세부를_싣지_않는다() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // show-details 는 기본값(never)이다 — 상태만 내보내고 기여자 목록(Redis 버전 · DB 종류)은 싣지 않는다
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    /**
     * ready 그룹은 readinessState · redis · db 를 본다 — 테스트에는 Redis(DB 15)와 H2 가 있으니 UP 이다.
     * 그룹에 없는 기여자 이름을 적으면 컨텍스트가 뜨지 않으므로(platform · notification 에서 확인한 것이다) 이 테스트가 뜬다는 것이
     * redis · db 가 그룹에 들어 있다는 확인이다.
     */
    @Test
    void health_ready_는_Redis_와_DB_가_있으면_UP() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void health_전체와_info_와_metrics_는_인증_없이_열린다() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/info")).andExpect(status().isOk());
        // load-test 가 /metrics/executor.queued 를 읽는다
        mockMvc.perform(get("/metrics")).andExpect(status().isOk());
    }

    @Test
    void health_는_깨진_쿠키가_있어도_401_이_아니다() throws Exception {
        mockMvc.perform(get("/health/live").cookie(new Cookie(TokenClaims.ACCESS_COOKIE, "not-a-jwt")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/info").cookie(new Cookie(TokenClaims.ACCESS_COOKIE, "not-a-jwt")))
                .andExpect(status().isOk());
    }

    /** 옛 주소는 없다 — 인증이 필요한 모르는 경로라 401 이다(쿠키 리졸버가 /api/v1/ 밖에서는 쿠키를 집지 않아 쿠키가 있어도 같다) */
    @Test
    void 옛_actuator_주소는_더_이상_없다() throws Exception {
        expectUnauthenticated(mockMvc.perform(get("/actuator/health")));
        expectUnauthenticated(mockMvc.perform(get("/actuator/health").cookie(TestJwt.cookie("42"))));
    }

    // ── "나"는 sub 다 ─────────────────────────────────────────────

    @Test
    void 매칭_요청은_토큰의_사용자로_접수되고_본문의_userId_는_무시한다() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("999")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        assertThat(redis.hasKey(SharedKeys.activeRequestKey("42"))).isTrue();
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("999"))).isFalse();
    }

    @Test
    void 조회에_남의_userId_를_붙여도_내_상태가_나온다() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/match-requests").param("userId", "42").cookie(TestJwt.cookie("7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));
        mockMvc.perform(get("/api/v1/match-requests").cookie(TestJwt.cookie("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void 남의_요청은_취소할_수_없고_내_요청은_취소된다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = response.replaceAll(".*\"requestId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete("/api/v1/match-requests/" + requestId)
                        .param("userId", "42").cookie(TestJwt.cookie("7")))
                .andExpect(status().isNotFound());
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("42"))).isTrue();

        mockMvc.perform(delete("/api/v1/match-requests/" + requestId).cookie(TestJwt.cookie("42")))
                .andExpect(status().isNoContent());
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("42"))).isFalse();
    }

    @Test
    void 제안_수락은_토큰으로_인증을_통과해_엔진까지_간다() throws Exception {
        mockMvc.perform(post("/api/v1/proposals/no-such-proposal/accept").cookie(TestJwt.cookie("42")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"));
    }

    // ── Origin ────────────────────────────────────────────────────

    @Test
    void 목록_밖의_Origin_인_POST_는_403() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("42"))).isFalse();
    }

    @Test
    void 목록_밖의_Origin_은_로그인하지_않아도_403() throws Exception {
        mockMvc.perform(delete("/api/v1/match-requests/r1").header(HttpHeaders.ORIGIN, "null"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    @Test
    void 허용된_Origin_의_POST_는_통과한다() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body("42")))
                .andExpect(status().isCreated());
    }

    @Test
    void GET_은_Origin_을_보지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isOk());
    }
}
