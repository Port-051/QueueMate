package com.queuemate.platform.room.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchRoomResultTest {

    @Test
    @DisplayName("enter-match-room.lua 의 반환값과 짝이 맞는다")
    void codesMatchTheScript()
    {
        assertThat(MatchRoomResult.fromCode(1L)).isEqualTo(MatchRoomResult.CREATED);
        assertThat(MatchRoomResult.fromCode(2L)).isEqualTo(MatchRoomResult.ALREADY_IN_ROOM);
        assertThat(MatchRoomResult.fromCode(3L)).isEqualTo(MatchRoomResult.ENTERED);
        assertThat(MatchRoomResult.fromCode(-2L)).isEqualTo(MatchRoomResult.ROOM_FULL);
        assertThat(MatchRoomResult.fromCode(-3L)).isEqualTo(MatchRoomResult.IN_OTHER_ROOM);
        assertThat(MatchRoomResult.fromCode(-4L)).isEqualTo(MatchRoomResult.PARTY_NOT_FOUND);
        assertThat(MatchRoomResult.fromCode(-6L)).isEqualTo(MatchRoomResult.NOT_PARTY_MEMBER);
    }

    @Test
    @DisplayName("모르는 값과 null 을 입장으로 읽지 않는다")
    void unknownCodeIsNotAnEntry()
    {
        assertThatThrownBy(() -> MatchRoomResult.fromCode(0L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MatchRoomResult.fromCode(-1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> MatchRoomResult.fromCode(null)).isInstanceOf(IllegalStateException.class);
    }
}
