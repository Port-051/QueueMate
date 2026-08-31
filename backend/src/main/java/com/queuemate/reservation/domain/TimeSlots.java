package com.queuemate.reservation.domain;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 예약 시간의 30분 격자 (docs/04 §2·§7).
 *
 * <p>사용자는 자기 지역 시간으로 입력하지만 저장과 색인은 언제나 UTC 기준이다.
 * 격자를 고정해야 "겹치는가"를 집합 연산으로 다룰 수 있다.
 */
public final class TimeSlots {

    public static final Duration SLOT = Duration.ofMinutes(30);

    private static final DateTimeFormatter SLOT_KEY =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm").withZone(ZoneOffset.UTC);

    private TimeSlots() {
    }

    /** 30분 경계에 맞는지. 초/나노까지 0이어야 한다. */
    public static boolean isAligned(OffsetDateTime time) {
        OffsetDateTime utc = time.withOffsetSameInstant(ZoneOffset.UTC);
        return utc.getMinute() % 30 == 0 && utc.getSecond() == 0 && utc.getNano() == 0;
    }

    /**
     * [from, to) 구간에 들어가는 30분 슬롯의 시작 시각들.
     *
     * <p>끝 경계는 넣지 않는다. 21:00에 끝나는 사람은 21:00에 시작하는 게임을 함께할 수 없다.
     */
    public static List<OffsetDateTime> slotsBetween(OffsetDateTime from, OffsetDateTime to) {
        requireAligned(from, "availableFrom");
        requireAligned(to, "availableTo");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("시작이 끝보다 앞서야 한다");
        }
        List<OffsetDateTime> slots = new ArrayList<>();
        OffsetDateTime cursor = from.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime end = to.withOffsetSameInstant(ZoneOffset.UTC);
        while (cursor.isBefore(end)) {
            slots.add(cursor);
            cursor = cursor.plus(SLOT);
        }
        return slots;
    }

    /** Redis 슬롯 키에 들어가는 부분. 예: {@code 20260829T2000}. */
    public static String slotKey(OffsetDateTime slotStart) {
        return SLOT_KEY.format(slotStart);
    }

    /**
     * 모두가 함께할 수 있는 가장 이른 슬롯 (docs/04 §5).
     *
     * @return 전원의 window가 겹치는 첫 30분 슬롯. 겹치지 않으면 empty
     */
    public static Optional<OffsetDateTime> earliestCommonSlot(List<Window> windows) {
        if (windows == null || windows.isEmpty()) {
            return Optional.empty();
        }
        OffsetDateTime start = windows.get(0).from();
        OffsetDateTime end = windows.get(0).to();
        for (Window window : windows) {
            start = window.from().isAfter(start) ? window.from() : start;
            end = window.to().isBefore(end) ? window.to() : end;
        }
        if (!start.isBefore(end)) {
            return Optional.empty();
        }
        return Optional.of(start.withOffsetSameInstant(ZoneOffset.UTC));
    }

    private static void requireAligned(OffsetDateTime time, String field) {
        if (!isAligned(time)) {
            throw new IllegalArgumentException(field + "는 30분 단위여야 한다: " + time);
        }
    }

    /** 플레이 가능한 시간 구간. */
    public record Window(OffsetDateTime from, OffsetDateTime to) {
        public Window {
            if (from == null || to == null) {
                throw new IllegalArgumentException("from과 to는 필수다");
            }
        }

        public boolean overlaps(Window other) {
            return from.isBefore(other.to) && other.from.isBefore(to);
        }
    }
}
