package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.RecentPlayer;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 최근 함께한 사람 — <b>읽기만 한다.</b> 채우는 쪽은 아직 없다({@code PartyClosed.fifo} 의 소비 — SQS 배선이 미정이다) */
public interface RecentPlayerRepository extends JpaRepository<RecentPlayer, RecentPlayer.Key> {

    /**
     * 내 목록을 최근순으로. <b>나와 어느 방향으로든 차단 관계인 사람은 쿼리에서 뺀다</b> — 받아 온 뒤에 빼면 "50명까지"가 차단한 수만큼 모자란다.
     * {@code social.blocks} 는 같은 스키마라 같이 읽어도 된다(금지된 것은 크로스 스키마 JOIN 이다 — CLAUDE.md §3.5).
     * 같은 시각이면 사용자 번호순이다 — 순서가 부를 때마다 바뀌지 않게.
     */
    @Query("""
            select p
              from RecentPlayer p
             where p.key.userId = :me
               and not exists (
                       select 1
                         from Block b
                        where (b.blockerId = :me and b.blockedId = p.key.otherUserId)
                           or (b.blockedId = :me and b.blockerId = p.key.otherUserId))
             order by p.lastPlayedAt desc, p.key.otherUserId asc
            """)
    List<RecentPlayer> findRecentExcludingBlocked(@Param("me") Long me, Limit limit);
}
