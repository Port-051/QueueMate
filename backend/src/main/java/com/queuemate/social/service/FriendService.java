package com.queuemate.social.service;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.common.social.BlockLookupPort;
import com.queuemate.social.domain.FriendRequest;
import com.queuemate.social.domain.FriendRequestStatus;
import com.queuemate.social.domain.Friendship;
import com.queuemate.social.repository.FriendRequestRepository;
import com.queuemate.social.repository.FriendshipRepository;
import com.queuemate.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FriendService {

    private final FriendRequestRepository friendRequests;
    private final FriendshipRepository friendships;
    private final UserRepository users;
    private final BlockLookupPort blockLookup;

    public FriendService(FriendRequestRepository friendRequests, FriendshipRepository friendships,
                         UserRepository users, BlockLookupPort blockLookup) {
        this.friendRequests = friendRequests;
        this.friendships = friendships;
        this.users = users;
        this.blockLookup = blockLookup;
    }

    @Transactional
    public FriendRequest request(UUID senderId, UUID targetId) {
        if (senderId.equals(targetId)) {
            throw new ConflictException("SELF_FRIEND_REQUEST", "자기 자신에게 친구 요청할 수 없다");
        }
        if (!users.existsById(targetId)) {
            throw new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없다");
        }
        // 차단은 방향과 무관하다. 차단당한 쪽에서도 요청을 보낼 수 없다.
        if (blockLookup.anyBlockBetween(List.of(senderId, targetId))) {
            throw new ConflictException("BLOCKED_RELATION", "차단 관계인 사용자다");
        }
        if (friendships.existsById(Friendship.FriendshipId.of(senderId, targetId))) {
            throw new ConflictException("ALREADY_FRIENDS", "이미 친구다");
        }
        // 상대가 이미 보낸 요청이 있으면 새 요청 대신 그대로 수락하게 한다.
        friendRequests.findBySenderIdAndReceiverIdAndStatus(
                        targetId, senderId, FriendRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "INVERSE_REQUEST_PENDING", "상대가 보낸 요청이 대기 중이다");
                });
        try {
            return friendRequests.saveAndFlush(FriendRequest.create(senderId, targetId));
        } catch (DataIntegrityViolationException e) {
            // friend_requests_one_pending_idx 위반. 동시 요청 경합이다.
            throw new ConflictException("REQUEST_ALREADY_PENDING", "이미 대기 중인 요청이 있다");
        }
    }

    @Transactional
    public Friendship accept(UUID receiverId, UUID requestId) {
        FriendRequest request = requireReceivedRequest(receiverId, requestId);
        if (blockLookup.anyBlockBetween(List.of(request.getSenderId(), receiverId))) {
            throw new ConflictException("BLOCKED_RELATION", "차단 관계인 사용자다");
        }
        request.transitionTo(FriendRequestStatus.ACCEPTED);
        friendRequests.saveAndFlush(request);
        return friendships.saveAndFlush(Friendship.of(request.getSenderId(), receiverId));
    }

    @Transactional
    public void decline(UUID receiverId, UUID requestId) {
        FriendRequest request = requireReceivedRequest(receiverId, requestId);
        request.transitionTo(FriendRequestStatus.DECLINED);
        friendRequests.saveAndFlush(request);
    }

    @Transactional
    public void cancel(UUID senderId, UUID requestId) {
        FriendRequest request = friendRequests.findById(requestId)
                .filter(candidate -> candidate.getSenderId().equals(senderId))
                .orElseThrow(() -> new NotFoundException(
                        "FRIEND_REQUEST_NOT_FOUND", "친구 요청을 찾을 수 없다"));
        request.transitionTo(FriendRequestStatus.CANCELLED);
        friendRequests.saveAndFlush(request);
    }

    @Transactional
    public void remove(UUID userId, UUID friendUserId) {
        Friendship.FriendshipId id = Friendship.FriendshipId.of(userId, friendUserId);
        if (!friendships.existsById(id)) {
            throw new NotFoundException("FRIENDSHIP_NOT_FOUND", "친구가 아니다");
        }
        friendships.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Friendship> listFriends(UUID userId) {
        return friendships.findAllOf(userId);
    }

    @Transactional(readOnly = true)
    public List<FriendRequest> listPending(UUID userId, boolean received) {
        return received
                ? friendRequests.findAllByReceiverIdAndStatusOrderByCreatedAtDesc(
                        userId, FriendRequestStatus.PENDING)
                : friendRequests.findAllBySenderIdAndStatusOrderByCreatedAtDesc(
                        userId, FriendRequestStatus.PENDING);
    }

    /** 남의 요청을 처리할 수 없도록 수신자까지 함께 확인한다 (docs/13 Authorization). */
    private FriendRequest requireReceivedRequest(UUID receiverId, UUID requestId) {
        return friendRequests.findById(requestId)
                .filter(request -> request.getReceiverId().equals(receiverId))
                .orElseThrow(() -> new NotFoundException(
                        "FRIEND_REQUEST_NOT_FOUND", "친구 요청을 찾을 수 없다"));
    }
}
