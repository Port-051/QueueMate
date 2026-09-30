package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeartbeatResultTest {

    @Test
    @DisplayName("heartbeat-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(HeartbeatResult.fromCode(1L)).isEqualTo(HeartbeatResult.ALIVE);
        assertThat(HeartbeatResult.fromCode(-1L)).isEqualTo(HeartbeatResult.NOT_IN_ROOM);
        assertThat(HeartbeatResult.fromCode(-2L)).isEqualTo(HeartbeatResult.ROOM_CLOSED);
    }

    @Test
    @DisplayName("모르는 값과 null 을 '살아 있다'로 읽지 않는다 — 스크립트가 return 을 빠뜨리면 null 이 온다")
    void unknownCodeIsNotAlive()
    {
        assertThatThrownBy(() -> HeartbeatResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> HeartbeatResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
