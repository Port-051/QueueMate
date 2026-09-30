package com.queuemate.platform.social.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.social.dto.FriendListResponse;
import com.queuemate.platform.social.service.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 친구 — <b>내 친구만</b> 본다. 남의 친구 목록을 보는 길이 없다({@link CurrentUserId}).
 * 친구를 만드는 것은 친구 요청의 수락이다({@link FriendRequestController}). 원본은 {@code contracts/platform-api.md} 다.
 */
@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @GetMapping
    public FriendListResponse list(@CurrentUserId Long userId)
    {
        return friendService.listFriends(userId);
    }

    /** 친구가 아니어도 · 없는 사용자여도 204 다 — 끊기는 멱등이다. 경로의 {@code userId} 가 숫자가 아니면 400 이다 */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unfriend(@CurrentUserId Long userId, @PathVariable("userId") Long otherUserId)
    {
        friendService.unfriend(userId, otherUserId);
        return ResponseEntity.noContent().build();
    }
}
