package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateResultTest {

    @Test
    @DisplayName("create-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(CreateResult.fromCode(1L)).isEqualTo(CreateResult.CREATED);
        assertThat(CreateResult.fromCode(2L)).isEqualTo(CreateResult.ALREADY_CREATED);
        assertThat(CreateResult.fromCode(-1L)).isEqualTo(CreateResult.ACTIVE_REQUEST_EXISTS);
        assertThat(CreateResult.fromCode(-3L)).isEqualTo(CreateResult.IN_OTHER_ROOM);
        assertThat(CreateResult.fromCode(-4L)).isEqualTo(CreateResult.ROOM_EXISTS);
    }

    @Test
    @DisplayName("모르는 값과 null 을 성공으로 읽지 않는다")
    void unknownCodeIsNotACreation()
    {
        assertThatThrownBy(() -> CreateResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CreateResult.fromCode(-2L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> CreateResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
