package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 방 안의 일의 거절. 코드 이름 · HTTP 상태는 {@code contracts/room-api.md} 가 원본이고 <b>{@code room} 앱이던 때와 글자까지 같다</b> — 클라이언트는
 * {@code code} 로 갈래를 정한다. 방의 규칙에 따른 거절은 서비스가 결과 enum 으로 돌려주고, 컨트롤러가 여기의 {@link ApiException} 으로 바꿔 던진다
 * (본문은 이 앱의 {@code GlobalExceptionHandler} 가 만든다).
 *
 * <p>여러 요청이 같이 쓰는 것만 여기 있다. 한 요청에만 나오는 거절은 그 컨트롤러의 {@code switch} 에 그대로 적었다 — 한눈에 계약의 표와 대조하려는 것이다.
 */
final class RoomErrors {

    static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    static final String NOT_IN_ROOM = "NOT_IN_ROOM";
    static final String ALREADY_QUEUED = "ALREADY_QUEUED";
    static final String IN_OTHER_ROOM = "IN_OTHER_ROOM";
    static final String NOT_HOST = "NOT_HOST";
    static final String TARGET_NOT_IN_ROOM = "TARGET_NOT_IN_ROOM";

    /** 요청 형식이 틀렸다 — {@code contracts/room-api.md} "공통 에러". 이 앱의 공통 코드({@code VALIDATION_FAILED})와 다르다 — {@link RoomExceptionHandler} */
    static final String INVALID_REQUEST = "INVALID_REQUEST";

    /** Redis 에 닿지 못했다 — {@code contracts/room-api.md} "공통 에러". {@link RoomExceptionHandler} */
    static final String ROOM_UNAVAILABLE = "ROOM_UNAVAILABLE";

    private RoomErrors()
    {
    }

    static ApiException roomNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND, "없는 파티방입니다");
    }

    static ApiException notInRoom()
    {
        return new ApiException(HttpStatus.FORBIDDEN, NOT_IN_ROOM, "이 파티방에 들어와 있지 않습니다");
    }

    static ApiException inOtherRoom()
    {
        return new ApiException(HttpStatus.CONFLICT, IN_OTHER_ROOM, "이미 다른 파티방에 들어가 있습니다");
    }
}
