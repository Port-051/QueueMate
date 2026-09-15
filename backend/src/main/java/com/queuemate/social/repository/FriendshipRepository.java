package com.queuemate.social.repository;

import com.queuemate.social.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.FriendshipId> {

    @Query("""
            SELECT f FROM Friendship f
             WHERE f.id.userLowId = :userId OR f.id.userHighId = :userId
             ORDER BY f.createdAt DESC
            """)
    List<Friendship> findAllOf(@Param("userId") UUID userId);
}
