package com.queuemate.matching.web;

import com.jayway.jsonpath.JsonPath;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 플레이 목적의 값 이름 (2026-09-29, docs/11 D-49) — 옛 {@code NORMAL}(일반 플레이)이 {@code TRYHARD}(빡겜)가 됐다.
 *
 * <p>목적은 enum 의 {@code name()} 이 <b>그대로</b> 활성 요청 HASH 의 {@code playPurpose} 필드와 대기열 색인 키
 * ({@code qm:party:open:…:{purpose}:needs:…})에 들어가고, 취소가 그 필드를 {@code valueOf} 로 되읽어 같은 키를 다시 조립한다.
 * 그래서 새 이름이 접수 → 색인 → 취소를 한 바퀴 도는지와 옛 이름이 요청 본문에서 거절되는지를 본다.
 *
 * <p>인증 · Origin 검사는 {@link AuthenticationApiTest} 가 본다. 같은 준비라 Spring 컨텍스트를 나눠 쓴다.
 */
@AutoConfigureMockMvc
class PlayPurposeApiTest extends ConcurrencyTestSupport {

    /** 검증기가 거절하지 않는 모드 — 티어 · 포지션을 보지 않는다 */
    private static final String MODE = "PURPOSE_TEST_MODE";   // 모드 이름에 NORMAL 을 쓰지 않는다 — 목적의 옛 이름과 헷갈린다

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    /** 새 이름이 들어간 이 모드의 색인 키. 티어를 안 보는 모드라 접미사가 없다 */
    private static final String TRYHARD_INDEX_PATTERN = "qm:party:open:LOL:" + MODE + ":REQUIRED:TRYHARD:needs:*";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedMode() {
        redis.opsForHash().putAll("qm:gameconfig:LOL:" + MODE, Map.of(
                "targetPartySize", "5",
                "positionUniqueness", "false",
                "tierRule", "NONE"));
    }

    private static String body(String purpose) {
        return """
                {"game":"LOL","modeKey":"%s",
                 "keyCondition":{"type":"POSITION","value":"NONE"},
                 "voicePreference":"REQUIRED","playPurpose":"%s"}""".formatted(MODE, purpose);
    }

    @Test
    void 빡겜_TRYHARD_는_접수되고_색인_키에_이름_그대로_들어가며_취소도_된다() throws Exception {
        String response = mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body("TRYHARD")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(response, "$.requestId");

        assertThat(redis.opsForHash().get(SharedKeys.activeRequestKey("42"), "playPurpose")).isEqualTo("TRYHARD");

        // 배정은 비동기다 — 파티가 색인에 오를 때까지 기다린다
        Set<String> indexes = Set.of();
        for (int i = 0; i < 100 && indexes.isEmpty(); i++) {
            Thread.sleep(50);
            indexes = redis.keys(TRYHARD_INDEX_PATTERN);
        }
        assertThat(indexes).as("새 이름이 색인 키의 목적 자리에 들어간다").isNotEmpty();

        // 취소는 저장된 필드를 PlayPurpose.valueOf 로 되읽어 같은 색인 키를 조립한다
        mockMvc.perform(delete("/api/v1/match-requests/" + requestId).cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isNoContent());

        assertThat(redis.hasKey(SharedKeys.activeRequestKey("42"))).isFalse();
        assertThat(redis.keys(TRYHARD_INDEX_PATTERN)).as("혼자 있던 파티가 색인에서 빠진다").isEmpty();
    }

    @Test
    void 옛_이름_NORMAL_은_400_이고_접수되지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/match-requests").cookie(TestJwt.cookie("42"))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body("NORMAL")))
                .andExpect(status().isBadRequest());

        assertThat(redis.hasKey(SharedKeys.activeRequestKey("42"))).isFalse();
    }
}
