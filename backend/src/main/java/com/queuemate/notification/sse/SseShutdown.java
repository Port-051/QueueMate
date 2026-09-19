package com.queuemate.notification.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 앱이 꺼질 때 열려 있는 SSE 연결을 먼저 닫는다.
 *
 * <p><b>없으면 종료가 30초씩 늘어진다.</b> Spring Boot 의 우아한 종료는 진행 중인 요청이 끝나길
 * 기다리는데, SSE 는 끝나지 않는 요청이다. 그래서 제한 시간
 * ({@code spring.lifecycle.timeout-per-shutdown-phase}, 기본 30초)을 꽉 채운 뒤에야 포기하고 끊는다.
 * 2026-09-19 에 잰 값: 열린 연결 0개면 0.2초, 2개면 32.2초. 그 30초 동안 사용자는 곧 죽을 서버에
 * 붙어 있고 배포는 그만큼 늦어진다.
 *
 * <p>여기서 닫아 주면 브라우저 {@code EventSource} 가 {@code retry:} 만큼 기다린 뒤 다시 붙는다.
 * 놓친 알림은 재연결 직후 상태 조회로 복구한다 (contracts/events.md "재연결").
 *
 * <p><b>순서가 전부다.</b> 웹 서버의 종료 대기({@link WebServerApplicationContext#GRACEFUL_SHUTDOWN_PHASE})보다
 * <b>먼저</b> 돌아야 한다. {@code SmartLifecycle} 은 phase 가 큰 것부터 멈추므로 그보다 큰 값을 준다.
 * {@code @PreDestroy} 로는 안 된다 — 빈 소멸은 lifecycle 정지가 다 끝난 뒤라 이미 30초를 기다린 다음이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseShutdown implements SmartLifecycle {

    /** 웹 서버의 종료 대기보다 먼저 멈추도록 그보다 큰 값 */
    static final int PHASE = WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE + 1;

    private final SseConnections connections;

    private volatile boolean running;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        int closed = connections.closeAll();
        log.info("종료 전에 SSE 연결 {}개를 닫았다", closed);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
