package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.RecentPlayer;
import com.queuemate.platform.social.dto.RecentPlayerResponse;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 최근 함께한 사람. 채우는 것은 게시판 파티가 닫힐 때다({@link #recordParty} — 2026-09-26 소유자 결정). 자동 매칭 파티({@code PartyClosed.fifo})는 SQS 가 정해진 뒤다.
 */
public interface RecentPlayerRepository extends JpaRepository<RecentPlayer, RecentPlayer.Key> {

    /**
     * 내 목록을 최근순으로, 닉네임까지 <b>쿼리 한 번</b>에({@code users} 를 JOIN). <b>나와 어느 방향으로든 차단 관계인 사람은 쿼리에서 뺀다</b> —
     * 받아 온 뒤에 빼면 "50명까지"가 차단한 수만큼 모자란다. 같은 시각이면 사용자 번호순이다 — 순서가 부를 때마다 바뀌지 않게.
     */
    @Query("""
            select new com.queuemate.platform.social.dto.RecentPlayerResponse(
                       p.key.otherUserId, u.nickname, p.lastPartyId, p.lastPlayedAt)
              from RecentPlayer p
              join User u on u.id = p.key.otherUserId
             where p.key.userId = :me
               and not exists (
                       select 1
                         from Block b
                        where (b.blockerId = :me and b.blockedId = p.key.otherUserId)
                           or (b.blockedId = :me and b.blockerId = p.key.otherUserId))
             order by p.lastPlayedAt desc, p.key.otherUserId asc
            """)
    List<RecentPlayerResponse> findRecentExcludingBlocked(@Param("me") Long me, Limit limit);

    /**
     * 파티 하나의 파티원끼리 <b>서로를</b> 적는다 — 순서쌍 (a, b), a ≠ b 마다 한 줄이다(방향이 있다). <b>쿼리 한 번이다</b> — 파티원을 {@code party_members} 에서
     * 자기 JOIN 으로 짝짓는다(2026-09-26 스키마를 합쳐 JOIN 이 된다). 이미 있는 줄은 마지막 파티 · 시각으로 덮는다(UPSERT — 한 사람에 한 줄).
     * 파티원이 1명이면 짝이 없어 아무것도 적지 않는다. 차단 관계인 사람도 적는다 — 읽을 때 뺀다({@link #findRecentExcludingBlocked}).
     *
     * @return 넣거나 덮은 줄 수
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO recent_players (user_id, other_user_id, last_party_id, last_played_at)
            SELECT a.user_id, b.user_id, a.party_id, :at
              FROM party_members a
              JOIN party_members b ON b.party_id = a.party_id AND b.user_id <> a.user_id
             WHERE a.party_id = :partyId
            ON CONFLICT (user_id, other_user_id)
            DO UPDATE SET last_party_id = EXCLUDED.last_party_id, last_played_at = EXCLUDED.last_played_at
            """)
    int recordParty(@Param("partyId") Long partyId, @Param("at") Instant at);
}
