package com.queuemate.platform.social.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.social.dto.RecentPlayerListResponse;
import com.queuemate.platform.social.service.RecentPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최근 함께한 사람 — <b>내 것만, 읽기만.</b> 원본은 {@code contracts/platform-api.md} 다 */
@RestController
@RequestMapping("/api/v1/recent-players")
@RequiredArgsConstructor
public class RecentPlayerController {

    private final RecentPlayerService recentPlayerService;

    @GetMapping
    public RecentPlayerListResponse list(@CurrentUserId Long userId)
    {
        return recentPlayerService.list(userId);
    }
}
