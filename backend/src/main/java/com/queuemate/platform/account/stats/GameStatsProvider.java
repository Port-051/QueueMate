package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;

/**
 * 게임 하나의 전적을 긁는 법. {@link com.queuemate.platform.account.oauth.OAuthProviderSpec} 과 같은 자리의 인터페이스다 —
 * 게임이 늘면 이것의 구현 하나를 더한다(빈으로 등록하면 {@link GameStatsSyncWorker} 가 게임별로 찾아 쓴다).
 *
 * <p><b>지금 구현은 LoL 하나다</b>({@link LolStatsProvider} — Riot API). VALORANT 는 Riot 의 <b>별도 승인</b>이 필요하고
 * PUBG 는 다른 API 다 — 구현이 없는 게임은 {@link GameStatsSyncWorker} 가 조용히 끝낸다(CLAUDE.md §7 "게임 계정 연동").
 *
 * <p><b>구현은 게임사 API 를 부르기만 한다</b> — DB 에 쓰지 않고, 락도 신선도도 보지 않는다(그것은 {@link GameStatsSyncWorker} 의 몫이다).
 */
public interface GameStatsProvider {

    Game game();

    /**
     * 그 게임 계정의 전적을 긁는다.
     *
     * @param gameNickname 사용자가 적어 넣은 게임 닉네임. <b>믿을 수 없는 값이다</b> — 형식이 아니면 긁지 않고 {@code null} 을 돌려준다
     * @return 저장할 스냅숏. <b>긁을 수 없으면 {@code null}</b>(형식이 아닌 닉네임 · 없는 계정 등) — 그러면 기존 전적 줄을 건드리지 않는다
     * @throws RiotApiException 게임사 API 가 거절했거나 · 응답이 없다. 부른 쪽이 잡아서 로그만 남긴다
     */
    StatsSnapshot fetch(String gameNickname);
}
