package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KickResultTest {

    @Test
    @DisplayName("kick-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(KickResult.fromCode(1L)).isEqualTo(KickResult.KICKED);
        assertThat(KickResult.fromCode(-3L)).isEqualTo(KickResult.TARGET_NOT_IN_ROOM);
        assertThat(KickResult.fromCode(-4L)).isEqualTo(KickResult.ROOM_NOT_FOUND);
        assertThat(KickResult.fromCode(-5L)).isEqualTo(KickResult.NOT_HOST);
        assertThat(KickResult.fromCode(-6L)).isEqualTo(KickResult.CANNOT_KICK_SELF);
    }

    @Test
    @DisplayName("모르는 값과 null 을 성공으로 읽지 않는다 — 스크립트가 return 을 빠뜨리면 null 이 온다")
    void unknownCodeIsNotAKick()
    {
        assertThatThrownBy(() -> KickResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        // 다른 스크립트에는 있지만 강퇴에는 없는 값이다
        assertThatThrownBy(() -> KickResult.fromCode(-1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> KickResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
