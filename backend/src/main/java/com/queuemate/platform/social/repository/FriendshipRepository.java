package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.Friendship;
import com.queuemate.platform.social.dto.FriendResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 친구. <b>두 사용자 번호는 늘 {@code Friendship.Key#of} 로 정규화해서 넘긴다</b>(작은 쪽, 큰 쪽) — 여기서는 순서를 다시 보지 않는다.
 * 순서가 뒤집혀 오면 DB 의 {@code friendships_ordered} CHECK 가 거절한다.
 */
public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.Key> {

    /** 친구 한 줄 + 상대(나가 아닌 쪽)의 번호 · 닉네임 · 친구가 된 시각. 사용자 번호에 FK 가 있어 INNER JOIN 으로 빠지는 줄이 없다 */
    String FRIEND_SELECT = """
            select new com.queuemate.platform.social.dto.FriendResponse(u.id, u.nickname, f.createdAt)
              from Friendship f
              join User u on (u.id = f.key.userLowId or u.id = f.key.userHighId) and u.id <> :me
             where (f.key.userLowId = :me or f.key.userHighId = :me)
            """;

    /**
     * 친구 한 줄을 넣는다. 돌려주는 값이 0 이면 이미 친구였다. <b>{@code ON CONFLICT DO NOTHING} 이다</b> — 제약 위반을 예외로 받으면
     * PostgreSQL 이 그 트랜잭션을 더 쓸 수 없게 만드는데, 같은 트랜잭션에서 요청의 상태도 바꾸므로 위반 없이 지나가야 한다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO friendships (user_low_id, user_high_id, created_at)
            VALUES (:lowId, :highId, :now)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("lowId") Long lowId, @Param("highId") Long highId, @Param("now") Instant now);

    /**
     * 내 친구 전부 — 내가 어느 칸에 있든 찾고, 상대의 닉네임을 JOIN 으로 붙여 <b>쿼리 한 번</b>에 읽는다.
     * <b>닉네임순</b>(대소문자를 가리지 않는다 — {@code lower}. 같으면 사용자 번호순)으로 DB 가 정렬한다.
     */
    @Query(FRIEND_SELECT + " order by lower(u.nickname), u.id")
    List<FriendResponse> findFriendsOf(@Param("me") Long me);

    /** 나와 그 사람의 친구 한 줄을 응답 모양으로 — 수락의 응답을 만든다. 친구가 아니면 비어 있다 */
    @Query(FRIEND_SELECT + " and u.id = :other")
    Optional<FriendResponse> findFriend(@Param("me") Long me, @Param("other") Long other);

    /** 없어도 에러가 아니다 — 지운 줄 수를 돌려준다 */
    @Modifying
    @Query("delete from Friendship f where f.key.userLowId = :lowId and f.key.userHighId = :highId")
    int deleteByLowAndHigh(@Param("lowId") Long lowId, @Param("highId") Long highId);
}
