package com.queuemate.reservation.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.infra.MatchingRedisKeys;
import com.queuemate.reservation.domain.TimeSlots;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 예약을 30분 슬롯 버킷에 색인한다 (docs/04 §7, docs/07 §8).
 *
 * <p>버킷에는 예약 id만 담는다. 조건 판정은 DB를 읽고 하며, 이 색인은 후보를 좁히는 용도다.
 * 수정/취소는 반드시 기존 슬롯을 모두 지우고 다시 넣는다. 낡은 색인이 남으면
 * 이미 사라진 예약이 계속 후보로 올라온다.
 */
@Repository
public class ReservationSlotIndex {

    /** 슬롯이 지난 뒤에도 잠시 남겨 둔다. 정리를 못 해도 결국 사라지게 하는 안전망이다. */
    private static final Duration RETENTION_AFTER_SLOT = Duration.ofHours(6);

    private final StringRedisTemplate redis;

    public ReservationSlotIndex(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void index(UUID reservationId, GameKey game, String modeKey,
                      OffsetDateTime from, OffsetDateTime to) {
        for (OffsetDateTime slot : TimeSlots.slotsBetween(from, to)) {
            String key = keyOf(game, modeKey, slot);
            redis.opsForSet().add(key, reservationId.toString());
            redis.expireAt(key, slot.plus(TimeSlots.SLOT).plus(RETENTION_AFTER_SLOT).toInstant());
        }
    }

    public void remove(UUID reservationId, GameKey game, String modeKey,
                       OffsetDateTime from, OffsetDateTime to) {
        for (OffsetDateTime slot : TimeSlots.slotsBetween(from, to)) {
            redis.opsForSet().remove(keyOf(game, modeKey, slot), reservationId.toString());
        }
    }

    /**
     * 주어진 구간과 한 슬롯이라도 겹치는 예약 id.
     *
     * @return 자기 자신을 뺀 후보 id 집합. 순서는 보장하지 않는다
     */
    public Set<UUID> candidatesOverlapping(GameKey game, String modeKey,
                                           OffsetDateTime from, OffsetDateTime to, UUID excluded) {
        List<OffsetDateTime> slots = TimeSlots.slotsBetween(from, to);
        if (slots.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> keys = new ArrayList<>(slots.size());
        for (OffsetDateTime slot : slots) {
            keys.add(keyOf(game, modeKey, slot));
        }
        Set<String> raw = redis.opsForSet().union(keys.get(0), keys.subList(1, keys.size()));
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> ids = new LinkedHashSet<>(raw.size());
        for (String value : raw) {
            UUID id = UUID.fromString(value);
            if (!id.equals(excluded)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String keyOf(GameKey game, String modeKey, OffsetDateTime slot) {
        return MatchingRedisKeys.reservationSlot(game, modeKey, TimeSlots.slotKey(slot));
    }
}
