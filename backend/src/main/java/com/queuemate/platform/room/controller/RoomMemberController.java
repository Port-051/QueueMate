package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.HeartbeatResult;
import com.queuemate.platform.room.domain.KickResult;
import com.queuemate.platform.room.domain.LeaveResult;
import com.queuemate.platform.room.domain.RoomMembersResult;
import com.queuemate.platform.room.dto.RoomMembersResponse;
import com.queuemate.platform.room.service.RoomMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 방 안의 사람 — 입장 · 목록 · 나가기 · 강퇴 · 접속 확인. 결과마다의 상태 코드와 에러 코드는 {@code contracts/platform-api.md} 가 원본이다.
 * "나"는 access 토큰에서 온다({@link CurrentUserId} — {@link RoomController} 와 같다).
 *
 * <p>{@code switch} 에 {@code default} 를 두지 않는다 — 결과 enum 에 값이 늘면 컴파일러가 알려 준다.
 */
@RestController
@RequestMapping("/api/v1/rooms/{roomId}/")
@RequiredArgsConstructor
public class RoomMemberController {

    private final RoomMemberService roomMemberService;

    /**
     * 방에 들어온다. <b>입장권이 없다</b>(2026-09-25 2단계 — 소유자 결정 ①: 경로는 그대로다). 두 앱이던 때 입장권 발급이 보던 것(글이 모집 중인가 ·
     * 방 안의 누구와도 차단 관계가 아닌가)을 이 요청 안에서 먼저 본다 — 404 {@code POST_NOT_FOUND} · 409 {@code POST_NOT_RECRUITING}
     * ({@link RoomMemberService#enter} 의 순서). 그 거절은 서비스가 {@code ApiException} 으로 던지므로 아래 {@code switch} 에 없다.
     *
     * <p><b>참가할 때 포지션을 고른다</b>(2026-09-30 소유자 결정 — P-44) — 쿼리 {@code ?position=JUNGLE}(본문이 아니다 — 소유자가 정했다). 포지션을 골라 들어오는 방
     * (그 글에 찾는 포지션이 있다)이면 남은 찾는 포지션 하나가 필수이고, 포지션이 없는 방이면 주지 않는다(주면 400). 이미 들어와 있는 사람의 재입장(200)은
     * {@code position} 을 보지 않는다 — 들어온 뒤에는 바꿀 수 없다. 빈 값({@code ?position=})은 안 준 것과 같다.
     */
    @PostMapping("members")
    public ResponseEntity<Void> enter(@CurrentUserId Long userId, @PathVariable String roomId,
                                      @RequestParam(required = false) String position)
    {
        EnterResult result = roomMemberService.enter(roomId, String.valueOf(userId), position);
        boolean chosen = position != null && !position.isBlank();

        return switch (result) {
            case ENTERED -> ResponseEntity.status(HttpStatus.CREATED).build();
            // 새로고침이나 재시도다. 성공이지만 새로 만든 것은 없다
            case ALREADY_ENTERED -> ResponseEntity.ok().build();
            case ACTIVE_REQUEST_EXISTS -> throw RoomErrors.alreadyQueued("자동 매칭을 돌리는 동안에는 파티방에 들어갈 수 없습니다");
            case FULL -> throw RoomErrors.roomFull();
            case IN_OTHER_ROOM -> throw RoomErrors.inOtherRoom();
            case ROOM_NOT_FOUND -> throw RoomErrors.roomNotFound();
            case ROOM_CONFIRMED -> throw new ApiException(HttpStatus.CONFLICT, "ROOM_CONFIRMED", "이미 확정된 파티방입니다");
            // 강퇴당한 방의 10분 재입장 금지(2026-09-29 소유자 결정). 403 인 것은 방의 상태가 아니라 이 사람에 대한 거절이라서다 —
            // 다른 방에는 그대로 들어갈 수 있고, 시간이 지나면 풀린다(Claude 가 정한 세부 — 계약에 적어야 한다)
            case KICKED_RECENTLY -> throw new ApiException(HttpStatus.FORBIDDEN, "KICKED_RECENTLY",
                    "강퇴당한 파티방에는 10분 동안 다시 들어갈 수 없습니다");
            // 포지션(2026-09-30 소유자 결정 — P-44). 스크립트는 -6 하나로 돌려준다 — 포지션 방인데 안 골랐거나 남은 찾는 포지션에 없다(남이 이미 고른 것도 SET 에서 빠져 있어 여기다) ·
            // 포지션이 없는 방인데 골랐다.
            // 글귀는 Claude 가 정한 세부다(계약 P-44)
            case INVALID_POSITION -> throw ApiException.validationFailed("position",
                    chosen ? "이 파티방에서 고를 수 없는 포지션입니다" : "필요합니다");
            // 지금 스크립트는 -8 을 돌려주지 않는다(2026-09-30 — 고른 포지션을 SET 에서 빼는 방식으로 바꿨다). switch 가 enum 을 다 덮어야 해서 남겨 둔다
            case POSITION_TAKEN -> throw new ApiException(HttpStatus.CONFLICT, "POSITION_TAKEN",
                    "이미 다른 사람이 고른 포지션입니다");
        };
    }

