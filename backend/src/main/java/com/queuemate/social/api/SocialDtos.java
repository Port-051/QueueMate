package com.queuemate.social.api;

import com.queuemate.social.domain.FriendRequestStatus;
import com.queuemate.social.domain.ReportReason;
import com.queuemate.social.service.RecentPlayerService.RecentPlayer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/** contracts/openapi.yaml의 소셜 스키마와 1:1로 대응한다. */
public final class SocialDtos {

    private SocialDtos() {
    }

    public enum Direction {
        RECEIVED,
        SENT
    }

    public record FriendView(UUID userId, String nickname, String avatarUrl, OffsetDateTime friendedAt) {
    }

    public record FriendRequestView(
            UUID id,
            Direction direction,
            UUID counterpartUserId,
            String counterpartNickname,
            FriendRequestStatus status,
            OffsetDateTime createdAt
    ) {
    }

    public record CreateFriendRequest(@NotNull UUID targetUserId) {
    }

    public record BlockView(UUID userId, String nickname, OffsetDateTime blockedAt) {
    }

    public record CreateBlockRequest(@NotNull UUID targetUserId) {
    }

    public record RecentPlayerView(
            UUID userId,
            String nickname,
            String avatarUrl,
            OffsetDateTime lastPlayedAt,
            int playCount,
            boolean friend
    ) {
        public static RecentPlayerView from(RecentPlayer player) {
            return new RecentPlayerView(player.userId(), player.nickname(), player.avatarUrl(),
                    player.lastPlayedAt(), player.playCount(), player.friend());
        }
    }

    public record CreateReportRequest(
            @NotNull UUID targetUserId,
            @NotNull ReportReason reason,
            @Size(max = 1000) String description,
            UUID partyId
    ) {
    }
}
