package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmResultTest {

    @Test
    @DisplayName("confirm-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(ConfirmResult.fromCode(1L)).isEqualTo(ConfirmResult.CONFIRMED);
        assertThat(ConfirmResult.fromCode(2L)).isEqualTo(ConfirmResult.ALREADY_CONFIRMED);
        assertThat(ConfirmResult.fromCode(-4L)).isEqualTo(ConfirmResult.ROOM_NOT_FOUND);
        assertThat(ConfirmResult.fromCode(-5L)).isEqualTo(ConfirmResult.NOT_HOST);
        assertThat(ConfirmResult.fromCode(-7L)).isEqualTo(ConfirmResult.NOT_ENOUGH_MEMBERS);
    }

    @Test
    @DisplayName("모르는 값과 null 을 확정으로 읽지 않는다")
    void unknownCodeIsNotAConfirmation()
    {
        assertThatThrownBy(() -> ConfirmResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ConfirmResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
