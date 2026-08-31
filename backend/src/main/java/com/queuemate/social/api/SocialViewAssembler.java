package com.queuemate.social.api;

import com.queuemate.social.api.SocialDtos.BlockView;
import com.queuemate.social.api.SocialDtos.Direction;
import com.queuemate.social.api.SocialDtos.FriendRequestView;
import com.queuemate.social.api.SocialDtos.FriendView;
import com.queuemate.social.domain.Block;
import com.queuemate.social.domain.FriendRequest;
import com.queuemate.social.domain.Friendship;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 상대편 프로필을 한 번에 읽어 N+1 조회를 피한다. */
@Component
public class SocialViewAssembler {

    private final UserRepository users;

    public SocialViewAssembler(UserRepository users) {
        this.users = users;
    }

    public List<FriendView> toFriendViews(UUID userId, List<Friendship> friendships) {
        Map<UUID, User> profiles = profilesOf(friendships.stream()
                .map(friendship -> friendship.counterpartOf(userId))
                .collect(Collectors.toSet()));
        return friendships.stream()
                .map(friendship -> {
                    UUID counterpartId = friendship.counterpartOf(userId);
                    User profile = profiles.get(counterpartId);
                    return new FriendView(
                            counterpartId,
                            profile == null ? null : profile.getNickname(),
                            profile == null ? null : profile.getAvatarUrl(),
                            friendship.getCreatedAt());
                })
                .toList();
    }

    public FriendView toFriendView(UUID userId, Friendship friendship) {
        return toFriendViews(userId, List.of(friendship)).getFirst();
    }

    public List<FriendRequestView> toRequestViews(List<FriendRequest> requests, Direction direction) {
        Map<UUID, User> profiles = profilesOf(requests.stream()
                .map(request -> counterpartOf(request, direction))
                .collect(Collectors.toSet()));
        return requests.stream()
                .map(request -> {
                    UUID counterpartId = counterpartOf(request, direction);
                    User profile = profiles.get(counterpartId);
                    return new FriendRequestView(
                            request.getId(),
                            direction,
                            counterpartId,
                            profile == null ? null : profile.getNickname(),
                            request.getStatus(),
                            request.getCreatedAt());
                })
                .toList();
    }

    public List<BlockView> toBlockViews(List<Block> blocks) {
        Map<UUID, User> profiles = profilesOf(blocks.stream()
                .map(block -> block.getId().blockedId())
                .collect(Collectors.toSet()));
        return blocks.stream()
                .map(block -> {
                    User profile = profiles.get(block.getId().blockedId());
                    return new BlockView(
                            block.getId().blockedId(),
                            profile == null ? null : profile.getNickname(),
                            block.getCreatedAt());
                })
                .toList();
    }

    public BlockView toBlockView(Block block) {
        return toBlockViews(List.of(block)).getFirst();
    }

    private UUID counterpartOf(FriendRequest request, Direction direction) {
        return direction == Direction.RECEIVED ? request.getSenderId() : request.getReceiverId();
    }

    private Map<UUID, User> profilesOf(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
