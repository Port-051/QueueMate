package com.queuemate.gameconfig.domain;

import com.queuemate.common.domain.GameKey;

/**
 * 게임 모드 하나의 매칭 규칙 (docs/06 game_mode_configs).
 *
 * <p>파티 정원은 여기서만 정한다. 클라이언트가 정원을 보내지 않는다 (docs/03 §9).
 *
 * @param targetPartySize 확정 파티의 인원. 2 미만은 파티가 아니다
 * @param roleUniqueness  같은 파티 안에서 key condition 값이 겹치면 안 되는 모드인지.
 *                        LoL 랭크처럼 자리가 하나뿐인 경우에만 true다
 */
public record GameModeConfig(
        GameKey game,
        String modeKey,
        int targetPartySize,
        boolean roleUniqueness,
        boolean active
) {

    public GameModeConfig {
        if (game == null) {
            throw new IllegalArgumentException("game은 필수다");
        }
        if (modeKey == null || modeKey.isBlank()) {
            throw new IllegalArgumentException("modeKey는 필수다");
        }
        if (targetPartySize < 2) {
            throw new IllegalArgumentException("파티 정원은 2 이상이어야 한다: " + targetPartySize);
        }
        modeKey = modeKey.trim();
    }
}
