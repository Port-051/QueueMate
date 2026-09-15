package com.queuemate.social.repository;

import com.queuemate.social.domain.Block;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<Block, Block.BlockId> {

    List<Block> findAllByIdBlockerIdOrderByCreatedAtDesc(UUID blockerId);

    /** 방향 무관하게 상대편 id를 모은다. INV-6의 후보 배제 집합이다. */
    @Query("""
            SELECT CASE WHEN b.id.blockerId = :userId THEN b.id.blockedId ELSE b.id.blockerId END
              FROM Block b
             WHERE b.id.blockerId = :userId OR b.id.blockedId = :userId
            """)
    List<UUID> findCounterpartIds(@Param("userId") UUID userId);

    /** 확정 직전 재검증용. 주어진 집합 안에 차단 쌍이 하나라도 있으면 결과가 비어 있지 않다. */
    @Query("""
            SELECT COUNT(b) FROM Block b
             WHERE b.id.blockerId IN :userIds AND b.id.blockedId IN :userIds
            """)
    long countBlocksWithin(@Param("userIds") Collection<UUID> userIds);
}
