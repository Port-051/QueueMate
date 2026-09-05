package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** docs/15 §4 TC-GAME. */
class GameConfigApiContractTest extends ApiContractTestSupport {

    private static List<String> texts(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asText()));
        return values;
    }

    @Test
    @DisplayName("TC-GAME-01 지원 게임 3개와 각 게임의 조건 종류를 준다")
    void listsThreeGamesWithConditionTypes() {
        UUID alpha = alpha();

        JsonNode games = body(get("/api/v1/games", alpha));

        assertEquals(3, games.size());
        assertEquals(List.of("LOL", "VALORANT", "PUBG"), texts(games, "game"));
        assertEquals(List.of("POSITION", "ROLE", "PLAY_STYLE"), texts(games, "keyConditionType"));
    }

    @Test
    @DisplayName("TC-GAME-02 게임 목록도 인증이 필요하다")
    void gamesAreNotPublic() {
        assertStatus(get("/api/v1/games", null), 401);
    }

    @Test
    @DisplayName("TC-GAME-03 PUBG 모드는 DUO 2인과 SQUAD 4인이다")
    void pubgModesCarryPartySize() {
        UUID alpha = alpha();

        JsonNode modes = body(get("/api/v1/games/PUBG/modes", alpha));

        assertEquals(2, modes.size());
        assertEquals(List.of("DUO", "SQUAD"), texts(modes, "modeKey"));
        assertEquals(2, modes.get(0).path("targetPartySize").asInt());
        assertEquals(4, modes.get(1).path("targetPartySize").asInt());
    }

    @Test
    @DisplayName("TC-GAME-04 LoL 랭크만 role uniqueness를 요구한다")
    void onlyLolRankedRequiresUniquePositions() {
        UUID alpha = alpha();

        JsonNode lol = body(get("/api/v1/games/LOL/modes", alpha));
        JsonNode valorant = body(get("/api/v1/games/VALORANT/modes", alpha));

        assertEquals("SOLO_DUO_RANKED", lol.get(0).path("modeKey").asText());
        assertTrue(lol.get(0).path("roleUniqueness").asBoolean());
        valorant.forEach(mode ->
                assertFalse(mode.path("roleUniqueness").asBoolean(),
                        "발로란트는 역할이 겹쳐도 게임이 성립한다"));
    }

    @Test
    @DisplayName("TC-GAME-05 LoL 조건 스키마에는 ANY가 들어 있다")
    void lolSchemaHasAny() {
        UUID alpha = alpha();

        JsonNode schema = body(get("/api/v1/games/LOL/match-schema", alpha));

        assertEquals("POSITION", schema.path("keyCondition").path("type").asText());
        List<String> values = new ArrayList<>();
        schema.path("keyCondition").path("values").forEach(node -> values.add(node.asText()));
        assertEquals(List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT", "ANY"), values);
        assertEquals(3, schema.path("voicePreferences").size());
        assertEquals(3, schema.path("playPurposes").size());
    }

    @Test
    @DisplayName("TC-GAME-06 발로란트에는 ANY가 없다. 세 게임에 같은 버튼을 그리면 안 된다")
    void valorantSchemaHasNoAny() {
        UUID alpha = alpha();

        JsonNode schema = body(get("/api/v1/games/VALORANT/match-schema", alpha));

        List<String> values = new ArrayList<>();
        schema.path("keyCondition").path("values").forEach(node -> values.add(node.asText()));
        assertEquals(List.of("DUELIST", "INITIATOR", "CONTROLLER", "SENTINEL"), values);
        assertFalse(values.contains("ANY"));
    }

    @Test
    @DisplayName("TC-GAME-07 지원하지 않는 게임은 경로 변수 변환에서 400이다")
    void unknownGameKeyIsBadRequest() {
        UUID alpha = alpha();

        // enum 경로 변수는 MethodArgumentTypeMismatchException이라 UNKNOWN_GAME(404)까지
        // 못 간다. 컨트롤러 안의 404는 "enum에는 있는데 활성 모드가 없는 게임"용이다.
        assertError(get("/api/v1/games/OVERWATCH/modes", alpha), 400, "VALIDATION_FAILED");
    }
}
