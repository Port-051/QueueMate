package com.queuemate.notification.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 살아 있는 모든 SSE 연결에 주기적으로 주석 줄을 보낸다.
 *
 * <p>프록시·로드밸런서가 유휴 연결을 끊지 않게 하는 것이 목적이다. 주기는
 * {@code queuemate.sse.heartbeat-interval-ms} 이고 유휴 타임아웃(Stage 2 ALB 300초)보다
 * 짧아야 한다 (CLAUDE.md §5). 쓰기에 실패한 연결은 이때 죽은 것으로 드러나 정리된다.
 */
@Component
@RequiredArgsConstructor
public class SseHeartbeat {

    private final SseConnections connections;

    @Scheduled(fixedDelayString = "${queuemate.sse.heartbeat-interval-ms}")
    public void beat() {
        // TODO: connections.broadcastComment("heartbeat") 를 부른다
    }
}