    /**
     * 방 안 사람 목록. 방 안의 사람만 볼 수 있다.
     */
    @GetMapping("members")
    public RoomMembersResponse members(@CurrentUserId Long userId, @PathVariable String roomId)
    {
        RoomMembersResult result = roomMemberService.members(roomId, String.valueOf(userId));

        return switch (result.status()) {
            case FOUND -> new RoomMembersResponse(roomId, result.hostId(), result.members().stream()
                    .map(member -> new RoomMembersResponse.Member(member.userId(), member.position()))
                    .toList());
            // 방 밖의 사람에게는 목록을 보여 주지 않는다. 이미 나갔거나 방이 없어진 경우도 여기로 온다 —
            // 방이 없어질 때 그 방 사람들의 입장 표시가 같이 지워지므로 스크립트의 첫 검사에서 걸린다
            case NOT_IN_ROOM -> throw RoomErrors.notInRoom();
            case ROOM_NOT_FOUND -> throw RoomErrors.roomNotFound();
        };
    }

    /**
     * 방에서 나간다. 방장이 나가면 방이 없어진다(확정한 방은 남은 사람이 방장을 넘겨받는다).
     *
     * <p>셋 다 204 다 — 나가기는 몇 번을 불러도 결과가 같다(이미 나간 사람이 다시 불러도 "나가 있다"가 참이다).
     * 클라이언트가 할 일도 셋 다 같다: 방 화면을 닫는다.
     */
    @DeleteMapping("members/me")
    public ResponseEntity<Void> leave(@CurrentUserId Long userId, @PathVariable String roomId)
    {
        LeaveResult result = roomMemberService.leave(roomId, String.valueOf(userId));

        return switch (result) {
            case LEFT, ROOM_CLOSED, NOT_IN_ROOM -> ResponseEntity.noContent().build();
        };
    }

    /**
     * 강퇴. 방장이 방에 들어와 있는 사람을 내보낸다. 방장만 할 수 있다 — 방장인지는 서비스(스크립트)가 방장 키와 비교해서 안다.
     * 권한 애너테이션을 쓰지 않는다: 방장은 계정의 role 이 아니라 방마다 다른 Redis 의 상태다.
     *
     * <p>나가기의 {@code members/me} 와 경로 모양이 겹치지만 글자 그대로의 경로가 패턴보다 먼저 잡힌다 — {@code me} 는 언제나 나가기다.
     * {@code targetUserId} 는 글자 그대로 받는다 — 숫자가 아니어도 400 이 아니라 멤버 HASH 에 없으니 404 {@code TARGET_NOT_IN_ROOM} 이다(합치기 전과 같다).
     */
    @DeleteMapping("members/{targetUserId}")
    public ResponseEntity<Void> kick(@CurrentUserId Long userId, @PathVariable String roomId,
                                     @PathVariable String targetUserId)
    {
        KickResult result = roomMemberService.kick(roomId, String.valueOf(userId), targetUserId);

        return switch (result) {
            case KICKED -> ResponseEntity.noContent().build();
            case ROOM_NOT_FOUND -> throw RoomErrors.roomNotFound();
            case NOT_HOST -> throw new ApiException(HttpStatus.FORBIDDEN, RoomErrors.NOT_HOST, "방장만 강퇴할 수 있습니다");
            // 방장이 나가려면 나가기를 쓴다 — 그러면 방이 닫힌다
            case CANNOT_KICK_SELF -> throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_KICK_SELF",
                    "자기 자신은 강퇴할 수 없습니다");
            // 그 사이에 나갔을 수 있다. 에러 코드는 시그널의 것과 같다
            case TARGET_NOT_IN_ROOM -> throw new ApiException(HttpStatus.NOT_FOUND, RoomErrors.TARGET_NOT_IN_ROOM,
                    "이 파티방에 없는 사용자입니다");
        };
    }

    /**
     * 접속 확인. 브라우저가 1분마다 부른다 — "나 아직 이 방에 있다". SSE 의 heartbeat(서버 → 브라우저)와는 다른 것이다.
     *
     * <p>204 면 계속 있으면 되고, 4xx 면 방 화면을 닫는다. 상태 코드만 보고 정할 수 있게 갈랐다.
     */
    @PostMapping("heartbeat")
    public ResponseEntity<Void> heartbeat(@CurrentUserId Long userId, @PathVariable String roomId)
    {
        HeartbeatResult result = roomMemberService.heartbeat(roomId, String.valueOf(userId));

        return switch (result) {
            case ALIVE -> ResponseEntity.noContent().build();
            // 신호가 끊겨 있던 동안 수명이 다해 빠졌을 수 있다
            case NOT_IN_ROOM -> throw RoomErrors.notInRoom();
            // 방장의 신호가 끊겨 방의 수명이 다했다. 에러 코드는 입장의 것과 같다 — 클라이언트는 code 로 갈래를 정한다
            case ROOM_CLOSED -> throw RoomErrors.roomNotFound();
        };
    }
}
