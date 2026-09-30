package com.queuemate.platform.room.domain;

import java.util.List;

/**
 * 방 안 사람 목록 조회의 결과. 컨트롤러가 {@link #status} 로 응답 코드를 정한다.
 *
 * <p>입장 · 나가기와 달리 코드 하나가 아니라 <b>데이터</b>(방장과 멤버)를 실어야 해서 enum 이 아니라 record 다.
 *
 * @param hostId  {@link Status#FOUND} 일 때만 있다
 * @param members {@link Status#FOUND} 일 때만 있다. SET 에서 읽은 것이라 <b>순서에 뜻이 없다</b> — 방장은 {@code hostId} 로 구분한다
 */
public record RoomMembersResult(Status status, String hostId, List<String> members) {

    public enum Status {

        /** 방이 있고 목록을 읽었다 */
        FOUND,

        /** 그런 방이 없다. 만들어진 적이 없거나 방장이 나가서 사라졌다 */
        ROOM_NOT_FOUND,

        /** 묻는 사람이 이 방에 없다. 방 안의 사람만 목록을 볼 수 있다 */
        NOT_IN_ROOM
    }

    public static RoomMembersResult found(String hostId, List<String> members)
    {
        return new RoomMembersResult(Status.FOUND, hostId, members);
    }

    public static RoomMembersResult roomNotFound()
    {
        return new RoomMembersResult(Status.ROOM_NOT_FOUND, null, List.of());
    }

    public static RoomMembersResult notInRoom()
    {
        return new RoomMembersResult(Status.NOT_IN_ROOM, null, List.of());
    }
}
