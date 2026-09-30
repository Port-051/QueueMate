package com.queuemate.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SseShutdownTest {

    @Test
    @DisplayName("웹 서버의 종료 대기보다 먼저 멈춘다 - phase 가 더 크다 (작거나 같으면 종료가 30초 늘어진다)")
    void 웹서버_종료_대기보다_먼저_멈춘다() {
        SseShutdown shutdown = new SseShutdown(mock(SseConnections.class));

        // SmartLifecycle 은 phase 가 큰 것부터 stop 한다
        assertThat(shutdown.getPhase()).isGreaterThan(WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE);
    }

    @Test
    @DisplayName("stop 하면 열린 연결을 전부 닫고 running 이 false 가 된다")
    void 멈출_때_연결을_전부_닫는다() {
        SseConnections connections = mock(SseConnections.class);
        when(connections.closeAll()).thenReturn(3);
        SseShutdown shutdown = new SseShutdown(connections);
        shutdown.start();
        assertThat(shutdown.isRunning()).isTrue();

        shutdown.stop();

        verify(connections).closeAll();
        assertThat(shutdown.isRunning()).isFalse();
    }

    @Test
    @DisplayName("자동으로 시작된다 - 시작되지 않은 lifecycle 은 stop 도 불리지 않는다")
    void 자동으로_시작된다() {
        assertThat(new SseShutdown(mock(SseConnections.class)).isAutoStartup()).isTrue();
    }
}
