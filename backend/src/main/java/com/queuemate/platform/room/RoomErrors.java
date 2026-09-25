package com.queuemate.platform.room;

import com.queuemate.platform.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 방 안의 일의 거절. 코드 이름 · HTTP 상태는 {@code contracts/platform-api.md} "방" 이 원본이다 — 클라이언트는 {@code code} 로 갈래를 정한다.
 * 방의 규칙에 따른 거절은 서비스가 결과 enum 으로 돌려주고, 컨트롤러(또는 글을 쓰며 방을 만드는 {@code party.service.PostStore})가
 * 여기의 {@link ApiException} 으로 바꿔 던진다(본문은 {@code GlobalExceptionHandler} 가 만든다).
 *
 * <p><b>{@code room} 패키지 밖에서도 쓴다</b> — 2026-09-25 2단계로 글 쓰기가 방을 만들고({@code PostStore#create}) 입장이 글을 검사하게 되어
 * ({@code PostEntryGate}) 같은 거절을 두 패키지가 같이 낸다. 그래서 {@code controller} 밖의 이 자리로 옮겼다.
 *
 * <p>여러 요청이 같이 쓰는 것만 여기 있다. 한 요청에만 나오는 거절은 그 컨트롤러의 {@code switch} 에 그대로 적었다 — 한눈에 계약의 표와 대조하려는 것이다.
 */
public final class RoomErrors {

    public static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String NOT_IN_ROOM = "NOT_IN_ROOM";
    public static final String ALREADY_QUEUED = "ALREADY_QUEUED";
    public static final String IN_OTHER_ROOM = "IN_OTHER_ROOM";
    public static final String NOT_HOST = "NOT_HOST";
    public static final String TARGET_NOT_IN_ROOM = "TARGET_NOT_IN_ROOM";

    /**
     * 방의 상태(Redis)를 확인할 수 없다. <b>게시판의 fail-closed 거절(글 고치기)과 방 안의 일의 Redis 장애가 같은 코드다</b> —
     * 2026-09-25 2단계로 {@code room} 앱이던 때의 {@code ROOM_UNAVAILABLE} 을 이 이름으로 합쳤다(Claude 가 정한 세부. 뜻이 같은 코드가 둘일 이유가 없다).
     */
    public static final String ROOM_STATE_UNAVAILABLE = "ROOM_STATE_UNAVAILABLE";

    /** 503 에 싣는 {@code Retry-After}(초). {@code room} 앱이던 때의 값 그대로다 */
    static final long RETRY_AFTER_SECONDS = 5;

    private RoomErrors()
    {
    }

    public static ApiException roomNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND, "없는 파티방입니다");
    }

    public static ApiException notInRoom()
    {
        return new ApiException(HttpStatus.FORBIDDEN, NOT_IN_ROOM, "이 파티방에 들어와 있지 않습니다");
    }

    public static ApiException inOtherRoom()
    {
        return new ApiException(HttpStatus.CONFLICT, IN_OTHER_ROOM, "이미 다른 파티방에 들어가 있습니다");
    }

    public static ApiException alreadyQueued(String message)
    {
        return new ApiException(HttpStatus.CONFLICT, ALREADY_QUEUED, message);
    }

    /**
     * 503 {@code ROOM_STATE_UNAVAILABLE} + {@code Retry-After: 5}. 방의 상태는 Redis 에만 있어서 확인이 안 되면 통과시킬 수 없다 —
     * 정원을 못 세는데 입장시키거나, 방 안에 누가 있는지 모르는데 글을 고치게 하지 않는다. 헤더는 {@link ApiException#retryAfter} 로 싣는다
     * (로그인 실패 제한의 429 와 같은 길이다 — {@code GlobalExceptionHandler#handleApi}).
     */
    public static ApiException stateUnavailable()
    {
        return ApiException.retryAfter(HttpStatus.SERVICE_UNAVAILABLE, ROOM_STATE_UNAVAILABLE,
                "방의 상태를 확인할 수 없습니다. 잠시 뒤에 다시 시도해 주세요", RETRY_AFTER_SECONDS);
    }
}
