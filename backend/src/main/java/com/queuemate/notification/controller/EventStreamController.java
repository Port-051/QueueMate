package com.queuemate.notification.controller;

import com.queuemate.notification.sse.SseConnections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 브라우저가 SSE 연결을 여는 입구. {@code GET /api/v1/events?userId=}
 *
 * <p>핸들러가 {@code SseEmitter} 를 반환하는 순간 톰캣 요청 스레드는 풀로 돌아간다
 * (서블릿 비동기). 유휴 연결이 스레드를 잡지 않으므로 동시 연결 상한은
 * {@code server.tomcat.max-connections} 다 (CLAUDE.md §5).
 *
 * <p>인증 방식(쿼리 파라미터 토큰 / 쿠키)은 아직 미정이다 (CLAUDE.md §7). 정해지기 전까지
 * {@code userId} 를 쿼리 파라미터로 그대로 받는다.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventStreamController {

    private final SseConnections connections;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String userId) {
        return new SseEmitter();
    }
}
