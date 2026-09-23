package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.GameAccountStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 전적 스냅숏을 쓰는 곳. <b>엔티티({@link GameAccountStats})는 {@code @Immutable} 이다</b> — 읽기 전용으로 두고 쓰기는 이 한 문장으로 한다.
 *
 * <p>"있으면 UPDATE 없으면 INSERT" 를 조회로 가르지 않는다 — 같은 계정을 두 번 긁는 일이 겹쳐도(락이 Redis 없이 통과했을 때)
 * {@code ON CONFLICT} 가 한 줄만 남긴다 (CLAUDE.md §5).
 */
public interface GameAccountStatsRepository extends JpaRepository<GameAccountStats, Long> {

    /**
     * 없으면 만들고 있으면 바꾼다 — <b>한 문장이다.</b> PostgreSQL 전용 문법이다(H2 를 쓰지 않는다).
     * {@code CAST} 는 {@code null} 을 넘길 때 드라이버가 자료형을 못 정하는 일을 막는다. {@code detail} 은 jsonb 로 들어간다.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO account.game_account_stats
                   (game_account_id, games, avg_kills, avg_deaths, avg_assists, wins, losses, win_streak,
                    detail, source, synced_at)
            VALUES (:gameAccountId, :games,
                    CAST(:avgKills AS numeric), CAST(:avgDeaths AS numeric), CAST(:avgAssists AS numeric),
                    CAST(:wins AS integer), CAST(:losses AS integer), CAST(:winStreak AS integer),
                    CAST(:detail AS jsonb), :source, :syncedAt)
            ON CONFLICT ON CONSTRAINT game_account_stats_pkey DO UPDATE
               SET games       = EXCLUDED.games,
                   avg_kills   = EXCLUDED.avg_kills,
                   avg_deaths  = EXCLUDED.avg_deaths,
                   avg_assists = EXCLUDED.avg_assists,
                   wins        = EXCLUDED.wins,
                   losses      = EXCLUDED.losses,
                   win_streak  = EXCLUDED.win_streak,
                   detail      = EXCLUDED.detail,
                   source      = EXCLUDED.source,
                   synced_at   = EXCLUDED.synced_at
            """)
    void upsert(@Param("gameAccountId") Long gameAccountId, @Param("games") int games,
                @Param("avgKills") BigDecimal avgKills, @Param("avgDeaths") BigDecimal avgDeaths,
                @Param("avgAssists") BigDecimal avgAssists, @Param("wins") Integer wins,
                @Param("losses") Integer losses, @Param("winStreak") Integer winStreak,
                @Param("detail") String detail, @Param("source") String source,
                @Param("syncedAt") Instant syncedAt);
}
