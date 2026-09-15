package com.queuemate.gameconfig.domain;

import com.queuemate.common.domain.GameKey;

import java.util.List;
import java.util.Optional;

/**
 * 모드 설정 조회 창구.
 *
 * <p>초기 설정은 코드 seed에 두고, DB(game_mode_configs) 구현으로 갈아끼울 수 있게 인터페이스로 감싼다.
 * 매칭 로직이 설정의 출처를 알 필요는 없다.
 */
public interface GameModeConfigProvider {

    /** 활성 모드만 돌려준다. 비활성 모드로는 새 매칭을 받지 않는다. */
    Optional<GameModeConfig> findActive(GameKey game, String modeKey);

    /** 해당 게임의 활성 모드 목록. 프론트 폼이 모드 선택지를 그리는 데 쓴다. */
    List<GameModeConfig> activeModes(GameKey game);
}
