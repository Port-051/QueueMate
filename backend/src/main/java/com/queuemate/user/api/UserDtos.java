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

    /**
     * 프로필 부분 수정.
     *
     * <p>record가 아니라 클래스인 이유가 있다. record로 받으면 "필드를 안 보낸 것"과
     * "null을 보낸 것"이 둘 다 null로 들어와 구분되지 않는다. 그러면 아바타를 지울
     * 방법이 없다. Jackson은 JSON에 있는 키에 대해서만 setter를 부르므로,
     * 호출 여부를 기록해 두면 두 경우를 가를 수 있다.
     *
     * <ul>
     *   <li>키 없음 → 그 항목은 건드리지 않는다</li>
     *   <li>{@code "avatarUrl": null} → 아바타를 지운다</li>
     * </ul>
     */
    public static final class UpdateUserRequest {

        @Size(min = 2, max = 16)
        private String nickname;
        private boolean nicknamePresent;

        private String avatarUrl;
        private boolean avatarUrlPresent;

        public String nickname() {
            return nickname;
        }

        public boolean nicknamePresent() {
            return nicknamePresent;
        }

        public String avatarUrl() {
            return avatarUrl;
        }

        public boolean avatarUrlPresent() {
            return avatarUrlPresent;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
            this.nicknamePresent = true;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            this.avatarUrlPresent = true;
        }
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
