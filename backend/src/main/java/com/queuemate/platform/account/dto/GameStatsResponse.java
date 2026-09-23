package com.queuemate.platform.account.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.queuemate.platform.account.domain.GameAccountStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * 게임 프로필의 {@code stats} — 전적 스냅숏을 밖에 보여 주는 모양 ({@code contracts/platform-api.md} "게임 프로필").
 *
 * <p>{@code winRate}(정수 퍼센트)와 {@code kda}({@code (킬 + 어시스트) / 데스})는 <b>이 앱이 계산해 내려 준다</b> — 저장하지 않는다.
 *
 * <p><b>게임마다 비는 칸이 다르다</b>(2026-09-22 소유자 결정) — PUBG 는 승/패 · 연승 · 어시스트가 없다.
 * 비는 칸은 <b>JSON 에서 빠지지 않고 {@code null} 로 나간다</b> — 화면이 "정보 없음"으로 그린다.
 *
 * @param games      판 수. 세 게임 모두에 있다 — 늘 값이 있다
 * @param wins       승. PUBG 는 {@code null} 이다. {@code losses} 와 같이 있거나 같이 없다
 * @param losses     패. {@code wins} 와 같이 있거나 같이 없다
 * @param winRate    반올림한 정수 퍼센트. <b>{@code wins} · {@code losses} 가 없거나</b> 그 합이 0 이면 {@code null} 이다
 *                   (0 으로 나눌 수 없다 — 계약에 없는 경우라 보수적으로 비웠다)
 * @param winStreak  연승. PUBG 는 {@code null} 이다
 * @param kda        소수 둘째 자리까지(반올림). 데스가 0 이거나 평균값이 하나라도 없으면 {@code null}
 * @param detail     게임마다 다른 나머지. DB 의 jsonb 를 <b>글자 그대로</b> 싣는다({@link JsonRawValue}) — jsonb 라서 늘 올바른 JSON 이다
 */
public record GameStatsResponse(
        int games,
        Integer wins,
        Integer losses,
        Integer winRate,
        Integer winStreak,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        BigDecimal kda,
        @JsonRawValue String detail,
        Instant syncedAt
) {
    /** 전적이 없으면({@code null}) 응답의 {@code stats} 도 {@code null} 이다 — 화면은 "정보 없음"으로 그린다 */
    public static GameStatsResponse from(GameAccountStats stats)
    {
        if(stats == null)
        {
            return null;
        }
        return new GameStatsResponse(stats.getGames(), stats.getWins(), stats.getLosses(),
                winRate(stats.getWins(), stats.getLosses()), stats.getWinStreak(),
                stats.getAvgKills(), stats.getAvgDeaths(), stats.getAvgAssists(),
                kda(stats.getAvgKills(), stats.getAvgDeaths(), stats.getAvgAssists()),
                stats.getDetail(), stats.getSyncedAt());
    }

    /** 승패가 없는 게임(PUBG)과 판 수가 0 인 계정은 {@code null} 이다 */
    static Integer winRate(Integer wins, Integer losses)
    {
        if(wins == null || losses == null)
        {
            return null;
        }
        long games = (long) wins + losses;
        if(games <= 0)
        {
            return null;
        }
        return (int) Math.round(wins * 100.0 / games);
    }

    static BigDecimal kda(BigDecimal kills, BigDecimal deaths, BigDecimal assists)
    {
        if(kills == null || deaths == null || assists == null || deaths.signum() == 0)
        {
            return null;
        }
        return kills.add(assists).divide(deaths, 2, RoundingMode.HALF_UP);
    }
}
