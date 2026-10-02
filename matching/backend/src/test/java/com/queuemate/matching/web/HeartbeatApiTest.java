package com.queuemate.matching.web;

import com.queuemate.common.security.TestJwt;
import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.redisKeys.SharedKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/match-requests/heartbeat} 의 응답 코드 (docs/11 D-43). 시한이 실제로 밀리는지 ·
 * 스위퍼가 빼는지는 {@code alive/RequestAliveTest} 가 본다 — 여기서는 컨트롤러가 서비스의 답을 어떻게 내보내는지만.
 *
 * <p>인증 · Origin 검사는 {@link AuthenticationApiTest} 와 같은 필터를 타므로 다시 보지 않는다.
 * 같은 준비({@link ConcurrencyTestSupport} + MockMvc)라 Spring 컨텍스트를 그쪽과 나눠 쓴다.
 */
@AutoConfigureMockMvc
class HeartbeatApiTest extends ConcurrencyTestSupport {

    /** 검증기가 거절하지 않는 모드 — 티어 · 포지션을 보지 않는다 */
    private static final String MODE = "NORMAL_HEARTBEAT_TEST";

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

    private static String body() {
        return """
                {"game":"LOL","modeKey":"%s",
                 "keyCondition":{"type":"POSITION","value":"NONE"},
                 "voicePreference":"REQUIRED","playPurpose":"FUN"}""".formatted(MODE);
    }

    @Test
    void 대기_중이면_204_이고_시한이_밀린다() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated());
        Double first = redis.opsForZSet().score(SharedKeys.HEARTBEAT_KEY, "42");
        assertThat(first).isNotNull();

        Thread.sleep(50);
        mockMvc.perform(post("/api/v1/match-requests/heartbeat").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isNoContent());

        assertThat(redis.opsForZSet().score(SharedKeys.HEARTBEAT_KEY, "42")).isGreaterThan(first);
    }

    @Test
    void 활성_요청이_없으면_404_MATCH_REQUEST_NOT_FOUND() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests/heartbeat").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"));

        assertThat(redis.opsForZSet().score(SharedKeys.HEARTBEAT_KEY, "42")).isNull();
    }
}
