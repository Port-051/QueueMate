package com.queuemate.platform.party.repository;

import com.queuemate.platform.party.domain.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 확정된 파티와 파티원의 기록. <b>넣는 것은 전부 {@code INSERT … ON CONFLICT DO NOTHING} 이다</b> — 같은 확정을 확정 요청과 자가 치유
 * (목록 · 단건이 확정 표시 키를 발견)가 동시에 적으려 들어도 파티는 하나, 파티원은 한 벌이다({@code contracts/platform-api.md} "방장 확정"). PostgreSQL 전용 문법이다(H2 를 쓰지 않는다).
 *
 * <p>제약 위반을 예외로 받아 "이미 있다"로 읽지 않는 이유 — PostgreSQL 은 위반이 난 트랜잭션을 더 쓸 수 없게 만든다.
 * 같은 트랜잭션에서 글의 상태도 바꾸므로 위반 없이 지나가야 한다.
 */
public interface PartyRecordRepository extends JpaRepository<PartyMember, PartyMember.Key> {

    /**
     * 게시판 파티 — {@code post_id} 가 글의 id(= roomId)이고 {@code id} 는 DB 가 매긴다(identity). <b>"한 글에 파티 하나"는 {@code UNIQUE (post_id)} 가
     * 지킨다</b> — 그래서 {@code ON CONFLICT (post_id)} 다. 돌려주는 값이 0 이면 이미 있던 파티다. 파티의 id 는 {@link #findPartyIdByPostId} 로 다시 읽는다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO parties (source, post_id, game, status, created_at)
            VALUES ('BOARD', :postId, :game, 'ACTIVE', :now)
            ON CONFLICT (post_id) DO NOTHING
            """)
    int insertBoardPartyIfAbsent(@Param("postId") Long postId, @Param("game") String game, @Param("now") Instant now);

    /** 그 글의 파티 id. 확정 기록이 없으면 비어 있다 */
    @Query(nativeQuery = true, value = "SELECT p.id FROM parties p WHERE p.post_id = :postId")
    Optional<Long> findPartyIdByPostId(@Param("postId") Long postId);

    /**
     * 파티원 한 명. <b>가입한 사용자만 적는다</b> — {@code party_members.user_id} 에 {@code users(id)} 로 가는 FK 가 있어(2026-09-26) 멤버 HASH 에 손으로 넣은
     * 가입하지 않은 번호를 그대로 넣으면 위반이 나고 PostgreSQL 이 그 트랜잭션(글의 확정까지)을 통째로 못 쓰게 만든다. 그래서 {@code WHERE EXISTS} 로 걸러
     * 위반 없이 지나간다 — 돌려주는 값이 0 이면 이미 있었거나 가입하지 않은 번호다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO party_members (party_id, user_id, is_host, joined_at)
            SELECT :partyId, :userId, :host, :now
             WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = :userId)
            ON CONFLICT DO NOTHING
            """)
    int insertMemberIfAbsent(@Param("partyId") Long partyId, @Param("userId") Long userId,
                             @Param("host") boolean host, @Param("now") Instant now);

    /**
     * <b>파티를 닫는다</b>(2026-09-26 소유자 결정 — 확정된 방이 없어질 때 파티가 닫힌다). <b>아직 열려 있을 때만</b>(조건부 UPDATE) —
     * 두 길(마지막 사람의 나가기 · 목록 · 단건이 사라진 방을 발견)이 동시에 와도 1줄을 받는 것은 한 호출뿐이다. 그 호출만 최근 함께한 사람을 적는다.
     *
     * @return 이 호출이 닫았으면 1, 이미 닫혔거나 그 글의 파티가 없으면(확정 전) 0
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE parties SET status = 'CLOSED', closed_at = :now
             WHERE post_id = :postId AND status = 'ACTIVE'
            """)
    int closeIfActive(@Param("postId") Long postId, @Param("now") Instant now);

    /**
     * <b>자동 매칭 파티</b>(2026-09-27 소유자 결정 — docs/11 D-42) — {@code match_party_id} 가 {@code matching} 의 {@code partyId}(UUID 문자열 = 그 파티의
     * {@code roomId})이고 {@code post_id} 는 없다(글이 없다). <b>"매칭의 파티 하나에 파티 하나"는 {@code UNIQUE (match_party_id)} 가 지킨다</b>(V2) —
     * 파티원 전원이 {@code MATCH_CONFIRMED} 를 받고 동시에 부르므로 {@code ON CONFLICT (match_party_id)} 다. 돌려주는 값이 1 이면 이 호출이 파티를 만들었다 —
     * 그 호출의 사용자를 {@code is_host} 로 적는다({@code party.service.MatchPartyStore}). 파티의 id 는 {@link #findPartyIdByMatchPartyId} 로 다시 읽는다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO parties (source, match_party_id, game, status, created_at)
            VALUES ('MATCH', :matchPartyId, :game, 'ACTIVE', :now)
            ON CONFLICT (match_party_id) DO NOTHING
            """)
    int insertMatchPartyIfAbsent(@Param("matchPartyId") String matchPartyId, @Param("game") String game, @Param("now") Instant now);

    /** 그 자동 매칭 파티의 파티 id. 아직 아무 파티원도 이 앱을 부르지 않았으면 비어 있다 */
    @Query(nativeQuery = true, value = "SELECT p.id FROM parties p WHERE p.match_party_id = :matchPartyId")
    Optional<Long> findPartyIdByMatchPartyId(@Param("matchPartyId") String matchPartyId);

