package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * <p><b>{@code verified} · {@code external_id} 는 이 문장 어디에도 없다</b> — 사용자의 요청으로 바뀌지 않는 칸이다.
     * 새 줄은 컬럼의 기본값({@code false} · {@code NULL})으로 들어가고, 있는 줄은 그 값을 지킨다. 딸린 전적({@code game_account_stats})도 건드리지 않는다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO game_accounts (user_id, game, game_nickname, tier, main_position, server, created_at, updated_at)
            VALUES (:userId, :game, :gameNickname, CAST(:tier AS varchar), CAST(:mainPosition AS varchar),
                    CAST(:server AS varchar), :now, :now)
            ON CONFLICT ON CONSTRAINT game_accounts_user_id_game_key DO UPDATE
               SET game_nickname = EXCLUDED.game_nickname,
                   tier          = EXCLUDED.tier,
                   main_position = EXCLUDED.main_position,
                   server        = EXCLUDED.server,
                   updated_at    = EXCLUDED.updated_at
            """)
    void upsert(@Param("userId") Long userId, @Param("game") String game,
                @Param("gameNickname") String gameNickname, @Param("tier") String tier,
                @Param("mainPosition") String mainPosition, @Param("server") String server,
                @Param("now") Instant now);

    /**
     * 게임사 쪽 계정 식별자(LoL 은 {@code puuid})를 적는다 — 전적을 긁을 때 알게 되는 값이다({@code account.stats}).
     * <b>{@code verified} 는 건드리지 않는다</b> — 식별자를 알아낸 것은 본인 확인이 아니다(켜는 길은 아직 없다 — CLAUDE.md §7).
     * 값이 그대로면 UPDATE 를 내지 않는다({@code where} 절이 가른다) — 전적만 갱신되는 흔한 경우에 {@code updated_at} 이 흔들리지 않게.
     */
    @Modifying
    @Query("""
            update GameAccount a
               set a.externalId = :externalId, a.updatedAt = :now
             where a.id = :id and (a.externalId is null or a.externalId <> :externalId)
            """)
    int updateExternalId(@Param("id") Long id, @Param("externalId") String externalId, @Param("now") Instant now);

    /** 없어도 에러가 아니다 — 지운 줄 수를 돌려준다. 엔티티를 읽어 와서 지우지 않는다(SELECT 없이 DELETE 한 번) */
    @Modifying
    @Query("delete from GameAccount g where g.userId = :userId and g.game = :game")
    int deleteByUserIdAndGame(@Param("userId") Long userId,
                              @Param("game") Game game);
}
