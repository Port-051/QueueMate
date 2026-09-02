package com.queuemate.social.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 완료된 party history에서 직접 조회한다. 별도 테이블을 만들지 않는다 (docs/06).
 * 파티 엔티티는 Phase 3 소관이라 여기서는 읽기 전용 질의로만 접근한다.
 *
 * 닫혔다는 것만으로는 부족하다. 준비 단계에서 한 명이 나가 깨진 파티도 닫힌다.
 * 함께 게임을 한 적이 없는 사람이 최근 함께한 사람에 뜨면 안 되므로
 * 게임에 들어간 적이 있는 파티만 센다. 그 근거가 played_at이다.
 */
@Service
public class RecentPlayerService {

    private static final String QUERY = """
            SELECT u.id                AS user_id,
                   u.nickname          AS nickname,
                   u.avatar_url        AS avatar_url,
                   MAX(mate.joined_at) AS last_played_at,
                   COUNT(DISTINCT p.id) AS play_count,
                   EXISTS (
                       SELECT 1 FROM friendships f
                        WHERE f.user_low_id = LEAST(:userId, u.id)
                          AND f.user_high_id = GREATEST(:userId, u.id)
                   )                   AS friend
              FROM party_members me
              JOIN parties p        ON p.id = me.party_id
              JOIN party_members mate ON mate.party_id = me.party_id
                                     AND mate.user_id <> me.user_id
              JOIN users u          ON u.id = mate.user_id
             WHERE me.user_id = :userId
               AND p.status = 'CLOSED'
               AND p.played_at IS NOT NULL
               AND u.status = 'ACTIVE'
               AND NOT EXISTS (
                   SELECT 1 FROM blocks b
                    WHERE (b.blocker_id = :userId AND b.blocked_id = u.id)
                       OR (b.blocker_id = u.id AND b.blocked_id = :userId)
               )
             GROUP BY u.id, u.nickname, u.avatar_url
             ORDER BY last_played_at DESC
             LIMIT :limit
            """;

    private final JdbcClient jdbcClient;

    public RecentPlayerService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<RecentPlayer> recentPlayers(UUID userId, int limit) {
        return jdbcClient.sql(QUERY)
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, rowNum) -> new RecentPlayer(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("nickname"),
                        rs.getString("avatar_url"),
                        rs.getObject("last_played_at", OffsetDateTime.class),
                        rs.getInt("play_count"),
                        rs.getBoolean("friend")))
                .list();
    }

    public record RecentPlayer(
            UUID userId,
            String nickname,
            String avatarUrl,
            OffsetDateTime lastPlayedAt,
            int playCount,
            boolean friend
    ) {
    }
}
