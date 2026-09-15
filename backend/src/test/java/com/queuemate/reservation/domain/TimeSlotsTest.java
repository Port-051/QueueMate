package com.queuemate.reservation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotsTest {

    private static OffsetDateTime utc(String iso) {
        return OffsetDateTime.parse(iso);
    }

    @Test
    @DisplayName("30분 경계만 허용한다")
    void onlyHalfHourBoundariesAreAligned() {
        assertThat(TimeSlots.isAligned(utc("2026-08-31T20:00:00Z"))).isTrue();
        assertThat(TimeSlots.isAligned(utc("2026-08-31T20:30:00Z"))).isTrue();
        assertThat(TimeSlots.isAligned(utc("2026-08-31T20:15:00Z"))).isFalse();
        assertThat(TimeSlots.isAligned(utc("2026-08-31T20:00:01Z"))).isFalse();
    }

    @Test
    @DisplayName("다른 시간대로 들어와도 UTC 기준으로 정렬을 본다")
    void alignmentIsJudgedInUtc() {
        // KST 05:00 = UTC 20:00. 지역 시간이 달라도 격자는 하나다.
        assertThat(TimeSlots.isAligned(
                utc("2026-09-01T05:00:00Z").withOffsetSameInstant(ZoneOffset.ofHours(9)))).isTrue();
    }

    @Test
    @DisplayName("끝 경계는 슬롯에 넣지 않는다")
    void endBoundaryIsExclusive() {
        List<OffsetDateTime> slots =
                TimeSlots.slotsBetween(utc("2026-08-31T20:00:00Z"), utc("2026-08-31T21:00:00Z"));

        assertThat(slots).containsExactly(
                utc("2026-08-31T20:00:00Z"), utc("2026-08-31T20:30:00Z"));
    }

    @Test
    @DisplayName("자정을 넘는 구간도 이어서 색인한다")
    void spansMidnight() {
        List<OffsetDateTime> slots =
                TimeSlots.slotsBetween(utc("2026-08-31T23:00:00Z"), utc("2026-09-01T00:30:00Z"));

        assertThat(slots).containsExactly(
                utc("2026-08-31T23:00:00Z"),
                utc("2026-08-31T23:30:00Z"),
                utc("2026-09-01T00:00:00Z"));
        assertThat(TimeSlots.slotKey(slots.get(2))).isEqualTo("20260901T0000");
    }

    @Test
    @DisplayName("30분 단위가 아니거나 순서가 뒤집히면 거부한다")
    void rejectsInvalidWindow() {
        assertThatThrownBy(() ->
                TimeSlots.slotsBetween(utc("2026-08-31T20:10:00Z"), utc("2026-08-31T21:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                TimeSlots.slotsBetween(utc("2026-08-31T21:00:00Z"), utc("2026-08-31T20:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("전원이 함께할 수 있는 가장 이른 슬롯을 고른다")
    void picksEarliestCommonSlot() {
        List<TimeSlots.Window> windows = List.of(
                new TimeSlots.Window(utc("2026-08-31T20:00:00Z"), utc("2026-08-31T23:00:00Z")),
                new TimeSlots.Window(utc("2026-08-31T21:00:00Z"), utc("2026-09-01T01:00:00Z")),
                new TimeSlots.Window(utc("2026-08-31T21:30:00Z"), utc("2026-08-31T22:30:00Z")));

        assertThat(TimeSlots.earliestCommonSlot(windows))
                .contains(utc("2026-08-31T21:30:00Z"));
    }

    @Test
    @DisplayName("한 명이라도 시간이 안 겹치면 공통 슬롯이 없다")
    void noCommonSlotWhenSomeoneIsOutside() {
        List<TimeSlots.Window> windows = List.of(
                new TimeSlots.Window(utc("2026-08-31T20:00:00Z"), utc("2026-08-31T21:00:00Z")),
                new TimeSlots.Window(utc("2026-08-31T21:00:00Z"), utc("2026-08-31T22:00:00Z")));

        // 21:00에 끝나는 사람과 21:00에 시작하는 사람은 함께 플레이할 수 없다.
        assertThat(TimeSlots.earliestCommonSlot(windows)).isEmpty();
    }

    @Test
    @DisplayName("경계가 맞닿기만 하면 겹친 것이 아니다")
    void touchingWindowsDoNotOverlap() {
        TimeSlots.Window early =
                new TimeSlots.Window(utc("2026-08-31T20:00:00Z"), utc("2026-08-31T21:00:00Z"));
        TimeSlots.Window late =
                new TimeSlots.Window(utc("2026-08-31T21:00:00Z"), utc("2026-08-31T22:00:00Z"));

        assertThat(early.overlaps(late)).isFalse();
        assertThat(early.overlaps(
                new TimeSlots.Window(utc("2026-08-31T20:30:00Z"), utc("2026-08-31T21:30:00Z")))).isTrue();
    }
}
