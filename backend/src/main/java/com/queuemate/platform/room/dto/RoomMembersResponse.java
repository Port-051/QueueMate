package com.queuemate.platform.room.dto;

import java.util.List;

/**
 * 방 안 사람 목록의 응답 본문. {@code members} 에는 방장도 들어 있다.
 */
public record RoomMembersResponse(String roomId, String hostId, List<String> members) {
}
