package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.Block;
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
}
