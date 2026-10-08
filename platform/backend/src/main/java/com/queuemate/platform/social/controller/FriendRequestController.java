package com.queuemate.platform.social.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.social.dto.FriendRequestCreateRequest;
import com.queuemate.platform.social.dto.FriendRequestDirection;
import com.queuemate.platform.social.dto.FriendRequestListResponse;
import com.queuemate.platform.social.dto.FriendRequestResponse;
import com.queuemate.platform.social.dto.FriendResponse;
import com.queuemate.platform.social.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 친구 요청. <b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}) — 남의 이름으로 보내거나 남이 받은 요청을 수락할 길이 없다.
 * 원본은 {@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람" 이다.
 *
 * <p><b>여기에 사람을 찾는 요청(아이디 · 닉네임 검색, 사용자 목록)을 붙이지 마라</b> — 상대의 사용자 번호는 방 안 사람 카드 등에서 이미 아는 값이다. — 공개 사용자 탐색 금지다(CLAUDE.md §1).
 */
@RestController
@RequestMapping("/api/v1/friend-requests")
@RequiredArgsConstructor
public class FriendRequestController {

    private final FriendService friendService;

    @PostMapping
    public ResponseEntity<FriendRequestResponse> send(@CurrentUserId Long userId,
                                                      @Valid @RequestBody FriendRequestCreateRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(friendService.send(userId, request.userId()));
    }

    /**
     * 대기 중인 것만. {@code direction} 은 {@code RECEIVED}(기본) · {@code SENT} — 대문자 그대로다. 그 밖의 값(소문자 포함)은 스프링의 enum 변환이
     * 거절해 400 이다({@code GlobalExceptionHandler#handleTypeMismatch} — 게시판 목록의 {@code game} 과 같은 본문).
     */
    @GetMapping
    public FriendRequestListResponse list(@CurrentUserId Long userId,
                                          @RequestParam(name = "direction", defaultValue = "RECEIVED") FriendRequestDirection direction)
    {
        return friendService.listPending(userId, direction == FriendRequestDirection.RECEIVED);
    }

    /** 받은 사람이 수락한다. 응답은 새 친구다. {@code requestId} 가 숫자가 아니면 400 이다 */
    @PostMapping("/{requestId}/accept")
    public FriendResponse accept(@CurrentUserId Long userId, @PathVariable("requestId") Long requestId)
    {
        return friendService.accept(userId, requestId);
    }

    @PostMapping("/{requestId}/decline")
    public ResponseEntity<Void> decline(@CurrentUserId Long userId, @PathVariable("requestId") Long requestId)
    {
        friendService.decline(userId, requestId);
        return ResponseEntity.noContent().build();
    }

    /** 보낸 사람이 거둔다 */
    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancel(@CurrentUserId Long userId, @PathVariable("requestId") Long requestId)
    {
        friendService.cancel(userId, requestId);
        return ResponseEntity.noContent().build();
    }
}
