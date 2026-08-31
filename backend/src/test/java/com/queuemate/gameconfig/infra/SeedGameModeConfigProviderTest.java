package com.queuemate.gameconfig.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.gameconfig.domain.GameModeConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedGameModeConfigProviderTest {

    private final SeedGameModeConfigProvider provider = new SeedGameModeConfigProvider();

    @Test
    @DisplayName("지원 게임 세 개의 모드를 모두 제공한다")
    void providesModesForEverySupportedGame() {
        assertThat(provider.activeModes(GameKey.LOL))
                .extracting(GameModeConfig::modeKey).containsExactly("SOLO_DUO_RANKED");
        assertThat(provider.activeModes(GameKey.VALORANT))
                .extracting(GameModeConfig::modeKey).containsExactly("COMPETITIVE", "UNRATED");
        assertThat(provider.activeModes(GameKey.PUBG))
                .extracting(GameModeConfig::modeKey).containsExactly("DUO", "SQUAD");
    }

    @Test
    @DisplayName("LoL 랭크만 포지션 중복을 막는다")
    void onlyLolRankedRequiresRoleUniqueness() {
        assertThat(provider.findActive(GameKey.LOL, "SOLO_DUO_RANKED"))
                .get().extracting(GameModeConfig::roleUniqueness).isEqualTo(true);
        assertThat(provider.findActive(GameKey.VALORANT, "COMPETITIVE"))
                .get().extracting(GameModeConfig::roleUniqueness).isEqualTo(false);
        assertThat(provider.findActive(GameKey.PUBG, "SQUAD"))
                .get().extracting(GameModeConfig::roleUniqueness).isEqualTo(false);
    }

    @Test
    @DisplayName("파티 정원은 모드 설정이 정한다")
    void partySizeComesFromConfig() {
        assertThat(provider.findActive(GameKey.LOL, "SOLO_DUO_RANKED"))
                .get().extracting(GameModeConfig::targetPartySize).isEqualTo(2);
        assertThat(provider.findActive(GameKey.PUBG, "DUO"))
                .get().extracting(GameModeConfig::targetPartySize).isEqualTo(2);
        assertThat(provider.findActive(GameKey.PUBG, "SQUAD"))
                .get().extracting(GameModeConfig::targetPartySize).isEqualTo(4);
    }

    @Test
    @DisplayName("모르는 모드나 다른 게임의 모드는 찾지 못한다")
    void unknownModeIsNotFound() {
        assertThat(provider.findActive(GameKey.LOL, "SQUAD")).isEmpty();
        assertThat(provider.findActive(GameKey.PUBG, "SOLO_DUO_RANKED")).isEmpty();
        assertThat(provider.findActive(GameKey.LOL, null)).isEmpty();
    }

    @Test
    @DisplayName("혼자 하는 모드는 설정으로도 만들 수 없다")
    void rejectsPartySizeBelowTwo() {
        assertThatThrownBy(() -> new GameModeConfig(GameKey.LOL, "SOLO", 1, false, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
