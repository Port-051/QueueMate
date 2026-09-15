package com.queuemate.social.repository;

import com.queuemate.social.domain.FriendRequest;
import com.queuemate.social.domain.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, UUID> {

    Optional<FriendRequest> findBySenderIdAndReceiverIdAndStatus(
            UUID senderId, UUID receiverId, FriendRequestStatus status);

    List<FriendRequest> findAllByReceiverIdAndStatusOrderByCreatedAtDesc(
            UUID receiverId, FriendRequestStatus status);

    List<FriendRequest> findAllBySenderIdAndStatusOrderByCreatedAtDesc(
            UUID senderId, FriendRequestStatus status);

    /** 차단 시 두 사람 사이의 대기 중 요청을 방향 상관없이 정리한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE FriendRequest r
               SET r.status = com.queuemate.social.domain.FriendRequestStatus.CANCELLED,
                   r.respondedAt = CURRENT_TIMESTAMP
             WHERE r.status = com.queuemate.social.domain.FriendRequestStatus.PENDING
               AND ((r.senderId = :one AND r.receiverId = :other)
                 OR (r.senderId = :other AND r.receiverId = :one))
            """)
    int cancelPendingBetween(@Param("one") UUID one, @Param("other") UUID other);
}
