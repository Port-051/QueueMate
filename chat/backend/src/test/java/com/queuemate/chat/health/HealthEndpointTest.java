package com.queuemate.chat.health;

import com.queuemate.chat.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 헬스 체크 경로 — {@code /health/live}(ALB 가 본다)만 본다. {@code /health/ready} · {@code /health} 는 Redis 를 보므로 Redis 가 없는 자리에서는
 * DOWN(503)이 맞는 답이라 여기서 단정하지 않는다({@code notification} 의 같은 이름 테스트는 Redis 를 전제했다).
 * 모양은 {@code platform} · {@code notification} 과 같고 {@code ready} 그룹이 {@code redis} 를 본다(application.yaml 의 {@code management}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestTokens.register(registry);
    }

    @Test
    @DisplayName("GET /health/live → 200 {\"status\":\"UP\"} — 인증 없이 · Redis 없이")
    void live_는_UP() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // show-details 는 기본값이다 — 상태만 내보내고 기여자 목록은 싣지 않는다
                .andExpect(jsonPath("$.components").doesNotExist());
    }
}