    /**
     * 자동 매칭 파티를 닫는다 — {@link #closeIfActive} 의 자동 매칭 판이다. 그 파티의 방({@code roomId} = {@code match_party_id})이 없어질 때
     * ({@code RoomMemberService} 의 나가기 · 접속 확인) 부른다. 조건부 UPDATE 라 몇 번 와도 1줄을 받는 것은 한 호출뿐이다.
     *
     * @return 이 호출이 닫았으면 1, 이미 닫혔거나 그런 파티가 없으면 0
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE parties SET status = 'CLOSED', closed_at = :now
             WHERE match_party_id = :matchPartyId AND status = 'ACTIVE'
            """)
    int closeIfActiveByMatchPartyId(@Param("matchPartyId") String matchPartyId, @Param("now") Instant now);

    /**
     * 주어진 글들의 <b>게시판 파티와 그 파티원</b> — 한 줄이 {@code [post_id, status, user_id, is_host]} 다(파티원이 없는 파티는 {@code user_id} · {@code is_host} 가
     * {@code NULL} 인 한 줄). <b>쿼리 한 번이다</b> — 목록 · 단건이 한 페이지의 확정된 글 전부에 대해 한 번 부른다({@code PostService#observe}).
     *
     * <p>두 가지를 한 번에 안다 — ① <b>파티가 아직 열려 있는 글</b>(방 키를 읽어 "방이 없어졌나" 를 볼 글 — 파티 닫힘 P-25. 이미 닫힌 파티의 글은 방 키를 다시 읽지 않는다)
     * ② <b>확정 순간의 파티원</b>(확정된 글의 카드 — 2026-09-30 소유자 결정 · P-40. 방에서 나간 뒤에도 보여 준다). 2026-09-30 전에는 ① 만 읽었다
     * ({@code findActivePartyPostIds}) — 둘을 합쳐 목록의 SQL 문장 수가 늘지 않게 했다.
     */
    @Query(nativeQuery = true, value = """
            SELECT p.post_id, p.status, m.user_id, m.is_host
              FROM parties p
              LEFT JOIN party_members m ON m.party_id = p.id
             WHERE p.post_id IN (:postIds)
             ORDER BY p.post_id, m.user_id
            """)
    List<Object[]> findBoardParties(@Param("postIds") Collection<Long> postIds);

    /**
     * 그 게임의 <b>아직 열려 있는 자동 매칭 파티</b>의 {@code match_party_id}(= 그 방의 {@code roomId}, UUID) — 게시판 목록이 그 방 키를 같이 읽어
     * "방이 없어졌나" 를 본다({@code PostService#closeVanishedMatchParties} — 2026-09-28 소유자 결정. 게시판 파티의 길 ② 의 자동 매칭 판이다).
     * 자동 매칭 파티는 글이 없어 {@link #findBoardParties} 로는 잡히지 않는다.
     *
     * <p><b>{@code LIMIT 200} 인 이유</b> — 목록 조회 한 번이 열린 파티를 끝없이 훑지 않게 한다(방 키 읽기가 파티 수만큼 파이프라인에 실린다).
     * 넘치는 것은 다음 목록 조회가 이어서 본다 — 닫힌 파티는 {@code ACTIVE} 가 아니라 다시 나오지 않으므로 앞에서부터 줄어든다.
     */
    @Query(nativeQuery = true, value = """
            SELECT p.match_party_id FROM parties p
             WHERE p.source = 'MATCH' AND p.status = 'ACTIVE' AND p.game = :game
             ORDER BY p.id LIMIT 200
            """)
    List<String> findActiveMatchPartyIds(@Param("game") String game);

    @Query("select m from PartyMember m where m.key.partyId = :partyId")
    List<PartyMember> findByPartyId(@Param("partyId") Long partyId);

    /**
     * 기록된 <b>자동 매칭 파티와 그 파티원</b> — 한 줄이 {@code [game, user_id]} 다(파티원이 없는 파티는 {@code user_id} 가 {@code NULL} 인 한 줄 · 그런 파티가 없으면 빈 목록).
     * <b>쿼리 한 번이다.</b> 퀵 매칭 파티의 팀원 카드가 파티 HASH 가 수명으로 사라진 뒤에 쓴다({@code MatchPartyService#members} — 2026-10-01 소유자 결정).
     * {@code party_members} 에는 가입한 사용자만 있다({@link #insertMemberIfAbsent}).
     */
    @Query(nativeQuery = true, value = """
            SELECT p.game, m.user_id
              FROM parties p
              LEFT JOIN party_members m ON m.party_id = p.id
             WHERE p.source = 'MATCH' AND p.match_party_id = :matchPartyId
             ORDER BY m.user_id
            """)
    List<Object[]> findMatchPartyMembers(@Param("matchPartyId") String matchPartyId);
}
