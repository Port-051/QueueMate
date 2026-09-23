package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 친구. <b>두 사용자 번호는 늘 {@code Friendship.Key#of} 로 정규화해서 넘긴다</b>(작은 쪽, 큰 쪽) — 여기서는 순서를 다시 보지 않는다.
 * 순서가 뒤집혀 오면 DB 의 {@code friendships_ordered} CHECK 가 거절한다.
 */
public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.Key> {

    /**
     * 친구 한 줄을 넣는다. 돌려주는 값이 0 이면 이미 친구였다. <b>{@code ON CONFLICT DO NOTHING} 이다</b> — 제약 위반을 예외로 받으면
     * PostgreSQL 이 그 트랜잭션을 더 쓸 수 없게 만드는데, 같은 트랜잭션에서 요청의 상태도 바꾸므로 위반 없이 지나가야 한다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO social.friendships (user_low_id, user_high_id, created_at)
            VALUES (:lowId, :highId, :now)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("lowId") Long lowId, @Param("highId") Long highId, @Param("now") Instant now);

    /** 내가 어느 칸에 있든 찾는다. 정렬은 부른 쪽이 한다 — 닉네임순인데 닉네임은 {@code account} 의 것이라 여기서 JOIN 하지 않는다 */
    @Query("select f from Friendship f where f.key.userLowId = :me or f.key.userHighId = :me")
    List<Friendship> findAllOf(@Param("me") Long me);

    /** 없어도 에러가 아니다 — 지운 줄 수를 돌려준다 */
    @Modifying
    @Query("delete from Friendship f where f.key.userLowId = :lowId and f.key.userHighId = :highId")
    int deleteByLowAndHigh(@Param("lowId") Long lowId, @Param("highId") Long highId);
}
