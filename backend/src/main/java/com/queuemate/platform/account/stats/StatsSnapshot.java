package com.queuemate.platform.account.stats;

import java.math.BigDecimal;
import java.util.Map;

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
 * @param wins       <b>시즌 누적 승</b>(솔로랭크) — 읽은 최근 경기(기본 10판 — {@code match-count})의 승패가 아니다. 솔로랭크 줄이 없으면(언랭) {@code null}
 * @param losses     시즌 누적 패. {@code wins} 와 같이 있거나 같이 없다
 * @param winStreak  가장 최근 경기부터 이어지는 연승. 최근 경기가 패면 0 이고, 경기를 하나도 못 읽었으면 {@code null}
 * @param detail     게임마다 다른 나머지를 담은 <b>JSON 객체의 글자</b>(jsonb 로 들어간다). LoL 은 {@code {"mostChampions": […], "recentResults": ["W", "L", …]}} 다
 *                   ({@code recentResults} 는 {@code games} 와 같은 경기의 승 · 패 · 새 경기가 먼저 — 2026-09-30, P-43)
 * @param tiers      <b>{@code game_accounts.tiers} 에 통째로 적는다</b>(2026-09-27 소유자 결정 — 게임사 API 로 채우는 게임은 티어를 요청으로 받지 않는다.
 *                   2026-09-29 — 사다리마다 따로다, P-36). 사다리 키({@code Game#tierLadders()}) → gameconfig 티어 사다리의 이름({@code GOLD_2} · {@code MASTER}).
 *                   LoL 은 {@code SOLO} · {@code FLEX} 둘이다. 언랭이거나 사다리에 없는 이름이면 그 값이 {@code null} 이다(키를 빼도 같다 —
 *                   {@code account.domain.GameTiers#write} 가 {@code null} 을 적지 않는다). {@code null} 이 아니다.
 *                   <b>주 포지션은 여기 없다</b> — 게임사 API 에서 뽑지 않는다(2026-09-27 소유자 결정. 2026-09-29 부터는 게임 계정에 그 칸 자체가 없다 — P-35)
 * @param gameNickname <b>게임사가 지금 알려 준 게임 닉네임</b> — 값이 있으면 {@code game_accounts.game_nickname} 을 이것으로 덮어쓴다(전적과 한 트랜잭션).
 *                   LoL 의 로그인 · 재발급 때 다시 받기가 저장된 {@code puuid} 로 {@code account-v1 by-puuid} 를 불러 채운다(2026-10-03 소유자 결정 ·
 *                   Riot 정책 "historic Riot IDs" · P-53). 그 밖(연결 · PUBG · 응답에 이름이 없을 때)은 {@code null} — 이름을 건드리지 않는다
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
        String detail,
        Map<String, String> tiers,
        String gameNickname
) {

    /** 이름을 건드리지 않는 스냅숏 — 연결 · PUBG 의 길이다({@code gameNickname} 이 {@code null}) */
    public StatsSnapshot(String externalId, int games, BigDecimal avgKills, BigDecimal avgDeaths, BigDecimal avgAssists,
                         Integer wins, Integer losses, Integer winStreak, String detail, Map<String, String> tiers)
    {
        this(externalId, games, avgKills, avgDeaths, avgAssists, wins, losses, winStreak, detail, tiers, null);
    }

    /** 같은 전적에 게임사가 지금 알려 준 이름을 싣는다(P-53) */
    public StatsSnapshot withGameNickname(String currentGameNickname)
    {
        return new StatsSnapshot(externalId, games, avgKills, avgDeaths, avgAssists, wins, losses, winStreak, detail, tiers,
                currentGameNickname);
    }
}
