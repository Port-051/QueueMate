package com.queuemate.social.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.social.api.SocialDtos.RecentPlayerView;
import com.queuemate.social.service.RecentPlayerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recent-players")
@Validated
public class RecentPlayerController {

    private final RecentPlayerService recentPlayerService;

    public RecentPlayerController(RecentPlayerService recentPlayerService) {
        this.recentPlayerService = recentPlayerService;
    }

    @GetMapping
    public List<RecentPlayerView> recentPlayers(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return recentPlayerService.recentPlayers(currentUser.userId(), limit).stream()
                .map(RecentPlayerView::from)
                .toList();
    }
}
