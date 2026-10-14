package com.queuemate.chat.web;

import com.queuemate.chat.security.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code Origin} 검사 — 상태를 바꾸는 요청만 본다. 아직 POST 경로가 없어서 테스트용 발판 {@code /api/v1/chat/ping} 에 POST 로 본다
 * (허용된 출처면 필터를 지나 405 가 나온다 — 403 이 아닌 것이 증명이다).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OriginCheckFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestTokens.register(registry);
    }

    @Test
    @DisplayName("허용 목록에 없는 Origin 의 POST 는 로그인과 상관없이 403 ORIGIN_NOT_ALLOWED")
    void 모르는_출처의_POST_는_403() throws Exception {
        mockMvc.perform(post("/api/v1/chat/ping").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("허용된 Origin 의 POST 는 필터를 지난다 — 이 경로에 POST 가 없어 403 이 아닌 405 다")
    void 허용된_출처의_POST_는_지난다() throws Exception {
        mockMvc.perform(post("/api/v1/chat/ping").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET 은 Origin 을 보지 않는다 — 소켓 핸드셰이크의 Origin 은 소켓 설정이 따로 본다")
    void GET_은_보지_않는다() throws Exception {
        mockMvc.perform(get("/api/v1/chat/ping").header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(TestTokens.accessCookie("42")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("42"));
    }
}
