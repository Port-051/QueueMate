package com.queuemate.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis가 다시 쓰기를 받는지 딱 한 번 확인한다.
 *
 * <p>PING으로는 안 된다. failover 직후 강등된 구 master는 PING에 정상 응답하면서
 * 쓰기만 {@code READONLY You can't write against a read only replica}로 거절한다.
 * 이 서비스가 Redis에 하는 일은 전부 쓰기(guard 획득, 대기열 등록, claim)이므로
 * 읽기가 되는 것은 살아났다는 근거가 되지 않는다.
 *
 * <p>그래서 실제 쓰기를 한 번 한다. 키는 짧은 TTL을 달아 스스로 사라지게 둔다.
 */
@Component
public class RedisWriteProbe {

    private static final Logger log = LoggerFactory.getLogger(RedisWriteProbe.class);

    /** 도메인 키와 섞이지 않도록 따로 둔다. TTL이 있어 쌓이지 않는다. */
    private static final String KEY = "qm:redis:probe";
    private static final Duration TTL = Duration.ofSeconds(10);

    private final StringRedisTemplate redis;

    public RedisWriteProbe(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return 쓰기가 받아들여졌으면 true */
    public boolean writable() {
        try {
            redis.opsForValue().set(KEY, Instant.now().toString(), TTL);
            return true;
        } catch (DataAccessException e) {
            // 아직 구 master를 보고 있거나 새 master 주소가 전파되지 않았다.
            log.debug("[failover] 쓰기 확인 실패", e);
            return false;
        }
    }
}
