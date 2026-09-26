package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.FriendRequest;
import com.queuemate.platform.social.domain.FriendRequestRow;
import com.queuemate.platform.social.domain.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 친구 요청. <b>"이미 보냈는지" 먼저 조회하고 넣지 마라</b> — 중복은 {@code saveAndFlush} 의 partial unique index 위반
 * ({@code friend_requests_one_pending})으로 안다({@code FriendService#send}).
 *
 * <p><b>상태를 바꾸는 것은 전부 조건부 UPDATE 다</b> — {@code WHERE … AND status = 'PENDING'} 이 "아직 대기 중일 때만"을 DB 에서 지킨다.
 * 같은 요청을 두 번 수락해도(동시에 와도) 한 번만 1 을 돌려준다 — 뒤에 온 쪽은 앞의 커밋을 기다렸다가 조건을 다시 보고 0 이 된다.
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /** 요청 한 줄 + 보낸 사람 · 받은 사람의 닉네임. 사용자 번호에 FK 가 있어 INNER JOIN 으로 빠지는 줄이 없다 */
    String ROW_SELECT = """
            select new com.queuemate.platform.social.domain.FriendRequestRow(
                       r.id, r.requesterId, requester.nickname, r.receiverId, receiver.nickname, r.createdAt)
              from FriendRequest r
              join User requester on requester.id = r.requesterId
              join User receiver on receiver.id = r.receiverId
            """;

    /** 내가 받은 대기 중 요청 — 새것이 먼저. 양쪽 닉네임까지 <b>쿼리 한 번이다</b>({@code users} 를 두 번 JOIN) */
    @Query(ROW_SELECT + """
             where r.receiverId = :me
               and r.status = com.queuemate.platform.social.domain.FriendRequestStatus.PENDING
             order by r.createdAt desc, r.id desc
            """)
    List<FriendRequestRow> findPendingReceivedRows(@Param("me") Long me);

    /** 내가 보낸 대기 중 요청 — 새것이 먼저. 양쪽 닉네임까지 쿼리 한 번이다 */
    @Query(ROW_SELECT + """
             where r.requesterId = :me
               and r.status = com.queuemate.platform.social.domain.FriendRequestStatus.PENDING
             order by r.createdAt desc, r.id desc
            """)
    List<FriendRequestRow> findPendingSentRows(@Param("me") Long me);

    /** 요청 한 건을 양쪽 닉네임과 같이 — 방금 넣은 요청의 응답을 만든다 */
    @Query(ROW_SELECT + " where r.id = :id")
    Optional<FriendRequestRow> findRowById(@Param("id") Long id);

    /** 친절한 에러({@code FRIEND_REQUEST_ALREADY_RECEIVED})를 위한 조회다 — 이것이 빠지거나 경쟁에 져도 데이터는 깨지지 않는다 */
    boolean existsByRequesterIdAndReceiverIdAndStatus(Long requesterId, Long receiverId, FriendRequestStatus status);

    /**
     * 받은 사람이 응답한다(수락 · 거절). <b>내가 받은 것이고 아직 대기 중일 때만</b> 바뀐다. 돌려주는 값이 1 이면 이 호출이 바꾼 것이다 —
     * 0 이면 없는 요청이거나 · 남의 요청이거나 · 이미 처리됐다(부른 쪽이 셋을 가르지 않고 404 로 답한다).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FriendRequest r
               set r.status = :status, r.respondedAt = :now
             where r.id = :id
               and r.receiverId = :receiverId
               and r.status = com.queuemate.platform.social.domain.FriendRequestStatus.PENDING
            """)
    int respondIfPending(@Param("id") Long id, @Param("receiverId") Long receiverId,
                         @Param("status") FriendRequestStatus status, @Param("now") Instant now);

    /** 보낸 사람이 거둔다. <b>내가 보낸 것이고 아직 대기 중일 때만</b> 바뀐다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FriendRequest r
               set r.status = com.queuemate.platform.social.domain.FriendRequestStatus.CANCELED, r.respondedAt = :now
             where r.id = :id
               and r.requesterId = :requesterId
               and r.status = com.queuemate.platform.social.domain.FriendRequestStatus.PENDING
            """)
    int cancelIfPending(@Param("id") Long id, @Param("requesterId") Long requesterId, @Param("now") Instant now);

    /**
     * 그 방향의 대기 중 요청을 {@code ACCEPTED} 로 닫는다 — 수락할 때 <b>반대 방향</b>의 요청을 같은 트랜잭션에서 닫는 데 쓴다.
     * 서로 동시에 요청을 보내면 양방향 PENDING 이 둘 생길 수 있다 — 하나만 닫으면 이미 친구인 사람의 요청이 목록에 남는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update FriendRequest r
               set r.status = com.queuemate.platform.social.domain.FriendRequestStatus.ACCEPTED, r.respondedAt = :now
             where r.requesterId = :requesterId
               and r.receiverId = :receiverId
               and r.status = com.queuemate.platform.social.domain.FriendRequestStatus.PENDING
            """)
    int acceptPendingBetween(@Param("requesterId") Long requesterId, @Param("receiverId") Long receiverId,
                             @Param("now") Instant now);
}
