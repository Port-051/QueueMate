package com.queuemate.realtime.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이 노드가 살아 있다고 주기적으로 알린다.
 *
 * 서버당 키 하나다. 접속자 수와 무관하다. 프로세스가 죽으면 이 키가 만료되면서
 * 그 노드에 붙어 있던 사용자들의 접속 기록이 한꺼번에 무효가 된다.
 */
@Component
public class NodeHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(NodeHeartbeat.class);

    private final ClusterPresence presence;

    public NodeHeartbeat(ClusterPresence presence) {
        this.presence = presence;
    }

    /** 처음 한 번은 곧바로 찍는다. 키가 없는 동안 다른 노드가 이 노드를 죽은 것으로 본다. */
    @Scheduled(initialDelay = 0, fixedDelay = 10_000)
    public void beat() {
        try {
            presence.touchNode();
        } catch (DataAccessException e) {
            // 이 노드에 붙은 사용자들이 다른 노드에서 오프라인으로 보이게 된다.
            log.error("노드 생존 신호 실패", e);
        }
    }
}
