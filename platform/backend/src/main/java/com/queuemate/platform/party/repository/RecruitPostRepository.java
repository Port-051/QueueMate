package com.queuemate.platform.party.repository;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.party.domain.VoicePreference;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * 게시판 목록의 <b>첫 페이지</b> — <b>글을 상태로 가리지 않는다.</b> 모집 중 · 확정 · 만료가 전부 {@code id} 내림차순으로 나온다
     * (2026-09-25 소유자 결정). 찾는 포지션은 쿼리 한 번으로 같이 온다({@code RecruitPost#wantedPositions}).
     *
     * <p><b>게임 하나만 고르는 쿼리다 — "세 게임 전부" 용 쿼리는 없다</b>(2026-09-25 소유자 결정 — <b>게시판은 게임별로 나뉜 페이지이고
     * "전체" 화면이 없다.</b> {@code contracts/platform-api.md} · P-21). 그 전에는 게임 없이 훑는 쿼리가 따로 있었는데 부르는 길이 없어져 지웠다 —
     * 그래서 <b>{@code game} 은 등호 조건으로 늘 있고 {@code (game, id DESC)} 인덱스를 언제나 그대로 탄다.</b>
     *
     * <p><b>거르는 조건이 없어졌다</b> — 2026-09-25 전에는 "모집 중이거나 만료 · 확정된 지 10분이 안 됐다"는 <b>세 컬럼에 걸친 {@code OR} 셋</b>이었다.
     * 그러면 {@code (game, id DESC)} 인덱스를 깨끗하게 타지 못한다(등호가 아닌 조건이 셋이라 걸러 내는 일이 인덱스 밖에서 일어난다).
     * 지금은 <b>게임으로 좁히고 그 인덱스를 순서대로 훑어 내려가면 끝이다.</b> 끝난 글을 계속 보여 주는 것은 "이 서비스에서 모집이 얼마나 활발한가"를
     * 보여 주는 쪽이기도 하다({@code contracts/platform-api.md} "목록의 정렬").
     *
     * <p><b>정렬은 {@code id} 내림차순 하나 = 최신순이다</b>(2026-09-24 소유자 결정 — {@code id} 가 identity 라 넣은 순서대로 커진다).
     * <b>글의 상태도 {@code createdAt} 도 쓰지 않는다</b>(옛 정렬은 모집 중인 글을 앞으로 당기고 그 뒤에 {@code createdAt} 을 봤다).
     * 커서는 정렬 키가 <b>변하지 않는다</b>는 전제 위에 서는데 상태는 변하고 이 목록 조회 자신이 바꾼다({@code PostService#list} 의 주석).
     *
     * <p><b>{@code limit} 이 있다</b>(2026-09-23 소유자 결정) — 게시판은 신호가 올 때마다 다시 받으므로 전부 내려 주면 그 큰 응답이 되풀이된다.
     * 다음 줄이 있는지는 부르는 쪽이 <b>한 개 더 읽어</b> 안다({@code PostService#list}).
     */
    @Query("""
            select p
              from RecruitPost p
             where p.game = :game
             order by p.id desc
            """)
    List<RecruitPost> findBoard(@Param("game") Game game, Limit limit);

    /**
     * {@link #findBoard} 의 <b>다음 페이지</b> — 커서(<b>마지막으로 읽은 글의 번호</b>)가 가리키는 줄 <b>다음</b>부터다.
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
             where p.game = :game
               and p.id < :postId
             order by p.id desc
            """)
    List<RecruitPost> findBoardAfter(@Param("game") Game game, @Param("postId") long postId, Limit limit);

    /**
     * <b>게시판 방 먼저 합류의 후보</b>(2026-09-28 소유자 결정 · P-28) — 그 게임 · 그 모드의 <b>모집 중인</b> 글을 <b>오래된 순({@code id} 오름차순)</b>으로 많아야 {@code limit} 개.
     * "여럿이면 가장 오래된 방부터" 다. {@code game} 이 등호 조건이라 {@code (game, id DESC)} 인덱스를 거꾸로 훑고, {@code mode} · {@code status} · {@code voice} · 내 글 제외는
     * 그 위에서 거른다(모집 중인 글은 게임마다 많지 않다 — 부분 UNIQUE 인덱스가 사람마다 하나로 묶는다).
     * <b>음성과 내 글 제외를 여기서 보는 이유</b>(2026-09-29 — 그 전에는 자바가 봤다) — 컬럼 등호 조건이라 DB 가 보는 것이 싸고, 무엇보다 {@code limit} 이 <b>쓸 만한 글</b>을 세게 된다.
     * 자바에서 거르면 음성이 안 맞는 글이 상한 {@code limit} 을 잡아먹어 뒤에 맞는 글이 있어도 404 가 났다.
     * 나머지 조건(PUBG 시점 — jsonb 안 · 포지션 — 별도 표 · 방장 티어 · 방 안 인원 — Redis)은 여전히 자바가 본다.
     * <b>빠른매치 입장을 금지한 글({@code allowAutoJoin = false})도 여기서 뺀다</b>(2026-10-02 소유자 결정 — P-50) — 음성 · 내 글과 같은 까닭으로 SQL 에서 거른다
     * (자바에서 거르면 금지한 글이 상한을 잡아먹는다).
     */
    @Query("""
            select p
              from RecruitPost p
             where p.game = :game
               and p.mode = :mode
               and p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
               and p.voice = :voice
               and p.hostId <> :me
               and p.allowAutoJoin = true
             order by p.id asc
            """)
    List<RecruitPost> findAutoJoinCandidates(@Param("game") Game game, @Param("mode") String mode, @Param("voice") VoicePreference voice,
                                             @Param("me") Long me, Limit limit);

    /**
     * 글을 고칠 때 — 줄을 잠그고 읽는다({@code SELECT … FOR UPDATE}). 읽고 판단하는 사이에 만료 · 확정이 끼어들지 못한다
     * (그쪽의 조건부 UPDATE 가 이 트랜잭션이 끝날 때까지 기다린다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RecruitPost p where p.id = :id")
    Optional<RecruitPost> findByIdForUpdate(@Param("id") Long id);

    /** 모집 중일 때만 만료로 바꾼다. 돌려주는 값이 1 이면 이 호출이 바꾼 것이다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RecruitPost p
               set p.status = com.queuemate.platform.party.domain.PostStatus.EXPIRED, p.expiredAt = :now, p.updatedAt = :now
             where p.id = :id
               and p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
            """)
    int expireIfRecruiting(@Param("id") Long id, @Param("now") Instant now);

    /**
     * 그 사람의 <b>모집 중인</b> 글의 번호 — 많아야 하나다(부분 UNIQUE 인덱스 {@code recruit_posts_one_recruiting_per_host}).
     * 회원 탈퇴가 남은 모집 중인 글을 글 지우기와 같은 길로 끝낼 때 쓴다({@code PostStore#findRecruitingOf} — 2026-10-02 · P-48)
     */
    @Query("""
            select p.id
              from RecruitPost p
             where p.hostId = :hostId
               and p.status = com.queuemate.platform.party.domain.PostStatus.RECRUITING
            """)
    Optional<Long> findRecruitingIdByHostId(@Param("hostId") Long hostId);

    /** 그 사람이 쓴 글이 하나라도 있는가(상태를 가리지 않는다) — 회원 탈퇴가 게시판 신호를 낼지 가른다({@code PostStore#deleteUnconfirmedOf}) */
    boolean existsByHostId(Long hostId);

    /**
     * <b>회원 탈퇴</b> — 그 사람이 쓴 글 가운데 <b>확정되지 않은 것</b>(모집 중 · 만료)을 지운다(2026-10-02 소유자 결정 · P-48). 찾는 포지션의 줄은 FK 의
     * {@code ON DELETE CASCADE} 가 같이 지운다(확정되지 않은 글에는 파티가 없다). <b>확정된 글은 남는다</b> — 방장의 칸은 이어서 {@code users} 를 지울 때
     * FK 의 {@code ON DELETE SET NULL} 이 비운다(V9). 이것을 건너뛰고 {@code users} 를 지우면 그 SET NULL 이 CHECK {@code recruit_posts_host_id_check} 에 걸린다.
     * SQL 그대로 쓴다 — 찾는 포지션({@code @ElementCollection})의 줄은 DB 의 CASCADE 에 맡긴다.
     *
     * @return 지운 글의 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "DELETE FROM recruit_posts WHERE host_id = :hostId AND status <> 'CONFIRMED'")
    int deleteUnconfirmedByHostId(@Param("hostId") Long hostId);

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
