package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.dto.MyRoomResponse;
import com.queuemate.platform.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방 자체 — 만들기 · 방장 확정 · 내 방 찾기. 경로 · 상태 코드 · 에러 코드의 원본은 {@code contracts/room-api.md} 다.
 *
 * <p><b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}) — {@code room} 앱이던 때의 {@code ?userId=} 는 2026-09-25 에 합치며 없앴다.
 * 방 키에는 사용자 번호를 십진 문자열로 적는다(게시판이 멤버 SET 을 사용자 번호로 읽는다). {@code roomId} 는 경로의 글자 그대로다.
 * <b>입장권은 아직 보지 않는다</b> — 합치기 전에도 검증하지 않았다(1단계는 인증만 바꿨다).
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * 방을 만든다. 부른 사람이 방장이 된다.
     *
     * <p>지금은 로그인한 누구나 아무 {@code roomId} 로 방을 만들고 방장이 된다 — 글을 쓴 사람인지 보지 않는다(합치기 전과 같다).
     */
    @PostMapping("/{roomId}")
    public ResponseEntity<Void> create(@CurrentUserId Long userId, @PathVariable String roomId)
    {
        CreateResult result = roomService.create(roomId, String.valueOf(userId));

        return switch (result) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).build();
            // 새로고침이나 재시도다. 성공이지만 새로 만든 것은 없다
            case ALREADY_CREATED -> ResponseEntity.ok().build();
            case ACTIVE_REQUEST_EXISTS -> throw new ApiException(HttpStatus.CONFLICT, RoomErrors.ALREADY_QUEUED,
                    "자동 매칭을 돌리는 동안에는 파티방을 만들 수 없습니다");
            case IN_OTHER_ROOM -> throw RoomErrors.inOtherRoom();
            case ROOM_EXISTS -> throw new ApiException(HttpStatus.CONFLICT, "ROOM_ALREADY_EXISTS", "이미 만들어진 파티방입니다");
        };
    }

    /**
     * 방장이 파티를 확정한다. 그 순간 방에 있는 전원이 파티원이 되고, 이제 새 사람이 못 들어온다. <b>되돌릴 수 없다</b> —
     * 그래서 프런트는 "확정하면 되돌릴 수 없습니다"를 띄우고 한 번 더 수락을 받은 뒤에 이 요청을 보낸다.
     */
    @PostMapping("/{roomId}/confirm")
    public ResponseEntity<Void> confirm(@CurrentUserId Long userId, @PathVariable String roomId)
    {
        ConfirmResult result = roomService.confirm(roomId, String.valueOf(userId));

        return switch (result) {
            case CONFIRMED -> ResponseEntity.noContent().build();
            // 버튼을 두 번 눌렀거나 재시도다. 성공이지만 바뀐 것은 없다
            case ALREADY_CONFIRMED -> ResponseEntity.ok().build();
            case ROOM_NOT_FOUND -> throw RoomErrors.roomNotFound();
            case NOT_HOST -> throw new ApiException(HttpStatus.FORBIDDEN, RoomErrors.NOT_HOST, "방장만 확정할 수 있습니다");
            case NOT_ENOUGH_MEMBERS -> throw new ApiException(HttpStatus.CONFLICT, "NOT_ENOUGH_MEMBERS",
                    "혼자서는 확정할 수 없습니다");
        };
    }

    /**
     * 내가 지금 어느 방에 있나. roomId 를 모르는 클라이언트(앱을 새로 연 경우)가 부른다.
     * 방에 없으면 {@code roomId} 가 {@code null} 이다.
     *
     * <p>{@code /me} 는 글자 그대로의 경로라 {@code /{roomId}} 보다 먼저 잡힌다.
     */
    @GetMapping("/me")
    public MyRoomResponse myRoom(@CurrentUserId Long userId)
    {
        // 방에 없는 것은 에러가 아니라 정상 상태다. 그래서 404 가 아니라 200 에 {"roomId": null} 이다
        return new MyRoomResponse(roomService.myRoom(String.valueOf(userId)));
    }
}
