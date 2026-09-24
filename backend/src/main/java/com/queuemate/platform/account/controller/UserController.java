package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.dto.GameAccountRequest;
import com.queuemate.platform.account.dto.GameProfileResponse;
import com.queuemate.platform.account.dto.NicknameChangeRequest;
import com.queuemate.platform.account.dto.UserResponse;
import com.queuemate.platform.account.service.UserService;
import com.queuemate.platform.common.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 프로필과 게임 계정. <b>"나"는 경로가 아니라 access 토큰에서 온다</b>({@link CurrentUserId}) — 경로에 사용자 번호를 받지 않으므로
 * 남의 것을 건드릴 길이 없다. 원본은 {@code contracts/platform-api.md} "계정" 이다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse me(@CurrentUserId Long userId)
    {
        return userService.me(userId);
    }

    @PatchMapping
    public UserResponse changeNickname(@CurrentUserId Long userId, @Valid @RequestBody NicknameChangeRequest request)
    {
        return userService.changeNickname(userId, request.nickname());
    }

    /**
     * {@code game} 을 enum 이 아니라 문자열로 받는다 — 모르는 게임을 서비스가 {@code details} 에 {@code "game: 사유"} 를 담아 400 으로 답한다.
     * 응답은 <b>게임 프로필</b>이다({@code verified} · {@code stats} 포함 — 둘은 요청으로 바꿀 수 없다).
     */
    @PutMapping("/game-accounts/{game}")
    public GameProfileResponse putGameAccount(@CurrentUserId Long userId, @PathVariable String game,
                                              @Valid @RequestBody GameAccountRequest request)
    {
        return userService.putGameAccount(userId, game, request);
    }

    /**
     * <b>전적 갱신</b> — 그 게임 계정의 전적을 게임사 API 에서 <b>지금 다시 받아 온다</b>(2026-09-24 소유자 결정).
     * <b>다 긁을 때까지 기다린다</b>(최대 30초) — 응답은 {@code PUT} 과 <b>같은 게임 프로필</b>이라 프런트가 그대로 갈아 끼운다.
     *
     * <p>같은 게임 계정은 <b>2분에 한 번</b>이다 — 그 안에 또 부르면 429 {@code TOO_MANY_STATS_REFRESHES} + {@code Retry-After} 다.
     * 거절의 갈래는 {@code stats.GameStatsRefresher} 가 정한다.
     */
    @PostMapping("/game-accounts/{game}/refresh")
    public GameProfileResponse refreshGameStats(@CurrentUserId Long userId, @PathVariable String game)
    {
        return userService.refreshGameStats(userId, game);
    }

    @DeleteMapping("/game-accounts/{game}")
    public ResponseEntity<Void> deleteGameAccount(@CurrentUserId Long userId, @PathVariable String game)
    {
        userService.deleteGameAccount(userId, game);
        return ResponseEntity.noContent().build();
    }
}
