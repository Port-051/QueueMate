package com.queuemate.chat.room;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;

import java.io.IOException;

/**
 * 모든 방 소켓에 주기적으로 ping 제어 프레임을 보낸다 — ① ALB 유휴 타임아웃(300초)보다 짧게 데이터를 흘려 조용한 연결이 끊기지 않게
 * ② 조용히 죽은 소켓을 쓰기 실패로 드러낸다(실패하면 끊어서 끊김 콜백이 장부에서 뺀다). 브라우저는 ping 에 자동으로 pong 을 답하고 JS 에는 보이지 않는다 — 프런트 코드 없음.
 * notification 의 {@code sse/SseHeartbeat} 와 같은 자리다.
 *
 * <p>간격은 {@code queuemate.chat.ping-interval-ms}(기본 20초). {@code fixedRate} — 한 바퀴가 오래 걸려도 다음 시작 시각이 밀리지 않는다.
 * {@code @Scheduled} 의 값은 컴파일 상수나 자리표시 문자열이어야 해서 {@code ChatProperties} 빈이 아니라 yaml 자리표시로 읽는다(같은 키다).
 * 장부의 소켓은 포장({@code ConcurrentWebSocketSessionDecorator})이라 전송 줄 · Tomcat 스레드와 같은 소켓에 겹쳐 써도 안전하다.
 * 스케줄링은 {@code config/AsyncConfig} 의 {@code @EnableScheduling} 이 켠다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomSocketPing {

    private final RoomSockets roomSockets;

    @Scheduled(fixedRateString = "${queuemate.chat.ping-interval-ms}")
    public void ping()
    {
        roomSockets.all().forEach(roomSocket -> {
            try {
                roomSocket.sendMessage(new PingMessage());
            } catch (Exception e) {                                  // 끊긴 소켓 · 포장이 던지는 상태 예외 — 이 소켓만 끊고 나머지는 계속
                log.debug("ping 실패 — 이 소켓을 끊는다 sessionId={} {}: {}", roomSocket.getId(), e.getClass().getSimpleName(), e.getMessage());
                try {
                    roomSocket.close(CloseStatus.SESSION_NOT_RELIABLE);
                } catch (IOException ignore) {
                    // 이미 끊긴 소켓 — 할 일 없음
                }
            }
        });
    }
}
