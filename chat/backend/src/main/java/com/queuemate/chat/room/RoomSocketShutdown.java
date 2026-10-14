package com.queuemate.chat.room;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;


@Component
@RequiredArgsConstructor
@Slf4j
public class RoomSocketShutdown implements SmartLifecycle {

    private static final int PHASE = WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE + 1;
    private volatile boolean running = false;   // 이 빈의 상태 — start/stop 을 부르는 스레드와 isRunning 을 읽는 스레드가 달라 volatile
    private final RoomSockets  roomSockets;

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        int closed = 0;
        for (var roomSocket : roomSockets.all()) {
            try {
                roomSocket.close(CloseStatus.GOING_AWAY);   // 1001 — "서버가 내려간다". 브라우저는 이 코드를 보고 잠시 뒤 재접속한다
                closed++;
            } catch (Exception e) {
                // 이미 끊긴 소켓 — 할 일 없음. 끊김 콜백이 장부에서 뺀다
            }
        }
        log.info("종료 전에 방 소켓 {}개를 닫았다", closed);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;   // 웹 서버의 종료 대기(GRACEFUL_SHUTDOWN_PHASE)보다 1 커서 그보다 먼저 stop() 이 불린다 — phase 가 큰 것부터 멈춘다
    }
}
