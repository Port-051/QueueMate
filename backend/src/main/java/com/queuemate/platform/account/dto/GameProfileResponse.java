package com.queuemate.platform.account.dto;

import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.domain.GameAccountStats;
import com.queuemate.platform.account.domain.GameAccountWithStats;

import java.util.Map;

/**
 * <b>게임 프로필</b> — 게임 계정 하나를 밖에 보여 주는 모양. {@code users/me} 와 목록의 카드가 같이 쓴다
 * ({@code contracts/platform-api.md} "게임 프로필").
 *
 * <p>{@code verified} · {@code stats} 는 읽기 전용이다 — 요청 본문에 그런 칸이 없다({@link GameAccountRequest}).
 * {@code externalId}(게임사 쪽 식별자)는 싣지 않는다 — 계약의 모양에 없다.
 * <b>{@code mainPosition}(주 포지션) 칸은 없다</b>(2026-09-29 소유자 결정 — P-35. 게임 계정에서 주 포지션을 없앴다 — 그래서 게시판 카드에도 사람별 포지션이 없다).
 *
 * <p><b>{@code tier} 칸은 없고 {@code tiers} 가 사다리마다의 티어다</b>(2026-09-29 소유자 결정 — P-36). <b>그 게임의 사다리 키가 전부</b> 들어가고
 * 값이 없는 사다리는 {@code null} 이다({@link com.queuemate.platform.account.domain.GameTiers#read}) — LoL {@code {"SOLO":"GOLD_4","FLEX":null}} ·
 * VALORANT {@code {"COMPETITIVE":"GOLD_2"}} · PUBG {@code {"RANKED":null}}. 어느 모드가 어느 사다리를 보는지는 gameconfig 의 {@code tierLadder} 다(프런트가 고른다).
 *
 * @param tiers 사다리 키 → 티어 이름 또는 {@code null}. 순서는 {@code Game#tierLadders()} 다
 */
public record GameProfileResponse(
        String game,
        String gameNickname,
        boolean verified,
        Map<String, String> tiers,
        String server,
        GameStatsResponse stats
) {
    public static GameProfileResponse of(GameAccount account, GameAccountStats stats)
    {
        return new GameProfileResponse(account.getGame().name(), account.getGameNickname(), account.isVerified(),
                account.ladderTiers(), account.getServer(), GameStatsResponse.from(stats));
    }

    public static GameProfileResponse from(GameAccountWithStats row)
    {
        return of(row.account(), row.stats());
    }
}
