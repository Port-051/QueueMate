package com.queuemate.platform.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 게임사 API 에서 가져온 전적의 스냅숏. 게임 계정과 1:1 이다 ({@code contracts/platform-api.md} "게임 프로필").
 *
 * <p><b>이 엔티티로는 읽기만 한다.</b> 쓰는 것은 {@code GameAccountStatsRepository#upsert} 한 문장이다 — 그래서 생성자도 세터도 없고
 * {@code @Immutable} 로 Hibernate 가 INSERT · UPDATE 를 만들지 않게 했다. 줄이 없으면 응답의 {@code stats} 가 {@code null} 이다.
 *
 * <p><b>채우는 것은 {@code account.stats} 다</b>(2026-09-23 소유자 결정) — <b>LoL 만</b> Riot API 에서 긁는다(게임 계정을 연결할 때 · 모집 글을 쓸 때.
 * {@code contracts/platform-api.md} "전적을 긁는 것"). VALORANT · PUBG 는 아직 채우는 것이 없어 {@code null} 이다.
 *
 * <p><b>세 게임이 보여 주는 것이 다르다</b>(2026-09-22 소유자 결정) — 세 게임 모두에 있는 {@link #games}(판 수)만 {@code NOT NULL} 이고
 * {@link #wins} · {@link #losses} · {@link #winStreak} 은 {@code null} 일 수 있다(PUBG). 게임마다 다른 나머지는 {@link #detail}(jsonb)이다.
 * 왜 이 모양인지는 마이그레이션({@code V1__schema.sql})의 {@code game_account_stats} 주석에 있다.
 *
 * <p>{@code winRate} · {@code kda} 는 컬럼이 아니다 — 응답을 만들 때 계산한다({@code GameStatsResponse}).
 */
@Entity
@Immutable
@Table(name = "game_account_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameAccountStats {

    @Id
    @Column(name = "game_account_id", nullable = false, updatable = false)
    private Long gameAccountId;

    /** 판 수. <b>세 게임 모두에 있다</b> — 그래서 이 칸만 {@code NOT NULL} 이다 */
    @Column(name = "games", nullable = false)
    private int games;

    @Column(name = "avg_kills", precision = 4, scale = 1)
    private BigDecimal avgKills;

    @Column(name = "avg_deaths", precision = 4, scale = 1)
    private BigDecimal avgDeaths;

    /** PUBG 는 {@code null} 이다 — K/D 만 보여 주고 어시스트를 보여 주지 않는다 */
    @Column(name = "avg_assists", precision = 4, scale = 1)
    private BigDecimal avgAssists;

    /**
     * 승. <b>PUBG 는 {@code null} 이다</b> — 100명 중 순위 싸움이라 "승"이 치킨(1위)이다({@code detail} 에 치킨률로 담는다).
     * {@link #losses} 와 <b>같이 있거나 같이 없다</b> — DB 의 {@code game_account_stats_wins_losses_together_check} 가 지킨다.
     */
    @Column(name = "wins")
    private Integer wins;

    /** 패. {@link #wins} 와 같이 있거나 같이 없다 */
    @Column(name = "losses")
    private Integer losses;

    /** 연승. <b>PUBG 는 {@code null} 이다</b> — 연승의 개념이 약하다 */
    @Column(name = "win_streak")
    private Integer winStreak;

    /**
     * 게임마다 모양이 다른 나머지(모스트 챔피언 · 모스트 요원 · 평균 데미지 등). <b>jsonb 의 글자 그대로</b> 들고 있다가 응답에 그대로 싣는다 —
     * 이 앱은 그 안을 들여다보지 않는다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = false)
    private String detail;

    /** {@code SELF} · {@code API}. 응답에 싣지 않는다 */
    @Column(name = "source", nullable = false, length = 6)
    private String source;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;
}
