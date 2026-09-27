package com.queuemate.matching.block;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 차단 조회. INV-6 을 지키는 두 겹이 각각 하나씩 쓴다 (docs/11 D-1 · D-2).
 *
 * <ul>
 *   <li><b>선필터</b> — {@link #findBlockedUserIds}. 배정 경로가 락을 잡기 전에 "나와 차단 관계인 사람"을
 *       한 번 가져와 후보 파티를 거른다.
 *   <li><b>확정 직전 최종 검증</b> — {@link #findBlocksAmong}. 수락이 들어올 때 파티원 전원 사이의 차단을
 *       한 번에 묻는다. 선필터는 <b>내가 들어갈 때</b>의 차단만 보므로, 파티가 찬 뒤에 생긴 차단과
 *       (정원 2 처럼) 선필터가 볼 상대가 없던 파티는 여기서만 잡힌다 ({@link PartyBlockCheck}).
 * </ul>
 *
 * <p><b>{@code JpaRepository} 를 상속하지 않는다.</b> 그러면 {@code save}/{@code delete} 가
 * 같이 딸려오는데 우리는 읽기만 한다. 필요한 메서드만 직접 선언한다.
 *
 * <p><b>모든 조회가 양방향이다.</b> 원본 테이블은 "누가 누구를 차단했다"라 방향이 있지만
 * 매칭에서는 방향이 의미 없다. A 가 B 를 차단했든 그 반대든 같은 파티에 넣으면 안 된다.
 *
 * <p><b>실패는 삼키지 않는다.</b> 두 메서드가 던지는 {@code DataAccessException} 은 그대로 올라가
 * {@code GlobalExceptionHandler} 가 503 으로 바꾼다 — 차단을 확인하지 못했으면 확정하지 않는다
 * (INV-10 fail-closed).
 */
public interface BlockRepository extends Repository<Block, Long> {

    /**
     * 주어진 사용자들 사이에 존재하는 차단 전부.
     *
     * <p><b>확정 직전 최종 검증이 쓰는 메서드다.</b> 참가자가 5명이면 확인할 조합이 10개인데
     * 조회 한 번으로 끝난다. 결과가 비어 있으면 그 파티는 확정해도 된다. 비어 있지 않으면 그 쌍의
     * 양쪽을 파티에서 빼는 것은 부르는 쪽({@link PartyBlockCheck} · {@code ProposalService})의 몫이다.
     *
     * <p>양쪽 컬럼에 같은 집합으로 {@code in} 을 걸었으므로 방향과 무관하게 잡힌다. 자기 자신을 차단한
     * 행은 저쪽이 막지만, 있어도 한 사람만으로 제안을 깨지 않게 걸러 둔다.
     *
     * @param ids 파티원의 사용자 번호. 둘 이상이어야 뜻이 있다
     */
    @Query("""
            select b from Block b
            where b.blockerId in :ids and b.blockedId in :ids
              and b.blockerId <> b.blockedId
            """)
    List<Block> findBlocksAmong(@Param("ids") Collection<Long> ids);

    /**
     * 이 사용자와 차단 관계인 상대의 id 전부.
     *
     * <p><b>배정 경로(선필터)가 쓰는 메서드다.</b> 내 차단 목록은 어느 후보 파티를 보든 같으므로
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
