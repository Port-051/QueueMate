package com.queuemate.notification.controller;

import com.queuemate.notification.security.TestTokens;
import com.queuemate.notification.sse.SseConnections;
import com.queuemate.notification.subscription.UserChannelSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE 입구를 웹 계층에서 본다. <b>Redis 없이 돈다</b> — {@link UserChannelSubscriber} 를 mock 으로
 * 바꿔 구독 호출이 Redis 까지 가지 않게 막는다.
 *
 * <p>연결은 쿠키 {@code qm_access}(테스트 키로 서명한 access 토큰 — {@link TestTokens})로 연다. {@code sub} 가 곧 {@code userId} 다.
 * 인증이 거절되는 경우는 {@code security/AuthenticationTest} 가 본다.
 *
 * <p>MockMvc 에서는 {@code SseEmitter.send} 가 <b>호출한 스레드에서 동기로</b> mock 응답에 쓴다.
 * 그래서 {@code connections.send} 가 리턴하면 응답 본문에 이미 들어 있다 — 기다림({@code sleep})이
 * 필요 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SseConnections connections;
    @MockitoBean
    private UserChannelSubscriber subscriber;
    @Value("${queuemate.sse.reconnect-delay-min-ms}")
    private long reconnectDelayMinMs;
    @Value("${queuemate.sse.reconnect-delay-max-ms}")
    private long reconnectDelayMaxMs;

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        TestTokens.register(registry);
    }

    /**
     * MockMvc 요청은 끊기지 않으므로 연결이 맵에 남는다. 다른 테스트(같은 컨텍스트를 나눠 쓰는
     * 테스트 포함)에 영향을 주지 않게 매번 비운다.
     */
    @AfterEach
    void 연결을_비운다() {
        연결_맵().clear();
    }

    @Test
    @DisplayName("GET /api/v1/events + 쿠키(sub=42) → 200, 비동기 시작, text/event-stream")
    void 연결하면_SSE_스트림이_열린다() throws Exception {
        mockMvc.perform(get("/api/v1/events").cookie(TestTokens.accessCookie("42")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        // 첫 연결이므로 그 사용자 채널 구독이 걸린다 — 토큰의 sub 가 채널의 userId 다
        verify(subscriber).subscribe("42");
    }

    @Test
    @DisplayName("연결 후 send 하면 응답 본문에 data: 와 id: 가 나타난다")
    void 연결_후_보낸_알림이_응답_본문에_실린다() throws Exception {
        String json = "{\"type\":\"MATCH_CONFIRMED\",\"eventId\":\"e-42\",\"occurredAt\":\"2026-01-01T00:00:00.000Z\",\"payload\":{\"nickname\":\"큐메이트\"}}";
        MvcResult result = mockMvc.perform(get("/api/v1/events").cookie(TestTokens.accessCookie("42")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        connections.send("42", json);
        connections.send("43", "{\"eventId\":\"not-for-42\"}");

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith(":connected\n");
        assertThat(body).contains("data:" + json + "\n");
        assertThat(body).contains("id:e-42\n");
        assertThat(body).doesNotContain("not-for-42");
    }

    @Test
    @DisplayName("연결 직후 :connected 다음 줄이 retry:<숫자> 이고 값이 설정 범위 안이다")
    void 연결_직후_retry_를_설정_범위_안에서_내려_준다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events").cookie(TestTokens.accessCookie("42")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 값은 연결마다 무작위다. 모양과 범위만 본다
        Matcher matcher = Pattern.compile("\\A:connected\nretry:(\\d+)\n\n\\z").matcher(body);
        assertThat(matcher.matches()).as("연결 직후 본문: %s", body).isTrue();
        assertThat(Long.parseLong(matcher.group(1))).isBetween(reconnectDelayMinMs, reconnectDelayMaxMs);
    }

    @Test
    @DisplayName("retry: 줄은 연결 직후 한 번뿐이고 이후 send 로 나간 부분에는 없다")
    void retry_는_연결할_때_한_번만_나간다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events").cookie(TestTokens.accessCookie("42")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        String onConnect = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        connections.send("42", "{\"type\":\"MATCH_CONFIRMED\",\"eventId\":\"e-1\",\"payload\":{}}");
        connections.send("42", "{\"type\":\"MATCH_CANCELLED\",\"eventId\":\"e-2\",\"payload\":{}}");

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(retry_줄_수(onConnect)).isEqualTo(1);
        assertThat(body).startsWith(onConnect);
        String afterConnect = body.substring(onConnect.length());
        assertThat(afterConnect).contains("id:e-1\n").contains("id:e-2\n");
        assertThat(retry_줄_수(afterConnect)).isZero();
        assertThat(retry_줄_수(body)).isEqualTo(1);
    }

    @Test
    @DisplayName("연결이 완료되면 콜백이 연결을 빼고 마지막 연결이므로 구독도 푼다")
    void 연결이_완료되면_목록에서_빠지고_구독이_풀린다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events").cookie(TestTokens.accessCookie("42")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        SseEmitter emitter = 연결_하나("42");
        verify(subscriber, never()).unsubscribe("42");

        // 완료 → 비동기 디스패치 → MockMvc 가 AsyncContext 를 complete 하며 onCompletion 콜백이 돈다.
        // 전부 이 테스트 스레드에서 동기로 일어난다
        emitter.complete();
        mockMvc.perform(asyncDispatch(result));

        verify(subscriber, times(1)).unsubscribe("42");
        assertThat(연결_맵()).doesNotContainKey("42");
    }

    private static long retry_줄_수(String wire) {
        return wire.lines().filter(line -> line.startsWith("retry:")).count();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<SseEmitter>> 연결_맵() {
        Map<String, Set<SseEmitter>> map =
                (Map<String, Set<SseEmitter>>) ReflectionTestUtils.getField(connections, "connections");
        assertThat(map).isNotNull();
        return map;
    }

    private SseEmitter 연결_하나(String userId) {
        Set<SseEmitter> emitters = 연결_맵().get(userId);
        assertThat(emitters).hasSize(1);
        return emitters.iterator().next();
    }
}
