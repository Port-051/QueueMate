package com.queuemate.gameconfig.api;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.domain.GameModeConfigProvider;
import com.queuemate.matching.domain.KeyCondition;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.PubgPlayStyle;
import com.queuemate.matching.domain.ValorantRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 게임/모드 설정 조회 (docs/05 Game config).
 *
 * <p>프론트가 매칭 폼을 그리는 근거다. 특히 파티 정원은 서버만 알아야 하므로
 * 클라이언트가 보내지 않고 여기서 받아 간다 (docs/03 §9).
 */
@RestController
@RequestMapping("/api/v1/games")
public class GameConfigController {

    private final GameModeConfigProvider modes;

    public GameConfigController(GameModeConfigProvider modes) {
        this.modes = modes;
    }

    @GetMapping
    public List<GameView> games() {
        return Arrays.stream(GameKey.values())
                .map(game -> new GameView(game, KeyCondition.typeOf(game)))
                .toList();
    }

    @GetMapping("/{gameKey}/modes")
    public List<ModeView> modes(@PathVariable GameKey gameKey) {
        return modes.activeModes(gameKey).stream().map(ModeView::of).toList();
    }

    /** 게임 하나의 조건 폼 전체. 선택지를 서버가 한곳에서 알려 준다. */
    @GetMapping("/{gameKey}/match-schema")
    public MatchSchemaView matchSchema(@PathVariable GameKey gameKey) {
        List<ModeView> activeModes = modes.activeModes(gameKey).stream().map(ModeView::of).toList();
        if (activeModes.isEmpty()) {
            throw new NotFoundException("UNKNOWN_GAME", "지원하지 않는 게임이다: " + gameKey);
        }
        return new MatchSchemaView(
                gameKey,
                activeModes,
                new KeyConditionSchema(KeyCondition.typeOf(gameKey), keyConditionValuesOf(gameKey)),
                Arrays.stream(VoicePreference.values()).map(Enum::name).toList(),
                Arrays.stream(PlayPurpose.values()).map(Enum::name).toList());
    }

    private static List<String> keyConditionValuesOf(GameKey game) {
        return switch (game) {
            case LOL -> Arrays.stream(LolPosition.values()).map(Enum::name).toList();
            case VALORANT -> Arrays.stream(ValorantRole.values()).map(Enum::name).toList();
            case PUBG -> Arrays.stream(PubgPlayStyle.values()).map(Enum::name).toList();
        };
    }

    public record GameView(GameKey game, KeyConditionType keyConditionType) {
    }

    public record ModeView(String modeKey, int targetPartySize, boolean roleUniqueness) {
        static ModeView of(GameModeConfig config) {
            return new ModeView(config.modeKey(), config.targetPartySize(), config.roleUniqueness());
        }
    }

    public record KeyConditionSchema(KeyConditionType type, List<String> values) {
    }

    public record MatchSchemaView(
            GameKey game,
            List<ModeView> modes,
            KeyConditionSchema keyCondition,
            List<String> voicePreferences,
            List<String> playPurposes
    ) {
    }
}
