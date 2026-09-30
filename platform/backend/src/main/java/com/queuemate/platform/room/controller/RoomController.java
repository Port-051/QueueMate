package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.ConfirmResult;
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
 * 방 자체 — 방장 확정 · 내 방 찾기. 경로 · 상태 코드 · 에러 코드의 원본은 {@code contracts/platform-api.md} "방" 이다.
 *
 * <p><b>방 만들기 요청({@code POST /api/v1/rooms/{roomId}})은 없다</b>(2026-09-25 2단계 — 소유자 결정 C). 게시판의 방은 글 쓰기가 만든다
 * ({@code POST /api/v1/posts}). 자동 매칭 파티의 방은 {@code POST /api/v1/match-parties/{partyId}/room} 이 만든다(2026-09-27 P-30).
 *
 * <p><b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}). 방 키에는 사용자 번호를 십진 문자열로 적는다.
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final PostService postService;

    /**
     * 방장이 파티를 확정한다. 그 순간 방에 있는 전원이 파티원이 되고, 이제 새 사람이 못 들어온다. <b>되돌릴 수 없다</b> —
     * 그래서 프런트는 "확정하면 되돌릴 수 없습니다"를 띄우고 한 번 더 수락을 받은 뒤에 이 요청을 보낸다.
     *
     * <p><b>한 요청에서 Redis 와 DB 를 같이 쓴다</b>(2026-09-25 2단계) — 방의 확정(스크립트)이 성공하면 같은 요청에서 글을 {@code CONFIRMED} 로 바꾸고
     * 파티와 파티원을 적는다({@link PostService#confirmRoom}). 두 앱이던 때 브라우저가 따로 부르던 {@code POST /api/v1/posts/{postId}/confirm} 은 없어졌다.
     *
     * <p>{@code roomId} 는 글의 번호다 — 숫자가 아니면 그런 글이 있을 수 없어 404 {@code ROOM_NOT_FOUND} 다(글이 없는 방은 만들어질 길이 없다).
     */
    @PostMapping("/{roomId}/confirm")
    public ResponseEntity<Void> confirm(@CurrentUserId Long userId, @PathVariable String roomId)
    {
        ConfirmResult result = postService.confirmRoom(userId, postIdOf(roomId));

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

    private static Long postIdOf(String roomId)
    {
        try
        {
            return Long.parseLong(roomId);
        }
        catch(NumberFormatException e)
        {
            throw RoomErrors.roomNotFound();
        }
    }
}
