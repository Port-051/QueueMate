package com.queuemate.chat.room;

import com.queuemate.chat.config.ChatSendProperties;
import com.queuemate.chat.config.RoomSendExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * 이 인스턴스에 붙은 방 소켓의 장부 — {@code roomId → (sessionId → 소켓)} — 와 그 소켓들에 쏘는 자리. 구독은 모른다(핸들러가 add 의 답을 보고 한다).
 *
 * <p><b>장부에는 포장된 소켓({@link ConcurrentWebSocketSessionDecorator})을 든다.</b> 한 소켓에 쓰는 스레드가 둘 이상이다 — 방 메시지는 전송 줄 스레드,
 * ACK · ERROR 는 Tomcat 요청 스레드, 뒤에 ping 은 스케줄러 스레드. {@code WebSocketSession.sendMessage} 는 동시 호출에 안전하지 않아 포장이 쓰기를 직렬화한다
 * (쓰는 중이면 버퍼에 넣고 돌아온다). 한 번 쓰기가 {@code time-limit-ms} 를 넘기거나 버퍼가 {@code buffer-size-limit} 를 넘으면 포장이 그 소켓을
 * {@code SESSION_NOT_RELIABLE}(4500) 로 끊는다 — 느린 브라우저가 줄을 오래 잡지 못한다. <b>그래서 소켓에 쓰는 코드는 전부 이 장부를 거친다</b>({@link #send} · {@link #sendTo}) —
 * 핸들러가 받은 원래 세션에 직접 쓰면 포장을 비켜 가 직렬화가 깨진다.
 *
 * <p>열쇠는 {@code session.getId()} 다 — 콜백({@code afterConnectionClosed})에는 포장이 아니라 원래 세션이 오는데, 둘의 {@code getId()} 는 같다.
 * "첫 소켓인가 · 마지막이었나" 는 {@code ConcurrentHashMap.compute} 안에서 정한다 — 그 roomId 에 대해 원자적이라 동시에 들어와도 "첫" 이 둘 나오지 않는다.
 * 람다 안에서는 Redis 를 부르지 않는다(버킷을 잠근 채 네트워크를 기다리게 된다 — 구독 호출은 호출한 쪽이 밖에서 한다). 2026-10-10 · 포장은 2026-10-11.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomSockets {

    private final Map<String, Map<String, WebSocketSession>> byRoom = new ConcurrentHashMap<>();
    private final RoomSendExecutors sendExecutors;
    private final ChatSendProperties sendProperties;

    /** 포장해서 넣는다. 그 방의 <b>첫</b> 소켓이면 {@code true}. */
    public boolean add(String roomId, WebSocketSession session)
    {
        WebSocketSession wrapped = new ConcurrentWebSocketSessionDecorator(session, sendProperties.timeLimitMs(), sendProperties.bufferSizeLimit());
        // 람다는 바깥 지역 변수에 다시 대입하지 못한다(effectively final) — 배열 한 칸의 "안" 을 바꾸는 것은 된다. compute 가 키를 잠근 채 돌려 경쟁은 없다
        boolean[] first = {false};
        byRoom.compute(roomId, (k, sessions) -> {
            if (sessions == null) {
                sessions = new ConcurrentHashMap<>();
                first[0] = true;
            }
            sessions.put(session.getId(), wrapped);
            return sessions;
        });
        return first[0];
    }

    /** 뺀다(원래 세션이든 포장이든 — id 로 찾는다). 그 방의 <b>마지막</b> 소켓이었으면 방 항목을 지우고 {@code true}. 없던 소켓이면 {@code false}. */
    public boolean remove(String roomId, WebSocketSession session) {
        boolean[] last = {false};
        byRoom.computeIfPresent(roomId, (k, sessions) -> {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                last[0] = true;
                return null;   // null 을 돌려주면 항목이 지워진다
            }
            return sessions;
        });
        return last[0];
    }

    /**
     * 그 방의 소켓 전부에 쏜다. Redis 리스너 스레드에서 바로 쓰지 않고 <b>그 방의 전송 줄</b>에 넘긴다(config/AsyncConfig — 같은 방은 같은 줄이라 순서가 지켜진다).
     * 한 소켓이 실패해도 그 소켓만 끊고 나머지는 받는다 — 끊김 콜백이 장부에서 뺀다.
     */
    public void send(String roomId, String message)
    {
        Map<String, WebSocketSession> sessions = byRoom.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage frame = new TextMessage(message);
        try {
            sendExecutors.forRoom(roomId).execute(() -> {
                for (WebSocketSession session : sessions.values()) {   // 동시 수정에 안전한 맵이라 돌면서 add/remove 가 일어나도 깨지지 않는다
                    try {
                        session.sendMessage(frame);
                    } catch (Exception e) {   // 이유가 무엇이든 이 소켓만 끊고 나머지는 계속 — 좁게 잡으면 예상 못 한 예외 하나가 나머지 소켓의 전송을 끊는다. Error 는 잡지 않는다
                        log.warn("보내기 실패 — 이 소켓을 끊는다 roomId={} sessionId={} {}: {}",
                                roomId, session.getId(), e.getClass().getSimpleName(), e.getMessage());   // 클래스 이름 — IOException 이면 네트워크, 그 밖이면 버그
                        close(session);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("전송 줄이 가득 차 메시지를 버린다 roomId={}", roomId);
        }
    }

    /**
     * 소켓 하나에만 쓴다 — ACK · ERROR 처럼 보낸 사람에게 돌아가는 것. 호출한 스레드(Tomcat)가 쓰되 포장이 전송 줄 스레드와의 겹침을 막는다.
     * 장부에 없는 소켓(이미 끊김)이면 조용히 버린다.
     */
    public void sendTo(String roomId, String sessionId, String message)
    {
        Map<String, WebSocketSession> sessions = byRoom.get(roomId);
        WebSocketSession session = sessions == null ? null : sessions.get(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(message));
        } catch (Exception e) {
            log.debug("응답 쓰기 실패 — 이 소켓을 끊는다 roomId={} sessionId={} {}", roomId, sessionId, e.getMessage());
            close(session);
        }
    }

    /** 이 인스턴스의 모든 소켓(방 상관없이 · 포장된 것) — ping · 종료(draining)처럼 전부를 훑는 일에. 그 순간의 사본이다. */
    public Set<WebSocketSession> all()
    {
        Set<WebSocketSession> snapshot = new HashSet<>();
        byRoom.forEach((roomId, sessions) -> snapshot.addAll(sessions.values()));
        return snapshot;
    }

    private static void close(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);   // Spring 이 "쓰기 실패 뒤 끊기" 에 쓰는 코드(4500). 끊김 콜백이 장부에서 뺀다
        } catch (IOException ignore) {
            // 이미 끊긴 소켓 — 할 일 없음
        }
    }
}
