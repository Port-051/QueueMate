package com.queuemate.notification.controller;

import com.queuemate.notification.sse.ReconnectDelay;
import com.queuemate.notification.sse.SseConnections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 브라우저가 SSE 연결을 여는 입구. {@code GET /api/v1/events}
 *
 * <p>핸들러가 {@code SseEmitter} 를 반환하는 순간 톰캣 요청 스레드는 풀로 돌아간다
 * (서블릿 비동기). 유휴 연결이 스레드를 잡지 않으므로 동시 연결 상한은
 * {@code server.tomcat.max-connections} 다 (CLAUDE.md §5).
 *
 * <p><b>"나"는 쿠키 {@code qm_access} 의 access 토큰이다</b>(2026-09-27 — 그 전에는 {@code ?userId=} 를 그대로 받았다).
 * 보안 필터가 서명 · {@code iss} · {@code exp} · {@code token_use} · {@code sub} 를 검증한 뒤에만 여기 닿고({@code security/JwtConfig}),
 * 토큰의 {@code sub}(사용자 번호의 십진 문자열 — {@code "42"})가 곧 채널 {@code qm:pubsub:push:{userId}} 의 {@code userId} 다.
 * {@code ?userId=} 를 줘도 보지 않는다. 검증은 연결할 때 한 번뿐이다 — 열린 연결은 토큰이 만료돼도 끊지 않는다 (CLAUDE.md §7.2 "인증").
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventStreamController {

    private final SseConnections connections;
    private final ReconnectDelay reconnectDelay;
    private final long timeoutMs;

    public EventStreamController(SseConnections connections,
                                 ReconnectDelay reconnectDelay,
                                 @Value("${queuemate.sse.timeout-ms}") long timeoutMs) {
        this.connections = connections;
        this.reconnectDelay = reconnectDelay;
        this.timeoutMs = timeoutMs;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) throws IOException {
        String userId = jwt.getSubject();
        SseEmitter emitter = new SseEmitter(timeoutMs);
        // retry: 는 여기서 한 번만 내려 준다. 브라우저가 기억한다 (ReconnectDelay 참고)
        emitter.send(SseEmitter.event()
                .comment("connected")
                .reconnectTime(reconnectDelay.nextMs()));
        connections.add(userId, emitter);
        return emitter;
    }
}
