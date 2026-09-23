package com.queuemate.platform.party.repository;

import com.queuemate.platform.party.domain.PartyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 확정된 파티와 파티원의 기록. <b>넣는 것은 전부 {@code INSERT … ON CONFLICT DO NOTHING} 이다</b> — 같은 확정이 두 길(길 ① · 길 ②)로
 * 동시에 와도 파티는 하나, 파티원은 한 벌이다({@code contracts/platform-api.md} "방장 확정의 기록"). PostgreSQL 전용 문법이다(H2 를 쓰지 않는다).
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
            INSERT INTO party.parties (source, post_id, game, status, created_at)
            VALUES ('BOARD', :postId, :game, 'ACTIVE', :now)
            ON CONFLICT (post_id) DO NOTHING
            """)
    int insertBoardPartyIfAbsent(@Param("postId") Long postId, @Param("game") String game, @Param("now") Instant now);

    /** 그 글의 파티 id. 확정 기록이 없으면 비어 있다 */
    @Query(nativeQuery = true, value = "SELECT p.id FROM party.parties p WHERE p.post_id = :postId")
    Optional<Long> findPartyIdByPostId(@Param("postId") Long postId);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO party.party_members (party_id, user_id, is_host, joined_at)
            VALUES (:partyId, :userId, :host, :now)
            ON CONFLICT DO NOTHING
            """)
    int insertMemberIfAbsent(@Param("partyId") Long partyId, @Param("userId") Long userId,
                             @Param("host") boolean host, @Param("now") Instant now);

    @Query("select m from PartyMember m where m.key.partyId = :partyId")
    List<PartyMember> findByPartyId(@Param("partyId") Long partyId);
}
