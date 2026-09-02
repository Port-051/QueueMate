package com.queuemate.realtime.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 이 프로세스의 식별자.
 *
 * 프로세스가 뜰 때 새로 만든다. 호스트 이름이나 포트를 쓰지 않는 이유는, 재시작한 서버가
 * 이전 서버와 같은 것으로 취급되면 안 되기 때문이다. 죽은 프로세스가 남긴 접속 기록을
 * 새 프로세스가 이어받으면, 실제로는 끊긴 사용자를 접속 중으로 보게 된다.
 */
@Component
public class NodeIdentity {

    private static final Logger log = LoggerFactory.getLogger(NodeIdentity.class);

    private final UUID id = UUID.randomUUID();

    public NodeIdentity() {
        log.info("노드 식별자 nodeId={}", id);
    }

    public UUID id() {
        return id;
    }

    public String asString() {
        return id.toString();
    }
}
