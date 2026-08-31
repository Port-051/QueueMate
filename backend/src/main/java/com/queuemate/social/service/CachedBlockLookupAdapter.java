package com.queuemate.social.service;

import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.social.repository.BlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 후보 필터링용 차단 집합을 Redis에 캐시한다.
 *
 * Redis가 죽어도 DB에서 그대로 읽는다. 캐시 실패를 이유로 차단을 무시하지는 않는다.
 * 매칭 확정 직전 재검증은 캐시를 아예 거치지 않는다 (docs/13 Blocking).
 */
@Component
public class CachedBlockLookupAdapter implements BlockLookupPort {

    private static final Logger log = LoggerFactory.getLogger(CachedBlockLookupAdapter.class);
    private static final String KEY_PREFIX = "social:blocks:";
    private static final Duration TTL = Duration.ofMinutes(10);
    /** 차단이 하나도 없는 사용자도 캐시해서 매번 DB를 때리지 않게 한다. */
    private static final String EMPTY_MARKER = "";

    private final BlockRepository blocks;
    private final StringRedisTemplate redis;

    public CachedBlockLookupAdapter(BlockRepository blocks, StringRedisTemplate redis) {
        this.blocks = blocks;
        this.redis = redis;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> blockedUserIds(UUID userId) {
        Set<String> cached = readCache(userId);
        if (cached != null) {
            return cached.stream()
                    .filter(value -> !EMPTY_MARKER.equals(value))
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());
        }
        Set<UUID> fromDb = new HashSet<>(blocks.findCounterpartIds(userId));
        writeCache(userId, fromDb);
        return fromDb;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean anyBlockBetween(Collection<UUID> userIds) {
        if (userIds == null || userIds.size() < 2) {
            return false;
        }
        Set<UUID> distinct = new HashSet<>(userIds);
        if (distinct.size() < 2) {
            return false;
        }
        return blocks.countBlocksWithin(distinct) > 0;
    }

    /** 차단 추가/해제 시 양쪽 사용자의 캐시를 함께 버린다. 차단은 양방향으로 작동한다. */
    public void invalidate(UUID one, UUID other) {
        try {
            redis.delete(Set.of(key(one), key(other)));
        } catch (DataAccessException e) {
            // 캐시를 못 지웠으면 TTL 만료까지 낡은 값이 남는다. 후보 필터가 과하게
            // 배제하거나 놓칠 수 있지만 확정 직전 재검증이 최종 방어선이다.
            log.error("차단 캐시 무효화 실패 users={},{}", one, other, e);
        }
    }

    private Set<String> readCache(UUID userId) {
        try {
            Set<String> members = redis.opsForSet().members(key(userId));
            return (members == null || members.isEmpty()) ? null : members;
        } catch (DataAccessException e) {
            log.warn("차단 캐시 조회 실패, DB로 우회한다 userId={}", userId);
            return null;
        }
    }

    private void writeCache(UUID userId, Set<UUID> counterparts) {
        try {
            String key = key(userId);
            String[] values = counterparts.isEmpty()
                    ? new String[]{EMPTY_MARKER}
                    : counterparts.stream().map(UUID::toString).toArray(String[]::new);
            redis.opsForSet().add(key, values);
            redis.expire(key, TTL);
        } catch (DataAccessException e) {
            log.warn("차단 캐시 저장 실패 userId={}", userId);
        }
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
