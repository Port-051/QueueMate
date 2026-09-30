package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterResultTest {

    @Test
    @DisplayName("enter-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(EnterResult.fromCode(1L)).isEqualTo(EnterResult.ENTERED);
        assertThat(EnterResult.fromCode(2L)).isEqualTo(EnterResult.ALREADY_ENTERED);
        assertThat(EnterResult.fromCode(-1L)).isEqualTo(EnterResult.ACTIVE_REQUEST_EXISTS);
        assertThat(EnterResult.fromCode(-2L)).isEqualTo(EnterResult.FULL);
        assertThat(EnterResult.fromCode(-3L)).isEqualTo(EnterResult.IN_OTHER_ROOM);
        assertThat(EnterResult.fromCode(-4L)).isEqualTo(EnterResult.ROOM_NOT_FOUND);
        assertThat(EnterResult.fromCode(-7L)).isEqualTo(EnterResult.ROOM_CONFIRMED);
    }

    @Test
    @DisplayName("모르는 값과 null 을 입장으로 읽지 않는다")
    void unknownCodeIsNotAnEntry()
    {
        assertThatThrownBy(() -> EnterResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnterResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
