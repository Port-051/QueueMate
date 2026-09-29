package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameAccountRepository extends JpaRepository<GameAccount, Long> {

    /**
     * 한 사용자의 게임 계정 전부를 전적과 함께 — <b>쿼리 한 번이다</b>(전적은 LEFT JOIN. 없으면 {@code null}). 게임 이름순으로 온다.
     */
    @Query("""
            select new com.queuemate.platform.account.domain.GameAccountWithStats(a, s)
              from GameAccount a
              left join GameAccountStats s on s.gameAccountId = a.id
             where a.userId = :userId
             order by a.game asc
            """)
    List<GameAccountWithStats> findWithStatsByUserId(@Param("userId") Long userId);

    @Query("""
            select new com.queuemate.platform.account.domain.GameAccountWithStats(a, s)
              from GameAccount a
              left join GameAccountStats s on s.gameAccountId = a.id
             where a.userId = :userId and a.game = :game
            """)
    Optional<GameAccountWithStats> findWithStatsByUserIdAndGame(@Param("userId") Long userId, @Param("game") Game game);

    /**
     * 없으면 만들고 있으면 바꾼다 — <b>한 문장이다.</b> 같은 요청이 동시에 두 번 와도 {@code UNIQUE (user_id, game)} 가
     * 한 줄만 남긴다. PostgreSQL 전용 문법이다(H2 를 쓰지 않는다 — CLAUDE.md §5).
     *
     * <p>{@code created_at} 은 처음 만들 때의 값을 지킨다 — DO UPDATE 절에 없다. {@code CAST} 는 {@code null} 을 넘길 때
     * 드라이버가 자료형을 못 정하는 일을 막는다.
     *
     * <p><b>{@code tiers} 는 통째로 갈아 끼운다</b>(2026-09-29 — P-36) — 글자는 {@code account.domain.GameTiers#write} 가 만든 JSON 객체다.
     * 자기신고(VALORANT)는 그 요청의 값이 전부이고, 게임사 API 로 채우는 게임은 긁어 온 사다리 전부다 — 사다리 하나만 고치는 길은 없다.
     *
     * <p><b>{@code verified} · {@code external_id} 는 이 문장 어디에도 없다</b> — 사용자의 요청으로 바뀌지 않는 칸이다.
     * 새 줄은 컬럼의 기본값({@code false} · {@code NULL})으로 들어가고, 있는 줄은 그 값을 지킨다. 딸린 전적({@code game_account_stats})도 건드리지 않는다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO game_accounts (user_id, game, game_nickname, tiers, server, created_at, updated_at)
            VALUES (:userId, :game, :gameNickname, CAST(:tiers AS jsonb), CAST(:server AS varchar), :now, :now)
            ON CONFLICT ON CONSTRAINT game_accounts_user_id_game_key DO UPDATE
               SET game_nickname = EXCLUDED.game_nickname,
                   tiers         = EXCLUDED.tiers,
                   server        = EXCLUDED.server,
                   updated_at    = EXCLUDED.updated_at
            """)
    void upsert(@Param("userId") Long userId, @Param("game") String game,
                @Param("gameNickname") String gameNickname, @Param("tiers") String tiers,
                @Param("server") String server, @Param("now") Instant now);

    /**
     * 여러 사용자의 그 게임 계정에서 <b>사다리 하나의 티어</b>만 — <b>쿼리 한 번이다</b>(전적은 읽지 않는다). 게시판 방 먼저 합류가 후보 글들의 방장 티어를
     * <b>그 모드의 사다리({@code tierLadder})</b>로 한꺼번에 읽는다({@code account.service.GameProfileReader#findTiers} — 2026-09-28 · 2026-09-29 P-36).
     * jsonb 안에서 SQL 이 뽑는다({@code tiers ->> 사다리}). {@code CAST(… AS text)} 는 {@code ->>} 의 오른쪽이 정수 · 글자 가운데 무엇인지 정해 주려는 것이다.
     *
     * @return 줄마다 {@code [user_id(Long), 티어(String 또는 null)]}. 그 게임에 계정이 없는 사용자는 줄이 없다. {@code userIds} 가 비어 있으면 부르지 마라
     */
    @Query(nativeQuery = true, value = """
            SELECT user_id, tiers ->> CAST(:ladder AS text)
              FROM game_accounts
             WHERE user_id IN (:userIds) AND game = :game
            """)
    List<Object[]> findLadderTiers(@Param("userIds") Collection<Long> userIds, @Param("game") String game,
                                   @Param("ladder") String ladder);

    /** 그 사람의 그 게임 계정의 번호만 — 엔티티를 읽지 않는다(같은 트랜잭션의 네이티브 UPDATE 뒤에 낡은 엔티티가 남지 않게) */
    @Query("select a.id from GameAccount a where a.userId = :userId and a.game = :game")
    Optional<Long> findIdByUserIdAndGame(@Param("userId") Long userId, @Param("game") Game game);

    /**
     * 게임사 API 에서 긁어 온 것을 게임 계정 줄에 적는다 — 게임사 쪽 식별자(LoL 은 {@code puuid}) · <b>사다리별 티어</b>
     * (2026-09-27 소유자 결정 — LoL 은 티어를 요청으로 받지 않고 Riot 에서 채운다. 2026-09-29 — 사다리마다 따로다, P-36. {@code account.stats}).
     * {@code tiers} 는 통째로 갈아 끼운다 — 긁어 온 사다리 전부다({@code account.domain.GameTiers#write} 의 글자).
     * <b>{@code verified} 는 건드리지 않는다</b> — 식별자를 알아낸 것은 본인 확인이 아니다(켜는 길은 아직 없다 — CLAUDE.md §7).
     * <b>{@code game_nickname} 도 건드리지 않는다</b> — 사용자가 적은 값이다.
     * 둘이 다 그대로면 UPDATE 를 내지 않는다({@code where} 절이 가른다 — jsonb 의 같음은 키 순서 · 공백과 무관하다) — 전적만 갱신되는 흔한 경우에
     * {@code updated_at} 이 흔들리지 않게.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE game_accounts
               SET external_id   = CAST(:externalId AS varchar),
                   tiers         = CAST(:tiers AS jsonb),
                   updated_at    = :now
             WHERE id = :id
               AND (external_id   IS DISTINCT FROM CAST(:externalId AS varchar)
                 OR tiers         IS DISTINCT FROM CAST(:tiers AS jsonb))
            """)
    int applyFetchedProfile(@Param("id") Long id, @Param("externalId") String externalId, @Param("tiers") String tiers,
                            @Param("now") Instant now);

    /** 없어도 에러가 아니다 — 지운 줄 수를 돌려준다. 엔티티를 읽어 와서 지우지 않는다(SELECT 없이 DELETE 한 번) */
    @Modifying
    @Query("delete from GameAccount g where g.userId = :userId and g.game = :game")
    int deleteByUserIdAndGame(@Param("userId") Long userId,
                              @Param("game") Game game);
}
