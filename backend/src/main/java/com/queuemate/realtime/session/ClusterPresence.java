package com.queuemate.realtime.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 이 사용자가 어느 서버에든 붙어 있는가.
 *
 * SessionRegistry는 이 프로세스만 안다. 서버가 여러 대면 다른 인스턴스에 붙은 사용자가
 * 언제나 오프라인으로 보이고, 이탈 정리가 게임 중인 사람을 파티에서 내보낸다.
 * 이벤트를 놓치는 것과 달리 데이터가 바뀌는 오류라 이쪽이 더 급하다.
 *
 * 자료구조가 둘이다.
 *
 *   qm:ws:presence:{userId}  SET nodeId   이 사용자가 붙어 있는 노드들
 *   qm:ws:node:{nodeId}      STRING + TTL 그 노드가 살아 있다는 표시
 *
 * 나눈 이유는 죽은 서버 때문이다. 프로세스가 죽으면 close 콜백이 돌지 않아 presence에서
 * 자기를 지우지 못한다. 사용자마다 만료를 걸면 접속 중인 사람의 기록까지 사라지므로
 * 사용자 수만큼 갱신해야 한다. 노드 생존을 따로 두면 갱신이 서버당 키 하나로 끝나고,
 * 서버가 죽으면 그 노드의 기록이 한꺼번에 무효가 된다.
 */
@Component
public class ClusterPresence {

    private static final Logger log = LoggerFactory.getLogger(ClusterPresence.class);

    static final Duration HEARTBEAT = Duration.ofSeconds(10);
    /** 놓친 heartbeat 두 번까지 견딘다. 간격보다 짧게 두면 살아 있는 노드가 죽은 것으로 보인다. */
    static final Duration NODE_TTL = HEARTBEAT.multipliedBy(3);
    /** presence set이 영원히 남지 않게 하는 보루다. 정상 경로는 연결이 끊길 때의 SREM이다. */
    private static final Duration PRESENCE_TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final SessionRegistry sessions;
    private final NodeIdentity node;

    public ClusterPresence(StringRedisTemplate redis, SessionRegistry sessions, NodeIdentity node) {
        this.redis = redis;
        this.sessions = sessions;
        this.node = node;
    }

    public void markOnline(UUID userId) {
        try {
            redis.opsForSet().add(presenceKey(userId), node.asString());
            redis.expire(presenceKey(userId), PRESENCE_TTL);
            touchNode();
        } catch (DataAccessException e) {
            // 기록하지 못하면 다른 노드가 이 사용자를 오프라인으로 본다.
            // 그쪽에서 이탈 처리가 돌 수 있어 조용히 넘기지 않는다.
            log.error("접속 기록 실패 userId={}", userId, e);
        }
    }

    /** 이 노드의 마지막 session이 닫힐 때 부른다. 탭이 남아 있으면 부르지 않는다. */
    public void markOffline(UUID userId) {
        try {
            redis.opsForSet().remove(presenceKey(userId), node.asString());
        } catch (DataAccessException e) {
            // 지우지 못해도 노드 생존 키가 살아 있는 동안만 오해가 남는다.
            log.warn("접속 해제 기록 실패 userId={}", userId);
        }
    }

    /** 이 노드가 살아 있다고 알린다. heartbeat가 주기적으로 부른다. */
    public void touchNode() {
        redis.opsForValue().set(nodeKey(node.asString()), "1", NODE_TTL);
    }

    /**
     * 확인할 수 없으면 접속 중으로 답한다.
     *
     * 아니라고 잘못 답하면 게임 중인 사용자를 파티에서 내보낸다. 되돌릴 수 없다.
     * 맞다고 잘못 답하면 이미 나간 사용자의 파티 정리가 늦어질 뿐이다.
     * 어차피 Redis가 죽으면 이탈 대기 목록도 못 읽어서 아무도 내보내지 않는다.
     */
    public boolean isOnline(UUID userId) {
        if (sessions.hasLocalSession(userId)) {
            return true;
        }
        try {
            Set<String> nodes = redis.opsForSet().members(presenceKey(userId));
            if (nodes == null || nodes.isEmpty()) {
                return false;
            }
            for (String nodeId : nodes) {
                // 내 노드는 위에서 이미 확인했다. 여기 남아 있다면 지우지 못한 흔적이다.
                if (node.asString().equals(nodeId)) {
                    continue;
                }
                if (Boolean.TRUE.equals(redis.hasKey(nodeKey(nodeId)))) {
                    return true;
                }
            }
            // 살아 있는 노드가 하나도 없다. 죽은 서버가 남긴 기록이므로 여기서 걷는다.
            redis.opsForSet().remove(presenceKey(userId), nodes.toArray());
            return false;
        } catch (DataAccessException e) {
            log.warn("접속 여부 확인 실패. 접속 중으로 본다 userId={}", userId);
            return true;
        }
    }

    /**
     * 이 중 어느 노드에도 붙어 있지 않은 사람들.
     *
     * 사용자마다 isOnline을 부르면 같은 노드의 생존 확인이 사람 수만큼 반복된다.
     * 한 번의 점검 안에서는 노드의 생사가 바뀌지 않는다고 보고 답을 재사용한다.
     * 점검 도중에 노드가 죽어도 다음 주기에 잡히므로 손해가 없다.
     *
     * 확인할 수 없으면 빈 목록을 준다. 모르는 것을 오프라인으로 답하면
     * 접속 중인 사용자를 파티에서 내보낸다.
     */
    public Set<UUID> offlineAmong(Collection<UUID> userIds) {
        Set<UUID> offline = new HashSet<>();
        Map<String, Boolean> nodeAlive = new HashMap<>();
        for (UUID userId : userIds) {
            if (sessions.hasLocalSession(userId)) {
                continue;
            }
            try {
                if (!anyLiveNode(userId, nodeAlive)) {
                    offline.add(userId);
                }
            } catch (DataAccessException e) {
                log.warn("접속 여부 확인 실패. 점검을 멈춘다 userId={}", userId);
                return Set.of();
            }
        }
        return offline;
    }

    private boolean anyLiveNode(UUID userId, Map<String, Boolean> nodeAlive) {
        Set<String> nodes = redis.opsForSet().members(presenceKey(userId));
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }
        for (String nodeId : nodes) {
            if (node.asString().equals(nodeId)) {
                continue;
            }
            if (nodeAlive.computeIfAbsent(nodeId,
                    id -> Boolean.TRUE.equals(redis.hasKey(nodeKey(id))))) {
                return true;
            }
        }
        redis.opsForSet().remove(presenceKey(userId), nodes.toArray());
        return false;
    }

    private String presenceKey(UUID userId) {
        return "qm:ws:presence:" + userId;
    }

    private String nodeKey(String nodeId) {
        return "qm:ws:node:" + nodeId;
    }
}
