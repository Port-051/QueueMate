package com.queuemate.matching.block;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 차단 조회. INV-6 은 <b>배정 때의 선필터 한 겹</b>으로 지킨다 — {@link #findBlockedUserIds} 를
 * 배정 경로가 락을 잡기 전에 한 번 불러 "나와 차단 관계인 사람"이 있는 후보 파티를 거른다.
 *
 * <p>docs/11 D-1 의 "확정 직전 동기 SELECT" 두 번째 겹은 2026-09-27 에 두지 않기로 했다(D-41). 그것이 더 잡는
 * 것은 "둘이 같은 파티에 들어온 뒤에 차단한 경우" 하나뿐이고 그 창은 제안 시한 20초 안이다 — 수락마다 DB 를
 * 치는 값에 못 미친다고 판단했다.
 *
 * <p><b>{@code JpaRepository} 를 상속하지 않는다.</b> 그러면 {@code save}/{@code delete} 가
 * 같이 딸려오는데 우리는 읽기만 한다. 필요한 메서드만 직접 선언한다.
 *
 * <p><b>모든 조회가 양방향이다.</b> 원본 테이블은 "누가 누구를 차단했다"라 방향이 있지만
 * 매칭에서는 방향이 의미 없다. A 가 B 를 차단했든 그 반대든 같은 파티에 넣으면 안 된다.
 *
 * <p><b>실패는 삼키지 않는다.</b> {@code DataAccessException} 은 그대로 올라간다 — 배정은 {@code @Async} 안이라
 * 로그로 남고 그 요청의 배정만 실패한다. 차단을 확인하지 못한 채 파티에 넣지 않는다 (INV-10 fail-closed).
 */
public interface BlockRepository extends Repository<Block, Long> {

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
