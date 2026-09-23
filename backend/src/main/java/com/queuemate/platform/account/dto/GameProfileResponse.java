package com.queuemate.platform.account.dto;

import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.domain.GameAccountStats;
import com.queuemate.platform.account.domain.GameAccountWithStats;

/**
 * <b>게임 프로필</b> — 게임 계정 하나를 밖에 보여 주는 모양. {@code users/me} 와 목록의 카드가 같이 쓴다
 * ({@code contracts/platform-api.md} "게임 프로필").
 *
 * <p>{@code verified} · {@code stats} 는 읽기 전용이다 — 요청 본문에 그런 칸이 없다({@link GameAccountRequest}).
 * {@code externalId}(게임사 쪽 식별자)는 싣지 않는다 — 계약의 모양에 없다.
 */
public record GameProfileResponse(
        String game,
        String gameNickname,
        boolean verified,
        String tier,
        String mainPosition,
        String server,
        GameStatsResponse stats
) {
    public static GameProfileResponse of(GameAccount account, GameAccountStats stats)
    {
        return new GameProfileResponse(account.getGame().name(), account.getGameNickname(), account.isVerified(),
                account.getTier(), account.getMainPosition(), account.getServer(), GameStatsResponse.from(stats));
    }

    public static GameProfileResponse from(GameAccountWithStats row)
    {
        return of(row.account(), row.stats());
    }
}
