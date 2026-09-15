package com.queuemate.gameconfig.api;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.KeyConditionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameConfigControllerTest {

    private final GameConfigController controller =
            new GameConfigController(new com.queuemate.gameconfig.infra.SeedGameModeConfigProvider());

    @Test
    @DisplayName("지원 게임과 각 게임의 조건 종류를 알려 준다")
    void listsSupportedGames() {
        assertThat(controller.games())
                .extracting(GameConfigController.GameView::game)
                .containsExactly(GameKey.LOL, GameKey.VALORANT, GameKey.PUBG);
        assertThat(controller.games())
                .extracting(GameConfigController.GameView::keyConditionType)
                .containsExactly(KeyConditionType.POSITION, KeyConditionType.ROLE,
                        KeyConditionType.PLAY_STYLE);
    }

    @Test
    @DisplayName("모드 목록에 파티 정원이 실린다. 클라이언트가 정원을 정하지 않는다")
    void modesCarryTargetPartySize() {
        assertThat(controller.modes(GameKey.PUBG))
                .containsExactly(
                        new GameConfigController.ModeView("DUO", 2, false),
                        new GameConfigController.ModeView("SQUAD", 4, false));
    }

    @Test
    @DisplayName("조건 스키마는 게임별 선택지를 모두 담는다")
    void matchSchemaCarriesEveryChoice() {
        GameConfigController.MatchSchemaView schema = controller.matchSchema(GameKey.LOL);

        assertThat(schema.keyCondition().type()).isEqualTo(KeyConditionType.POSITION);
        assertThat(schema.keyCondition().values())
                .containsExactly("TOP", "JUNGLE", "MID", "ADC", "SUPPORT", "ANY");
        assertThat(schema.voicePreferences()).containsExactly("REQUIRED", "OPTIONAL", "NO_VOICE");
        assertThat(schema.playPurposes()).containsExactly("RANK_UP", "NORMAL", "FUN");
        assertThat(schema.modes()).hasSize(1);
    }

    @Test
    @DisplayName("활성 모드가 하나도 없는 게임은 404다")
    void unknownGameIsNotFound() {
        GameConfigController empty = new GameConfigController(new GameModeConfigProvider() {
            @Override
            public Optional<GameModeConfig> findActive(GameKey game, String modeKey) {
                return Optional.empty();
            }

            @Override
            public List<GameModeConfig> activeModes(GameKey game) {
                return List.of();
            }
        });

        assertThatThrownBy(() -> empty.matchSchema(GameKey.LOL))
                .isInstanceOf(NotFoundException.class);
    }
}
