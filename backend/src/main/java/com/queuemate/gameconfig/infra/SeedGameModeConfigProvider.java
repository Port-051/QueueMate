package com.queuemate.gameconfig.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 초기 모드 설정 (docs/02 §3~§5).
 *
 * <p>세부 모드는 문자열 key로 늘리고 코드 enum을 불필요하게 키우지 않는다.
 *
 * <p>운영 경로에서 쓰지 않는다. source of truth는 game_mode_configs 표이고
 * 애플리케이션은 {@code GameModeCatalog}로 그것을 읽는다. 이 클래스는 DB 없이 도는
 * 단위 테스트의 고정 fixture로만 남긴다. 스프링 빈으로 등록하면 카탈로그와 둘 다
 * 후보가 되어 어느 쪽이 쓰이는지가 조립 순서에 달린다.
 *
 * <p>여기 값과 V3 시드가 어긋나면 단위 테스트는 통과하는데 실제 매칭은 다르게 도는
 * 상태가 된다. {@code GameModeCatalogIntegrationTest}가 둘을 대조해 그것을 막는다.
 */
public class SeedGameModeConfigProvider implements GameModeConfigProvider {

    private final Map<GameKey, List<GameModeConfig>> byGame;

    public SeedGameModeConfigProvider() {
        Map<GameKey, List<GameModeConfig>> configs = new LinkedHashMap<>();
        configs.put(GameKey.LOL, List.of(
                // 자리가 하나뿐이라 포지션 중복을 hard reject 한다.
                new GameModeConfig(GameKey.LOL, "SOLO_DUO_RANKED", 2, true, true)));
        configs.put(GameKey.VALORANT, List.of(
                new GameModeConfig(GameKey.VALORANT, "COMPETITIVE", 5, false, true),
                new GameModeConfig(GameKey.VALORANT, "UNRATED", 5, false, true)));
        configs.put(GameKey.PUBG, List.of(
                new GameModeConfig(GameKey.PUBG, "DUO", 2, false, true),
                new GameModeConfig(GameKey.PUBG, "SQUAD", 4, false, true)));
        this.byGame = Map.copyOf(configs);
    }

    @Override
    public Optional<GameModeConfig> findActive(GameKey game, String modeKey) {
        if (game == null || modeKey == null) {
            return Optional.empty();
        }
        return activeModes(game).stream()
                .filter(config -> config.modeKey().equals(modeKey.trim()))
                .findFirst();
    }

    @Override
    public List<GameModeConfig> activeModes(GameKey game) {
        return byGame.getOrDefault(game, List.of()).stream()
                .filter(GameModeConfig::active)
                .toList();
    }
}
