package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignalResultTest {

    @Test
    @DisplayName("signal-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(SignalResult.fromCode(1L)).isEqualTo(SignalResult.SENT);
        assertThat(SignalResult.fromCode(-1L)).isEqualTo(SignalResult.NOT_IN_ROOM);
        assertThat(SignalResult.fromCode(-3L)).isEqualTo(SignalResult.TARGET_NOT_IN_ROOM);
    }

    @Test
    @DisplayName("모르는 값과 null 을 '보내도 된다'로 읽지 않는다")
    void unknownCodeIsNotSent()
    {
        assertThatThrownBy(() -> SignalResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SignalResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
