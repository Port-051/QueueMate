package com.queuemate.notification.web;

import com.queuemate.notification.security.TestTokens;
import com.queuemate.notification.sse.SseConnections;
import com.queuemate.notification.subscription.UserChannelSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code Origin} 검사 — 상태를 바꾸는 요청만 본다. 지금 이 서비스에는 그런 요청이 없어서 없는 경로({@code POST /api/v1/events})로 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OriginCheckFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SseConnections connections;
    @MockitoBean
    private UserChannelSubscriber subscriber;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestTokens.register(registry);
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void 연결을_비운다() {
        ((Map<String, ?>) ReflectionTestUtils.getField(connections, "connections")).clear();
    }

    @Test
    @DisplayName("허용 목록에 없는 Origin 의 POST 는 로그인과 상관없이 403 ORIGIN_NOT_ALLOWED")
    void 모르는_출처의_POST_는_403() throws Exception {
        mockMvc.perform(post("/api/v1/events").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("허용된 Origin 의 POST 는 필터를 지난다 — 이 경로에 POST 가 없어 403 이 아닌 다른 응답이다")
    void 허용된_출처의_POST_는_지난다() throws Exception {
        mockMvc.perform(post("/api/v1/events").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET 은 Origin 을 보지 않는다 — SSE 연결은 열린다")
    void GET_은_보지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/events").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(TestTokens.accessCookie("42")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk());
    }
}
