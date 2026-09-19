package com.queuemate.notification.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 살아 있는 모든 SSE 연결에 주기적으로 하트비트 이벤트({@code event:heartbeat})를 보낸다.
 *
 * <p>목적은 셋이다. 프록시·로드밸런서가 유휴 연결을 끊지 않게 하고, 쓰기에 실패한 연결을 이때
 * 죽은 것으로 드러내 정리하고, <b>프런트가 조용히 죽은 연결을 알아챌 수 있게</b> 한다 — 주석 줄이
 * 아니라 이름 있는 이벤트로 보내는 이유다 ({@link SseConnections#broadcastHeartbeat()}).
 *
 * <p>주기는 {@code queuemate.sse.heartbeat-interval-ms} 이고 유휴 타임아웃(Stage 2 ALB 300초)보다
 * 짧아야 한다 (CLAUDE.md §5).
 */
@Component
@RequiredArgsConstructor
public class SseHeartbeat {

    private final SseConnections connections;

    @Scheduled(fixedDelayString = "${queuemate.sse.heartbeat-interval-ms}")
    public void beat() {
        connections.broadcastHeartbeat();
    }
}
