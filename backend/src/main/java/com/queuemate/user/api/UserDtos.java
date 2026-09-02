package com.queuemate.user.api;

import com.queuemate.common.domain.GameKey;
import com.queuemate.user.domain.GameAccount;
import com.queuemate.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** contracts/openapi.yaml의 UserProfile/GameAccountView와 1:1로 대응한다. */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserProfileResponse(UUID id, String nickname, String avatarUrl) {
        public static UserProfileResponse from(User user) {
            return new UserProfileResponse(user.getId(), user.getNickname(), user.getAvatarUrl());
        }
    }

    public record UpdateUserRequest(
            @Size(min = 2, max = 16) String nickname,
            String avatarUrl
    ) {
    }

    public record GameAccountView(
            UUID id,
            GameKey game,
            String externalGameId,
            String region,
            String rankCode,
            OffsetDateTime verifiedAt
    ) {
        public static GameAccountView from(GameAccount account) {
            return new GameAccountView(
                    account.getId(), account.getProviderGame(), account.getExternalGameId(),
                    account.getRegion(), account.getRankCode(), account.getVerifiedAt());
        }
    }

    public record CreateGameAccountRequest(
            @NotNull GameKey game,
            @NotBlank @Size(max = 128) String externalGameId,
            @Size(max = 20) String region
    ) {
    }
}
