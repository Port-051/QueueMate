package com.queuemate.matching.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.gameconfig.infra.SeedGameModeConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code harness/fixtures/}의 사람이 읽는 케이스를 그대로 돌린다 (docs/08 §3).
 *
 * <p>fixture와 구현이 갈라지면 여기서 깨진다. 규칙을 바꾸려면 fixture도 같이 바꿔야 한다.
 */
class ConditionCompatibilityFixtureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path FIXTURES = Path.of("..", "harness", "fixtures");
    private static final SeedGameModeConfigProvider CONFIGS = new SeedGameModeConfigProvider();

    /** fixture 파일 하나가 어떤 게임/모드/조건 필드를 뜻하는지. */
    private record FixtureFile(String fileName, GameKey game, String modeKey, String keyField) {
    }

    private static final List<FixtureFile> FILES = List.of(
            new FixtureFile("lol.json", GameKey.LOL, "SOLO_DUO_RANKED", "position"),
            new FixtureFile("valorant.json", GameKey.VALORANT, "COMPETITIVE", "role"),
            new FixtureFile("pubg.json", GameKey.PUBG, "SQUAD", "style"));

    private record Case(GameKey game, String modeKey, String name,
                        MatchCondition a, MatchCondition b, String expected) {
        @Override
        public String toString() {
            return game + " " + name;
        }
    }

    static Stream<Case> fixtureCases() throws IOException {
        List<Case> cases = new ArrayList<>();
        for (FixtureFile file : FILES) {
            Path path = FIXTURES.resolve(file.fileName());
            assertThat(Files.exists(path))
                    .withFailMessage("fixture가 없다: %s (작업 디렉토리: %s)",
                            path.toAbsolutePath(), Path.of("").toAbsolutePath())
                    .isTrue();
            JsonNode root = MAPPER.readTree(Files.readString(path));
            for (JsonNode node : root.get("cases")) {
                cases.add(new Case(
                        file.game(), file.modeKey(), node.get("name").asText(),
                        toCondition(file, node.get("a")),
                        toCondition(file, node.get("b")),
                        node.get("expected").asText()));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureCases")
    void matchesFixtureExpectation(Case testCase) {
        Optional<CompatibilityTier> tier = evaluate(testCase);

        if ("INCOMPATIBLE".equals(testCase.expected())) {
            assertThat(tier)
                    .withFailMessage("%s 는 같은 파티가 될 수 없어야 한다", testCase.name())
                    .isEmpty();
        } else {
            assertThat(tier)
                    .withFailMessage("%s 는 호환되어야 한다", testCase.name())
                    .isPresent();
        }
    }

    @Test
    @DisplayName("fixture가 HIGHER라고 표시한 조합이 LOWER 조합보다 좋은 등급이다")
    void higherTierBeatsLowerTier() throws IOException {
        List<Case> cases = fixtureCases().toList();

        for (FixtureFile file : FILES) {
            Optional<CompatibilityTier> higher = tierOf(cases, file.game(), "COMPATIBLE_HIGHER_TIER");
            Optional<CompatibilityTier> lower = tierOf(cases, file.game(), "COMPATIBLE_LOWER_TIER");
            if (higher.isEmpty() || lower.isEmpty()) {
                continue; // 그 게임 fixture에 비교 쌍이 없다
            }
            assertThat(higher.get().isBetterThan(lower.get()))
                    .withFailMessage("%s: HIGHER(%s)가 LOWER(%s)보다 좋아야 한다",
                            file.game(), higher.get(), lower.get())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("VALORANT는 역할이 겹쳐도 거절하지 않는다")
    void valorantAllowsDuplicateRole() {
        GameModeConfig config = config(GameKey.VALORANT, "COMPETITIVE");
        MatchCondition duelist = new MatchCondition(GameKey.VALORANT, "COMPETITIVE",
                ValorantRole.DUELIST, VoicePreference.OPTIONAL, PlayPurpose.NORMAL);

        assertThat(ConditionCompatibility.between(duelist, duelist, config)).isPresent();
    }

    @Test
    @DisplayName("LoL에서 ANY를 고른 사람은 어떤 포지션과도 자리를 다투지 않는다")
    void lolAnyDoesNotConflict() {
        GameModeConfig config = config(GameKey.LOL, "SOLO_DUO_RANKED");
        MatchCondition any = lol(LolPosition.ANY, VoicePreference.OPTIONAL, PlayPurpose.NORMAL);
        MatchCondition jungle = lol(LolPosition.JUNGLE, VoicePreference.OPTIONAL, PlayPurpose.NORMAL);

        assertThat(ConditionCompatibility.between(any, jungle, config)).isPresent();
        assertThat(ConditionCompatibility.between(any, any, config)).isPresent();
        // 다만 명시한 포지션이 보장되지 않으므로 최적은 아니다.
        assertThat(ConditionCompatibility.between(any, jungle, config))
                .contains(CompatibilityTier.GOOD);
    }

    @Test
    @DisplayName("정원을 넘는 조합은 파티가 되지 않는다")
    void rejectsPartyLargerThanTargetSize() {
        GameModeConfig config = config(GameKey.LOL, "SOLO_DUO_RANKED"); // 정원 2
        List<MatchCondition> three = List.of(
                lol(LolPosition.TOP, VoicePreference.OPTIONAL, PlayPurpose.NORMAL),
                lol(LolPosition.MID, VoicePreference.OPTIONAL, PlayPurpose.NORMAL),
                lol(LolPosition.ADC, VoicePreference.OPTIONAL, PlayPurpose.NORMAL));

        assertThat(ConditionCompatibility.forParty(three, config)).isEmpty();
    }

    @Test
    @DisplayName("파티 등급은 가장 나쁜 쌍이 결정한다")
    void partyTierIsTheWorstPair() {
        GameModeConfig config = config(GameKey.VALORANT, "COMPETITIVE");
        List<MatchCondition> party = List.of(
                valorant(ValorantRole.DUELIST, VoicePreference.REQUIRED, PlayPurpose.RANK_UP),
                valorant(ValorantRole.CONTROLLER, VoicePreference.REQUIRED, PlayPurpose.RANK_UP),
                // 이 사람만 목적이 다르다. 전체 등급이 여기에 끌려 내려간다.
                valorant(ValorantRole.SENTINEL, VoicePreference.REQUIRED, PlayPurpose.FUN));

        assertThat(ConditionCompatibility.forParty(party, config)).contains(CompatibilityTier.GOOD);
    }

    @Test
    @DisplayName("한 쌍이라도 음성 조건이 충돌하면 파티가 되지 않는다")
    void partyFailsOnOneVoiceConflict() {
        GameModeConfig config = config(GameKey.VALORANT, "COMPETITIVE");
        List<MatchCondition> party = List.of(
                valorant(ValorantRole.DUELIST, VoicePreference.REQUIRED, PlayPurpose.RANK_UP),
                valorant(ValorantRole.CONTROLLER, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP),
                valorant(ValorantRole.SENTINEL, VoicePreference.NO_VOICE, PlayPurpose.RANK_UP));

        assertThat(ConditionCompatibility.forParty(party, config)).isEmpty();
    }

    private Optional<CompatibilityTier> tierOf(List<Case> cases, GameKey game, String expected) {
        return cases.stream()
                .filter(c -> c.game() == game && c.expected().equals(expected))
                .findFirst()
                .flatMap(this::evaluate);
    }

    private Optional<CompatibilityTier> evaluate(Case testCase) {
        return ConditionCompatibility.between(
                testCase.a(), testCase.b(), config(testCase.game(), testCase.modeKey()));
    }

    private static GameModeConfig config(GameKey game, String modeKey) {
        return CONFIGS.findActive(game, modeKey).orElseThrow();
    }

    private static MatchCondition toCondition(FixtureFile file, JsonNode node) {
        return new MatchCondition(
                file.game(), file.modeKey(),
                KeyCondition.of(file.game(), KeyCondition.typeOf(file.game()),
                        node.get(file.keyField()).asText()),
                VoicePreference.valueOf(node.get("voice").asText()),
                PlayPurpose.valueOf(node.get("purpose").asText()));
    }

    private static MatchCondition lol(LolPosition position, VoicePreference voice, PlayPurpose purpose) {
        return new MatchCondition(GameKey.LOL, "SOLO_DUO_RANKED", position, voice, purpose);
    }

    private static MatchCondition valorant(ValorantRole role, VoicePreference voice, PlayPurpose purpose) {
        return new MatchCondition(GameKey.VALORANT, "COMPETITIVE", role, voice, purpose);
    }
}
