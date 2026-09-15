package com.queuemate.realtime.presence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 연결이 끊긴 사용자를 유예 시간이 지날 때까지 담아 둔다.
 *
 * 메모리에 두면 서버가 재시작할 때 통째로 사라져서, 그 사이 끊긴 사람들의 파티가
 * 영원히 안 닫힌다. Redis에 두면 재시작을 넘긴다.
 *
 * 만료 시각을 score로 쓰는 ZSET이다. 키를 훑지 않고 만료된 것만 꺼낼 수 있다.
 */
@Component
public class DeparturePendingStore {

    private static final Logger log = LoggerFactory.getLogger(DeparturePendingStore.class);
    private static final String KEY = "qm:party:departure-due";

    private final StringRedisTemplate redis;

    public DeparturePendingStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 연결이 끊겼다. 유예 시간 뒤로 만료 시각을 잡는다. */
    public void schedule(UUID userId, Duration grace) {
        try {
            redis.opsForZSet().add(KEY, userId.toString(),
                    Instant.now().plus(grace).toEpochMilli());
        } catch (DataAccessException e) {
            // 놓치면 그 사용자의 파티가 안 닫힌다. 조용히 지나가면 원인을 못 찾는다.
            log.error("이탈 예약 실패 userId={}", userId, e);
        }
    }

    /**
     * 아직 예약이 없을 때만 잡는다. 주기적인 점검이 쓴다.
     *
     * 그냥 schedule을 부르면 안 된다. ZADD가 점수를 덮어써서, 점검이 돌 때마다
     * 만료 시각이 뒤로 밀린다. 게임 중 유예가 5분인데 점검이 1분마다 돌면
     * 그 사용자는 영원히 만료되지 않는다.
     *
     * @return 이번에 새로 잡았으면 true
     */
    public boolean scheduleIfAbsent(UUID userId, Duration grace) {
        try {
            return Boolean.TRUE.equals(redis.opsForZSet().addIfAbsent(KEY, userId.toString(),
                    Instant.now().plus(grace).toEpochMilli()));
        } catch (DataAccessException e) {
            log.error("이탈 예약 실패 userId={}", userId, e);
            return false;
        }
    }

    /** 돌아왔다. 예약을 취소한다. */
    public void cancel(UUID userId) {
        try {
            redis.opsForZSet().remove(KEY, userId.toString());
        } catch (DataAccessException e) {
            // 취소를 놓치면 붙어 있는 사용자를 내보낼 수 있다. sweeper가 다시 확인한다.
            log.warn("이탈 예약 취소 실패 userId={}", userId);
        }
    }

    /** 만료된 항목을 꺼내면서 목록에서 지운다. 같은 사용자를 두 번 처리하지 않기 위해서다. */
    public List<UUID> pollDue() {
        try {
            Set<String> due = redis.opsForZSet()
                    .rangeByScore(KEY, 0, Instant.now().toEpochMilli());
            if (due == null || due.isEmpty()) {
                return List.of();
            }
            redis.opsForZSet().remove(KEY, due.toArray());
            return due.stream().map(UUID::fromString).toList();
        } catch (DataAccessException e) {
            log.warn("이탈 대상 조회 실패, 다음 주기에 다시 본다");
            return List.of();
        }
    }
}
