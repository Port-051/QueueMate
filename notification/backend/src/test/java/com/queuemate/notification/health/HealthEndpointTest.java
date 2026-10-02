package com.queuemate.notification.health;

import com.queuemate.notification.security.TestTokens;
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
 * 헬스 체크 경로 — {@code /health/live}(ALB 가 본다) · {@code /health/ready}(Redis 까지 본다) · {@code /health}. 셋 다 인증 없이 열린다.
 * 모양은 {@code platform} 과 같고 {@code ready} 그룹이 {@code db} 대신 {@code redis} 를 본다(application.yaml 의 {@code management}).
 *
 * <p><b>Redis 가 있어야 돈다</b> — 다른 {@code @SpringBootTest} 와 같다. 구독 컨테이너가 기동 때 Redis 에 붙어서 Redis 가 없으면 컨텍스트가 뜨지 않는다
 * (2026-10-02 빈 포트로 돌려 확인 — {@code Failed to start bean 'redisMessageListenerContainer'}). 그래서 "Redis 가 죽으면 ready 가 DOWN" 은 이 테스트로 보지 못한다.
 * 포트는 {@code REDIS_PORT}로 넘긴다(공용 테스트 Redis {@code qm-platform-test-redis} 는 6380 — 6379 는 다른 프로젝트 것이라 쓰지 않는다).
 * 그룹에 없는 기여자 이름을 적으면 컨텍스트가 뜨지 않는다 — {@code redisx} 로 바꿔 돌려 {@code Health contributor 'redisx' … does not exist} 를 확인했다(2026-10-02).
 * 그러니 이 테스트가 뜬다는 것이 {@code ready} 그룹에 {@code redis} 가 들어 있다는 확인이다.
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
    @DisplayName("GET /health/live → 200 {\"status\":\"UP\"} — 인증 없이")
    void live_는_UP() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // show-details 는 기본값이다 — 상태만 내보내고 기여자 목록은 싣지 않는다
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("GET /health/ready → 200 UP — readinessState 와 Redis 를 본다(Redis 가 떠 있을 때)")
    void ready_는_Redis_가_있으면_UP() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /health → 200 UP — 인증 없이")
    void health_전체도_인증_없이_열린다() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
