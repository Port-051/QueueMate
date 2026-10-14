package com.queuemate.platform.account.dto;

import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.domain.SocialProvider;
import com.queuemate.platform.account.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET · PATCH /users/me} 의 응답. {@code createdAt} 은 ISO-8601 UTC 로 나간다.
 *
 * @param userId          사용자 번호
 * @param email           소셜 제공자가 검증했다고 말한 이메일(2026-10-13 · P-58). <b>읽기 전용</b> — 없으면 {@code null}. 식별자가 아니다
 * @param socialProviders 이 계정에 이어진 소셜 제공자의 이름(대문자 — {@code KAKAO} · {@code DISCORD} · {@code GOOGLE}). 없으면 {@code []}
 * @param gameAccounts    게임 프로필의 목록. 게임 이름순이다
 */
public record UserResponse(
        Long userId,
        String nickname,
        String email,
        Instant createdAt,
        List<String> socialProviders,
        List<GameProfileResponse> gameAccounts
) {
    public static UserResponse of(User user, List<SocialProvider> socialProviders,
                                  List<GameAccountWithStats> gameAccounts)
    {
        return new UserResponse(user.getId(), user.getNickname(), user.getEmail(), user.getCreatedAt(),
                socialProviders.stream().map(SocialProvider::name).sorted().toList(),
                gameAccounts.stream().map(GameProfileResponse::from).toList());
    }
}
