package com.queuemate.realtime.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * userId → 열려 있는 WebSocket session들.
 *
 * 한 사용자가 탭을 여러 개 열 수 있으므로 1:N이다. 하나만 두면 새 탭이 이전 탭의
 * 이벤트를 빼앗는다.
 *
 * 이 레지스트리는 프로세스 안에만 있다. 다른 인스턴스에 붙은 사용자는 여기 없다.
 * 노드 간 전달은 EventFanout이, 서버 전체의 접속 여부는 ClusterPresence가 맡는다.
 */
@Component
public class SessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        sessionsByUser
                .computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unregister(UUID userId, WebSocketSession session) {
        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(session);
            // 빈 Set을 남겨두면 접속했다 나간 사용자만큼 map이 계속 자란다.
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public Collection<WebSocketSession> sessionsOf(UUID userId) {
        return sessionsByUser.getOrDefault(userId, Set.of());
    }

    /**
     * 이 프로세스에 열린 session이 있는가. 서버 전체의 접속 여부가 아니다.
     * 그건 ClusterPresence가 답한다.
     */
    public boolean hasLocalSession(UUID userId) {
        return !sessionsOf(userId).isEmpty();
    }

    public int openSessionCount() {
        return sessionsByUser.values().stream().mapToInt(Set::size).sum();
    }
}
