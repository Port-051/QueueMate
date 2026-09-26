package com.queuemate.platform.account.stats;

import java.math.BigDecimal;

/**
 * 게임사 API 에서 긁어 온 전적 — <b>{@code game_account_stats} 한 줄에 그대로 들어가는 모양</b>이다
 * ({@code contracts/platform-api.md} "게임 프로필"). 게임별 구현({@link GameStatsProvider})이 이것을 만들고
 * {@link GameStatsStore} 가 upsert 한다.
 *
 * <p><b>비는 칸이 있다</b>(2026-09-22 소유자 결정 · P-12) — 세 게임이 이 테이블 하나를 같이 쓴다. 판 수만 늘 값이 있다.
 * DB 의 CHECK 가 두 가지를 지킨다 — 값은 음수가 아니고, {@code wins} 와 {@code losses} 는 <b>같이 있거나 같이 없다.</b>
 *
 * @param externalId 게임사 쪽 계정 식별자(LoL 은 {@code puuid}) — {@code game_accounts.external_id} 에 적는다.
 *                   <b>{@code verified} 는 이것으로 켜지 않는다</b>(본인 확인이 아니다 — 켜는 길은 아직 없다)
 * @param games      실제로 읽은 경기 수. 하나도 못 읽었으면 0 이다
 * @param avgKills   읽은 경기들의 평균, 소수 첫째 자리. 경기가 없으면 {@code null}
 * @param avgDeaths  같음
 * @param avgAssists 같음
 * @param wins       <b>시즌 누적 승</b>(솔로랭크) — 읽은 경기 20개의 승패가 아니다. 솔로랭크 줄이 없으면(언랭) {@code null}
 * @param losses     시즌 누적 패. {@code wins} 와 같이 있거나 같이 없다
 * @param winStreak  가장 최근 경기부터 이어지는 연승. 최근 경기가 패면 0 이고, 경기를 하나도 못 읽었으면 {@code null}
 * @param detail     게임마다 다른 나머지를 담은 <b>JSON 객체의 글자</b>(jsonb 로 들어간다). LoL 은 {@code {"mostChampions": […]}} 다
 */
public record StatsSnapshot(
        String externalId,
        int games,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists,
        Integer wins,
        Integer losses,
        Integer winStreak,
        String detail
) {
}
