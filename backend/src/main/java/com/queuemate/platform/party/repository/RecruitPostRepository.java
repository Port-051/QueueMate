package com.queuemate.platform.party.repository;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.party.domain.RecruitPost;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <b>"모집 중인 글이 이미 있는지" 먼저 조회하고 넣지 마라</b> — 확인과 INSERT 사이에 같은 요청이 끼어든다. 중복은 {@code saveAndFlush} 의
 * 부분 UNIQUE 인덱스 위반({@code recruit_posts_one_recruiting_per_host})으로 안다 ({@code PostStore#create}).
 *
 * <p><b>상태를 바꾸는 것은 전부 조건부 UPDATE 다</b>({@code … and p.status = RECRUITING}) — 만료 · 확정 · 방장의 삭제가 동시에 와도
 * 한 번만 바뀌고, 바뀐 줄 수(0 또는 1)로 "내가 바꿨는가"를 안다. 읽어서 판단한 뒤 저장하지 않는다.
 *
 * <p>UPDATE 문은 영속성 컨텍스트를 거치지 않는다 — 그래서 실행한 뒤 컨텍스트를 비운다({@code clearAutomatically}). 같은 트랜잭션에서
 * 그 글을 다시 읽으면 메모리의 옛 상태가 아니라 DB 의 지금 상태가 온다.
 */
public interface RecruitPostRepository extends JpaRepository<RecruitPost, Long> {

    /**
     * 게시판 목록의 <b>첫 페이지</b>(세 게임 전부) — 모집 중인 글 전부와, 만료 · 확정된 지 {@code closedAfter} 가 안 지난 글.
     * <b>정렬은 {@code id} 내림차순 하나 = 최신순이다</b>(2026-09-24 소유자 결정 — {@code id} 가 identity 라 넣은 순서대로 커진다).
     * 찾는 포지션은 쿼리 한 번으로 같이 온다({@code RecruitPost#wantedPositions}).
     *
     * <p><b>정렬에 글의 상태도 {@code createdAt} 도 쓰지 않는다</b>(옛 정렬은 모집 중인 글을 앞으로 당기고 그 뒤에 {@code createdAt} 을 봤다).
     * 커서는 정렬 키가 <b>변하지 않는다</b>는 전제 위에 서는데 상태는 변하고 이 목록 조회 자신이 바꾼다({@code BoardCursor} 의 주석).
     *
     * <p><b>{@code limit} 이 있다</b>(2026-09-23 소유자 결정) — 게시판은 신호가 올 때마다 다시 받으므로 전부 내려 주면 그 큰 응답이 되풀이된다.
     * 다음 줄이 있는지는 부르는 쪽이 <b>한 개 더 읽어</b> 안다({@code PostService#list}).
     */
    @Query("""
            select p
              from RecruitPost p
             where (p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
                    or p.confirmedAt > :closedAfter
                    or p.expiredAt > :closedAfter)
             order by p.id desc
            """)
    List<RecruitPost> findBoard(@Param("closedAfter") Instant closedAfter, Limit limit);

    /** {@link #findBoard} 와 같고 한 게임만이다. 둘로 나눈 이유 — {@code :game is null or …} 은 PostgreSQL 이 null 파라미터의 자료형을 못 정할 수 있다 */
    @Query("""
            select p
              from RecruitPost p
             where p.game = :game
               and (p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
                    or p.confirmedAt > :closedAfter
                    or p.expiredAt > :closedAfter)
             order by p.id desc
            """)
    List<RecruitPost> findBoardByGame(@Param("game") Game game, @Param("closedAfter") Instant closedAfter, Limit limit);

    /**
     * {@link #findBoard} 의 <b>다음 페이지</b> — 커서({@code BoardCursor})가 가리키는 줄 <b>다음</b>부터다.
     *
     * <p>조건은 <b>{@code p.id < :postId} 한 줄이다</b> — 정렬이 {@code id desc} 이므로 "그 뒤" 는 번호가 <b>작은</b> 글이다
     * (내림차순이라 부등호가 {@code <} 다 — 오름차순으로 착각해 {@code >} 를 쓰면 페이지가 거꾸로 걸린다).
     * 번호가 겹치지 않아 {@code id} 하나로 충분하다 — 옛 정렬처럼 시각을 같이 보면 같은 시각의 글에서 건너뛰거나 두 번 보여 줄 위험을 스스로 만든다.
     *
     * <p>{@code offset} 이 아닌 이유 — 게시판은 1페이지를 보는 동안에도 글이 올라온다. {@code offset} 이면 그만큼 줄이 밀려
     * 2페이지에 같은 글이 또 나오거나 사이의 글이 빠진다. 커서는 값을 기준으로 잘라서 그런 일이 없다.
     */
    @Query("""
            select p
              from RecruitPost p
             where (p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
                    or p.confirmedAt > :closedAfter
                    or p.expiredAt > :closedAfter)
               and p.id < :postId
             order by p.id desc
            """)
    List<RecruitPost> findBoardAfter(@Param("closedAfter") Instant closedAfter, @Param("postId") long postId, Limit limit);

    /** {@link #findBoardAfter} 와 같고 한 게임만이다 */
    @Query("""
            select p
              from RecruitPost p
             where p.game = :game
               and (p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
                    or p.confirmedAt > :closedAfter
                    or p.expiredAt > :closedAfter)
               and p.id < :postId
             order by p.id desc
            """)
    List<RecruitPost> findBoardAfterByGame(@Param("game") Game game, @Param("closedAfter") Instant closedAfter,
                                           @Param("postId") long postId, Limit limit);

    /**
     * 글을 고칠 때 — 줄을 잠그고 읽는다({@code SELECT … FOR UPDATE}). 읽고 판단하는 사이에 만료 · 확정이 끼어들지 못한다
     * (그쪽의 조건부 UPDATE 가 이 트랜잭션이 끝날 때까지 기다린다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RecruitPost p where p.id = :id")
    Optional<RecruitPost> findByIdForUpdate(@Param("id") Long id);

    /** 방장 키를 처음 봤다. 이미 적혀 있으면 건드리지 않는다 — "처음 본 순간"이다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RecruitPost p
               set p.roomSeenAt = :now
             where p.id in :ids
               and p.roomSeenAt is null
               and p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
            """)
    int markRoomSeen(@Param("ids") Collection<Long> ids, @Param("now") Instant now);

    /** 모집 중일 때만 만료로 바꾼다. 돌려주는 값이 1 이면 이 호출이 바꾼 것이다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RecruitPost p
               set p.status = com.queuemate.platform.party.domain.PostStatus.EXPIRED, p.expiredAt = :now, p.updatedAt = :now
             where p.id = :id
               and p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
            """)
    int expireIfRecruiting(@Param("id") Long id, @Param("now") Instant now);

    /** 모집 중일 때만 확정으로 바꾼다. 돌려주는 값이 1 이면 이 호출이 바꾼 것이다 — 그 트랜잭션이 파티와 파티원을 기록한다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RecruitPost p
               set p.status = com.queuemate.platform.party.domain.PostStatus.CONFIRMED, p.confirmedAt = :now, p.updatedAt = :now
             where p.id = :id
               and p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
            """)
    int confirmIfRecruiting(@Param("id") Long id, @Param("now") Instant now);
}
