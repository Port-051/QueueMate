package com.queuemate.matching.block;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 차단 조회. 파티를 확정하기 직전에 부른다.
 *
 * <p><b>{@code JpaRepository} 를 상속하지 않는다.</b> 그러면 {@code save}/{@code delete} 가
 * 같이 딸려오는데 우리는 읽기만 한다. 필요한 메서드만 직접 선언한다.
 *
 * <p><b>모든 조회가 양방향이다.</b> 원본 테이블은 "누가 누구를 차단했다"라 방향이 있지만
 * 매칭에서는 방향이 의미 없다. A 가 B 를 차단했든 그 반대든 같은 파티에 넣으면 안 된다.
 */
public interface BlockRepository extends Repository<Block, Long> {

    /** 두 사람이 차단 관계인가. 방향을 따지지 않는다. */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blockerId = :a and b.blockedId = :b)
               or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean isBlocked(@Param("a") Long a, @Param("b") Long b);

    /**
     * 주어진 사용자들 사이에 존재하는 차단 전부.
     *
     * <p>파티 확정 검증이 쓰는 메서드다. 참가자가 5명이면 확인할 조합이 10개인데
     * 조회 한 번으로 끝난다. 결과가 비어 있으면 그 파티는 확정해도 된다.
     *
     * <p>양쪽 컬럼에 {@code in} 을 걸었으므로 방향과 무관하게 잡힌다.
     */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blockerId = :userId and b.blockedId in :memberIds)
               or (b.blockerId in :memberIds and b.blockedId = :userId)
            """)
    boolean findBlocksAmong(@Param("userId") Long userId, @Param("memberIds") Collection<Long> memberIds);

    /**
     * 이 사용자와 차단 관계인 상대의 id 전부.
     *
     * <p><b>배정 경로가 쓰는 메서드다.</b> 내 차단 목록은 어느 후보 파티를 보든 같으므로
     * 후보마다 다시 물을 이유가 없다. 배정을 시작하기 전에 한 번만 가져오면 후보 풀 락 안에는
     * Redis 명령만 남는다 (PoolLock 클래스 주석 — 락 안에서 DB 를 치지 마라).
     *
     * <p><b>배정 경로는 이것을 직접 부르지 않고 {@link BlockedUsers#of} 를 거친다</b> — 이 앱의
     * {@code userId} 는 문자열이라 거기서 바꿔 준다.
     *
     * <p>방향과 무관하게 상대방 id 를 돌려준다. 내가 차단한 쪽이면 {@code blockedId},
     * 내가 차단당한 쪽이면 {@code blockerId} 가 상대다.
     */
    @Query("""
            select case when b.blockerId = :userId then b.blockedId else b.blockerId end
            from Block b
            where b.blockerId = :userId or b.blockedId = :userId
            """)
    List<Long> findBlockedUserIds(@Param("userId") Long userId);
}
