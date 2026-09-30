package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveResultTest {

    @Test
    @DisplayName("leave-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(LeaveResult.fromCode(1L)).isEqualTo(LeaveResult.LEFT);
        assertThat(LeaveResult.fromCode(2L)).isEqualTo(LeaveResult.ROOM_CLOSED);
        assertThat(LeaveResult.fromCode(-1L)).isEqualTo(LeaveResult.NOT_IN_ROOM);
    }

    @Test
    @DisplayName("모르는 값과 null 을 성공으로 읽지 않는다 — 스크립트가 return 을 빠뜨리면 null 이 온다")
    void unknownCodeIsNotALeave()
    {
        assertThatThrownBy(() -> LeaveResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LeaveResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
