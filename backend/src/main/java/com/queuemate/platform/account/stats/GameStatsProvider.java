package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;

/**
 * 게임 하나의 전적을 긁는 법. {@link com.queuemate.platform.account.oauth.OAuthProviderSpec} 과 같은 자리의 인터페이스다 —
 * 게임이 늘면 이것의 구현 하나를 더한다(빈으로 등록하면 {@link GameStatsSyncWorker} 가 게임별로 찾아 쓴다).
 *
 * <p><b>지금 구현은 둘이다</b> — LoL({@link LolStatsProvider} — Riot API)과 <b>PUBG</b>({@link PubgStatsProvider} — PUBG API, 2026-09-29 소유자 결정 · P-36).
 * VALORANT 는 Riot 의 <b>별도 승인</b>이 필요해 구현이 없다 — 자기신고로 저장하고 긁지 않는다(CLAUDE.md §7 "게임 계정 연동").
 *
 * <p><b>구현은 게임사 API 를 부르기만 한다</b> — DB 에 쓰지 않고, 락도 보지 않는다(그것은 {@link GameStatsSyncWorker} 의 몫이다).
 * 캐시(PUBG 의 현재 시즌 — {@link PubgSeasonCache})처럼 <b>게임사 API 호출을 줄이는 Redis</b> 는 구현이 볼 수 있다.
 */
public interface GameStatsProvider {

    Game game();

    /**
     * 그 게임사 API 의 키가 있는가 — 없으면 긁지 않는다(게임 계정 연결 · 전적 갱신이 503 이다). 기동은 정상이다.
     * 키는 게임마다 따로다({@code RIOT_API_KEY} · {@code PUBG_API_KEY}).
     */
    boolean configured();

    /**
     * 그 게임 계정의 전적을 긁는다.
     *
     * @param gameNickname 사용자가 적어 넣은 게임 닉네임. <b>믿을 수 없는 값이다</b> — 형식이 아니면 긁지 않고 {@code null} 을 돌려준다
     * @param server       게임 계정의 서버 — PUBG 만 쓴다({@code STEAM} · {@code KAKAO} → PUBG 의 shard). LoL 은 {@code null} 이고 보지 않는다
     * @return 저장할 스냅숏. <b>긁을 수 없으면 {@code null}</b>(형식이 아닌 닉네임 · 서버가 없는 PUBG 계정 등) — 그러면 기존 전적 줄을 건드리지 않는다
     * @throws RiotIdNotFoundException     그 Riot ID 가 Riot 에 없다(LoL)
     * @throws PubgPlayerNotFoundException 그 shard 에 그 닉네임의 플레이어가 없다(PUBG)
     * @throws RiotApiException            Riot API 가 거절했거나 · 응답이 없다
     * @throws PubgApiException            PUBG API 가 거절했거나 · 응답이 없다. 부른 쪽({@link GameStatsRefresher})이 둘 다 503 으로 옮긴다
     */
    StatsSnapshot fetch(String gameNickname, String server);
}
