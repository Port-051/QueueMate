package com.queuemate.realtime.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

/**
 * 이 프로세스에 붙어 있는 session에만 쓴다.
 *
 * 이벤트를 만드는 쪽과 노드 간 전달을 맡는 쪽이 둘 다 이걸 쓴다. 하나로 모아 두지 않으면
 * session에 쓰는 코드가 두 벌이 되고, 동시 전송 보호 같은 것을 한쪽만 지키게 된다.
 */
@Component
public class SessionMessageSender {

    private static final Logger log = LoggerFactory.getLogger(SessionMessageSender.class);

    private final SessionRegistry sessions;

    public SessionMessageSender(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    /** @return 실제로 보낸 session 수. 대상이 이 노드에 없으면 0이다. */
    public int deliver(Collection<UUID> userIds, String payload) {
        int delivered = 0;
        for (UUID userId : userIds) {
            for (WebSocketSession session : sessions.sessionsOf(userId)) {
                delivered += send(session, payload) ? 1 : 0;
            }
        }
        return delivered;
    }

    public boolean send(WebSocketSession session, String payload) {
        try {
            // WebSocketSession은 동시 전송에 안전하지 않다. 같은 session으로 두 스레드가
            // 동시에 쓰면 프레임이 섞인다.
            synchronized (session) {
                if (!session.isOpen()) {
                    return false;
                }
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            // 끊긴 연결이다. 여기서 정리하지 않는다. close 콜백이 레지스트리를 정리한다.
            log.debug("이벤트 전송 실패 sessionId={}: {}", session.getId(), e.getMessage());
            return false;
        }
    }
}
