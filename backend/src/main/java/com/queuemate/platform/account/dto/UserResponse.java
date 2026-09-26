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
 * @param socialProviders 이 계정에 이어진 소셜 제공자의 이름(대문자 — {@code KAKAO} · {@code DISCORD}). 없으면 {@code []}
 * @param gameAccounts    게임 프로필의 목록. 게임 이름순이다
 */
public record UserResponse(
        Long userId,
        String nickname,
        Instant createdAt,
        List<String> socialProviders,
        List<GameProfileResponse> gameAccounts
) {
    public static UserResponse of(User user, List<SocialProvider> socialProviders,
                                  List<GameAccountWithStats> gameAccounts)
    {
        return new UserResponse(user.getId(), user.getNickname(), user.getCreatedAt(),
                socialProviders.stream().map(SocialProvider::name).sorted().toList(),
                gameAccounts.stream().map(GameProfileResponse::from).toList());
    }
}
