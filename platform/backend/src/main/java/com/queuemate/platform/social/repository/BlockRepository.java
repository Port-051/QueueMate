package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.Block;
import com.queuemate.platform.social.domain.BlockPair;
import com.queuemate.platform.social.dto.BlockResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <b>"이미 차단했는지" 먼저 조회하고 넣지 마라</b> — 확인과 INSERT 사이에 같은 요청이 끼어든다. 중복은 {@code saveAndFlush} 의
 * UNIQUE 위반({@code blocks_blocker_blocked_key})으로 안다 ({@code BlockService#block}).
 */
public interface BlockRepository extends JpaRepository<Block, Long> {

    /**
     * 내가 차단한 사람만 — 나를 차단한 사람은 보여 주지 않는다. 새로 차단한 사람이 먼저 온다. <b>닉네임까지 쿼리 한 번이다</b>({@code users} 를 JOIN).
     * 차단한 사람의 줄은 FK({@code ON DELETE CASCADE})라 사용자가 없어지면 같이 지워진다 — 닉네임이 빈 줄은 생기지 않는다.
     */
    @Query("""
            select new com.queuemate.platform.social.dto.BlockResponse(b.blockedId, u.nickname, b.createdAt)
              from Block b
              join User u on u.id = b.blockedId
             where b.blockerId = :blockerId
             order by b.createdAt desc, b.id desc
            """)
    List<BlockResponse> findResponsesOf(@Param("blockerId") Long blockerId);

    /** 차단 한 건을 응답 모양으로 — 방금 넣은 줄을 닉네임과 같이 읽는다 */
    @Query("""
            select new com.queuemate.platform.social.dto.BlockResponse(b.blockedId, u.nickname, b.createdAt)
              from Block b
              join User u on u.id = b.blockedId
             where b.blockerId = :blockerId
               and b.blockedId = :blockedId
            """)
    Optional<BlockResponse> findResponse(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** 없어도 에러가 아니다 — 지운 줄 수를 돌려준다. 엔티티를 읽어 와서 지우지 않는다(SELECT 없이 DELETE 한 번) */
    @Modifying
    @Query("delete from Block b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    int deleteByBlockerIdAndBlockedId(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /**
     * {@code others} 가운데 나와 <b>어느 방향으로든</b> 차단 관계인 사람의 번호. <b>쿼리 한 번이다.</b> 양쪽이 서로 차단했으면 같은 아이디가
     * 두 번 온다 — 부른 쪽이 집합으로 모은다. {@code others} 가 비어 있으면 부르지 마라.
     */
    @Query("""
            select case when b.blockerId = :me then b.blockedId else b.blockerId end
              from Block b
             where (b.blockerId = :me and b.blockedId in :others)
                or (b.blockedId = :me and b.blockerId in :others)
            """)
    List<Long> findCounterpartsEitherWay(@Param("me") Long me, @Param("others") Collection<Long> others);

    /**
     * 나와 <b>어느 방향으로든</b> 차단 관계인 사람 전부 — 회원 탈퇴가 사용자 줄을 지우기 전에 읽는다(지우면 CASCADE 가 그 줄들을 지운다 —
     * 그 뒤 차단 관계 사본에서 탈퇴자 번호를 뺄 상대 목록이다). 양쪽이 서로 차단했으면 같은 번호가 두 번 온다.
     */
    @Query("""
            select case when b.blockerId = :me then b.blockedId else b.blockerId end
              from Block b
             where b.blockerId = :me or b.blockedId = :me
            """)
    List<Long> findAllCounterparts(@Param("me") Long me);

    /** 반대 방향의 차단이 남았는가 — 해제가 차단 관계 사본에서 그 쌍을 뺄지 정한다({@code BlockService#unblock}) */
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * {@code blocks} 표 전체 — 차단 관계 사본의 재구성이 읽는다({@code BlockRelationRedis#rebuild}). <b>표가 크지 않다는 전제다</b>(수천 줄 — 사람이 누른 차단만 쌓인다).
     * 수십만 줄이 되면 한 번에 읽지 말고 나눠 읽게 바꾼다.
     */
    @Query("select new com.queuemate.platform.social.domain.BlockPair(b.blockerId, b.blockedId) from Block b")
    List<BlockPair> findAllPairs();

    /**
     * <b>차단 관계 사본(Redis)을 바꾸는 일을 한 줄로 세운다</b> — 트랜잭션 단위 advisory lock({@code pg_advisory_xact_lock}). 커밋 · 롤백 때 저절로 풀린다.
     * 차단 · 해제 · 재구성이 트랜잭션 맨 앞에서 부른다(Claude 세부 — P-52). 트랜잭션 안에서만 부른다.
     *
     * <p>왜 — 사본은 "DB 를 바꾸고 커밋 전에 Redis 를 고친다" 로 맞춘다. 줄을 세우지 않으면 ① 재구성이 표를 읽은 뒤 커밋된 차단을 재구성의 옛 계산이 덮어
     * 지운다 ② 같은 두 사람의 차단과 해제가 겹치면 해제의 "반대 방향 줄이 있나" 가 아직 커밋 안 된 차단을 못 보고 사본에서 뺀다 — 둘 다 <b>DB 에는 있는데 사본에는 없는</b>
     * 차단(덜 막기)이 다음 재구성까지 남는다. 사람이 누르는 요청이라 앱 전체에서 한 줄로 서도 기다림은 짧다(재구성이 도는 동안은 그만큼 기다린다).
     * 키는 사본 접두사의 {@code hashtext} 다 — 이 앱에서 advisory lock 을 쓰는 곳은 여기뿐이다.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext('qm:user:block-rel:'))", nativeQuery = true)
    Integer lockRelationCopy();
}
