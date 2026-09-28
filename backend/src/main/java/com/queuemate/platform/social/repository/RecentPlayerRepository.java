package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.RecentPlayer;
import com.queuemate.platform.social.dto.RecentPlayerResponse;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 최근 함께한 사람. 채우는 자리가 둘이다 — 게시판 파티는 닫힐 때 파티원끼리 한꺼번에({@link #recordParty} — 2026-09-26 소유자 결정),
 * 자동 매칭 파티는 <b>사람이 방에 들어올 때</b> 그 순간 방에 있던 사람들과({@link #recordEntry} — 2026-09-28 소유자 결정. 늦게 들어오는 사람이 있는
 * 방이라 닫을 때 한꺼번에 적으면 서로 마주친 적 없는 두 사람도 짝이 된다. SQS 는 두지 않는다 — docs/11 D-42).
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

    /**
     * <b>한 사람이 방에 들어온 순간</b> — 그 사람과 그때 방에 있던 사람들({@code others} — 스크립트가 돌려준 멤버 SET)을 <b>양방향으로</b> 적는다.
     * 자동 매칭 파티가 쓴다(2026-09-28 소유자 결정). 파티를 닫을 때가 아니라 들어올 때 적는 이유 — 자동 매칭 방은 확정 뒤에도
     * 사람이 늦게 들어오고 먼저 나가므로, 닫을 때 {@code party_members} 끼리 전부 짝을 지으면 A 가 나간 뒤 들어온 C 까지 A 와
     * "함께한 사람"이 된다. 들어올 때 "그 순간 방에 있는 사람"과 짝을 지으면 실제로 마주친 사람끼리만 남는다.
     * 먼저 들어온 사람은 나중 사람이 들어올 때 그 사람과 짝이 지어지므로 빠지지 않는다.
     *
     * <p>{@code others} 를 {@code party_members} 로 걸러 적는다 — {@code users} 에 없어 파티원으로 안 적힌 번호는 짝에서도 빠지므로 FK 위반이
     * 나지 않고, 방 멤버 SET 에 손으로 넣은 값도 걸러진다.
     * 같은 사람을 두 번 넣어도 {@code ON CONFLICT} 로 시각만 갱신된다.
     *
     * <p>SELECT 목록의 파라미터에 CAST 를 붙인 이유 — {@code UNION ALL} 이 끼면 Postgres 가 INSERT 대상 칸에서 타입을 끌어오지 못하고
     * {@code text} 로 보아 "column last_played_at is of type timestamp with time zone but expression is of type text" 로 실패한다
     * (2026-09-28 테스트에서 확인). {@link #recordParty} 는 UNION 이 없어 그냥 된다.
     *
     * @return 적거나 갱신한 줄 수 ({@code others} 가운데 파티원이 n 명이면 2n)
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO recent_players (user_id, other_user_id, last_party_id, last_played_at)
            SELECT CAST(:me AS bigint), m.user_id, CAST(:partyId AS bigint), CAST(:at AS timestamptz)
              FROM party_members m
             WHERE m.party_id = :partyId AND m.user_id IN (:others) AND m.user_id <> :me
            UNION ALL
            SELECT m.user_id, CAST(:me AS bigint), CAST(:partyId AS bigint), CAST(:at AS timestamptz)
              FROM party_members m
             WHERE m.party_id = :partyId AND m.user_id IN (:others) AND m.user_id <> :me
            ON CONFLICT (user_id, other_user_id)
            DO UPDATE SET last_party_id = EXCLUDED.last_party_id, last_played_at = EXCLUDED.last_played_at
            """)
    int recordEntry(@Param("partyId") Long partyId, @Param("me") Long me, @Param("others") Collection<Long> others,
                    @Param("at") Instant at);
}
