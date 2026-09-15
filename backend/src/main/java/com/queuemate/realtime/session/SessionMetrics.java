package com.queuemate.realtime.session;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 이 노드에 열려 있는 WebSocket session 수.
 *
 * 카운터가 아니라 게이지다. 늘고 주는 값이라 누적으로 세면 의미가 없다.
 * 레지스트리가 값을 읽어 가는 방식이라 여기서 주기적으로 올릴 필요가 없다.
 *
 * 노드마다 따로 잡힌다. 전체 접속자 수는 노드별 값을 합쳐야 나온다.
 * 한 사용자가 탭을 여러 개 열면 그만큼 세므로 사용자 수와 다르다.
 */
@Component
public class SessionMetrics {

    private final MeterRegistry registry;
    private final SessionRegistry sessions;

    public SessionMetrics(MeterRegistry registry, SessionRegistry sessions) {
        this.registry = registry;
        this.sessions = sessions;
    }

    @PostConstruct
    void register() {
        Gauge.builder("queuemate.ws.sessions", sessions, SessionRegistry::openSessionCount)
                .description("이 노드에 열려 있는 WebSocket session 수")
                .register(registry);
    }
}
