package com.queuemate.platform.party;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>자동 매칭 전에 조건 맞는 게시판 방에 먼저 합류</b> — {@code POST /api/v1/posts/auto-join}(2026-09-28 소유자 결정 · {@code contracts/platform-api.md}
 * "자동 매칭이 게시판 방에 먼저 합류하는 길" · P-28 · docs/11 D-40). 본문은 {@code matching} 의 매칭 요청과 같은 모양이다.
 *
 * <p>글은 SQL 로 넣고 방은 손으로 연다({@link PostTestSupport#openRoom}) — 여러 방장의 글이 여럿 필요해서다. 입장은 <b>진짜로</b> 거친다(HTTP → 스크립트) — 들어간 사람의
 * 입장 표시 키와 멤버 SET 을 단언하고 끝나면 지운다({@link #track}).
 *
 * <p><b>gameconfig 는 seed 의 모양대로 심는다</b> — {@code ApiTestSupport} 가 모드 HASH 에 {@code targetPartySize} 만 심으므로 여기서 {@code tierRule} 을 보태고, 티어를 안 보는 모드
 * ({@code NORMAL_5} · PUBG {@code NORMAL_DUO_TPP})와 tier-range 표 · 사다리의 나머지 단계를 더 심는다. 있던 키는 건드리지 않고 없어서 심은 것만 끝나고 지운다.
 * 사다리에는 seed 와 같은 score 를 {@code ZADD} 한다 — 소유자의 사다리가 있으면 같은 값이라 바뀌지 않는다.
 */
class AutoJoinTest extends PostTestSupport {

    /** LoL 에서 티어를 안 보는 모드 — seed 의 것이다 */
    private static final String LOL_NORMAL_MODE = "NORMAL_5";
    /** PUBG 에서 티어를 안 보는 모드 — 이름 끝에 시점(TPP)이 접혀 있다 */
    private static final String PUBG_NORMAL_MODE = "NORMAL_DUO_TPP";

    @BeforeEach
    void seedAutoJoinGameConfig()
    {
        // ApiTestSupport 가 심은 RANKED_SOLO 에는 targetPartySize 만 있다 — 티어를 보는 모드로 만든다(소유자의 seed 면 이미 EXIST 다)
        redisTemplate.opsForHash().putIfAbsent("qm:gameconfig:LOL:" + LOL_MODE, "tierRule", "EXIST");
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_NORMAL_MODE, key -> redisTemplate.opsForHash()
                .putAll(key, Map.of("targetPartySize", "5", "positionUniqueness", "true", "tierRule", "NONE")));
        seedIfAbsent("qm:gameconfig:PUBG:" + PUBG_NORMAL_MODE, key -> redisTemplate.opsForHash()
                .putAll(key, Map.of("targetPartySize", "2", "tierRule", "NONE")));
        // 방장 티어 → 허용 범위. seed 의 값이다 — GOLD 는 SILVER_4(9)..PLATINUM_1(20), EMERALD_4 는 PLATINUM_4(17)..EMERALD_1(24), 언랭은 혼자만
        seedIfAbsent("qm:gameconfig:LOL:tier-range:" + LOL_MODE, key -> redisTemplate.opsForHash().putAll(key, Map.of(
                "UNRANKED", "SOLO_ONLY",
                "GOLD_4", "SILVER_4:PLATINUM_1",
                "GOLD_1", "SILVER_4:PLATINUM_1",
                "EMERALD_4", "PLATINUM_4:EMERALD_1")));
        // 범위의 양끝 이름이 사다리에 있어야 단계 번호로 비교할 수 있다 — seed 의 score 그대로다
        String ladder = "qm:gameconfig:LOL:tier";
        redisTemplate.opsForZSet().add(ladder, "SILVER_4", 9);
        redisTemplate.opsForZSet().add(ladder, "PLATINUM_4", 17);
        redisTemplate.opsForZSet().add(ladder, "PLATINUM_1", 20);
        redisTemplate.opsForZSet().add(ladder, "EMERALD_1", 24);
    }

    // ---- 도우미 ----

    private ResultActions autoJoin(Cookie cookie, String body) throws Exception
    {
        return mockMvc.perform(post("/api/v1/posts/auto-join").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** {@code matching} 의 {@code CreateMatchRequestCommand} 모양이다. {@code null} 인 칸은 보내지 않는다 */
    private static String body(String game, String modeKey, String tier, String conditionType, String conditionValue,
                               String voice, String playPurpose)
    {
        StringBuilder json = new StringBuilder("{\"game\":\"" + game + "\",\"modeKey\":\"" + modeKey + "\"");
        if(tier != null)
        {
            json.append(",\"tier\":\"").append(tier).append('"');
        }
        if(conditionType != null)
        {
            json.append(",\"keyCondition\":{\"type\":\"").append(conditionType).append("\",\"value\":\"").append(conditionValue).append("\"}");
        }
        json.append(",\"voicePreference\":\"").append(voice).append('"');
        if(playPurpose != null)
        {
            json.append(",\"playPurpose\":\"").append(playPurpose).append('"');
        }
        return json.append('}').toString();
    }

    /** 랭크(티어를 보는 모드) — GOLD_4 · MID · 음성 켬 */
    private static String rankedBody(String tier, String position)
    {
        return body("LOL", LOL_MODE, tier, "POSITION", position, "REQUIRED", "RANK_UP");
    }

    /** 일반(티어를 안 보는 모드) — 포지션만 */
    private static String normalBody(String position, String voice)
    {
        return body("LOL", LOL_NORMAL_MODE, null, "POSITION", position, voice, "NORMAL");
    }

    /**
     * 모집 중인 글을 SQL 로 넣고 방을 연다(방장 + {@code others}). 글 번호를 돌려준다. 방장은 {@link #insertUser()} 로 만든 사람이어야 한다(FK)
     */
    private Long insertPost(Long hostId, String game, String mode, String voice, String conditionsJson, String[] wanted, Long... others)
    {
        java.sql.Timestamp at = java.sql.Timestamp.from(Instant.now());
        Long postId = jdbcTemplate.queryForObject("insert into recruit_posts "
                + "(host_id, game, mode, title, voice, conditions, status, created_at, updated_at) "
                + "values (?, ?, ?, ?, ?, ?::jsonb, 'RECRUITING', ?, ?) returning id",
                Long.class, hostId, game, mode, "같이 하실 분", voice, conditionsJson, at, at);
        for(String position : wanted)
        {
            jdbcTemplate.update("insert into recruit_post_positions (post_id, position) values (?, ?)", postId, position);
        }
        openRoom(postId, hostId, (Object[]) others);
        return postId;
    }

    private Long insertRankedPost(Long hostId, String... wanted)
    {
        return insertPost(hostId, "LOL", LOL_MODE, "REQUIRED", "{}", wanted);
    }

    private Long insertNormalPost(Long hostId, String voice, String... wanted)
    {
        return insertPost(hostId, "LOL", LOL_NORMAL_MODE, voice, "{}", wanted);
    }

    /** 그 티어의 LoL 게임 계정을 가진 방장 */
    private Long hostWithTier(String tier)
    {
        Long hostId = insertUser();
        insertGameAccount(hostId, "LOL", "host#KR1", tier);
        return hostId;
    }

    /** 200 을 받은 뒤 — 정말 그 방에 들어가 있는지. 끝나면 입장 표시 키를 지우게 적어 둔다 */
    private void assertJoined(ResultActions result, Long postId, Long me) throws Exception
    {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId))
                .andExpect(jsonPath("$.roomId").value(postId));
        track(postId, me);
        assertThat(redisTemplate.opsForSet().members(membersKey(postId))).contains(String.valueOf(me));
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + me)).isEqualTo(String.valueOf(postId));
    }

    private static ResultActions expectNoMatchingPost(ResultActions result) throws Exception
    {
        return result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NO_MATCHING_POST"));
    }

    // ---- ① · ② · ③ ----

    @Test
    @DisplayName("조건 맞는 글이 있으면 200 {postId, roomId}(같은 숫자) 이고 멤버 SET 과 입장 표시 키에 들어가 있다 — 활성 요청 키는 만들지 않는다")
    void joinsMatchingPost() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        Long meId = userIdOf(nickname);
        Long postId = insertRankedPost(hostWithTier("GOLD_1"), "MID", "ADC");

        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), postId, meId);
        assertThat(redisTemplate.hasKey("qm:user:active-request:" + meId)).isFalse();
    }

    @Test
    @DisplayName("맞는 글이 없으면 404 NO_MATCHING_POST — 프런트가 matching 을 부른다")
    void noPostIs404() throws Exception
    {
        Cookie me = login(newNickname());
        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));
    }

    @Test
    @DisplayName("여럿이면 가장 오래된 방(id 가 작은 글)부터")
    void oldestFirst() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        Long older = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        insertRankedPost(hostWithTier("GOLD_1"), "MID");

        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), older, userIdOf(nickname));
    }

    // ---- ④ 음성 · ⑪ PUBG 시점 ----

    @Test
    @DisplayName("voice 가 다른 글은 건너뛴다")
    void skipsDifferentVoice() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        insertNormalPost(insertUser(), "NO_VOICE", "MID");
        expectNoMatchingPost(autoJoin(me, normalBody("MID", "REQUIRED")));

        Long matching = insertNormalPost(insertUser(), "REQUIRED", "MID");
        assertJoined(autoJoin(me, normalBody("MID", "REQUIRED")), matching, userIdOf(nickname));
    }

    @Test
    @DisplayName("PUBG — 글의 perspective 가 모드 이름의 시점(_TPP)과 다르면 건너뛴다. PLATFORM 값은 보지 않는다")
    void skipsDifferentPerspective() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        String pubg = body("PUBG", PUBG_NORMAL_MODE, null, "PLATFORM", "STEAM", "REQUIRED", "FUN");
        insertPost(insertUser(), "PUBG", PUBG_NORMAL_MODE, "REQUIRED", "{\"perspective\":\"FPP\"}", new String[0]);
        expectNoMatchingPost(autoJoin(me, pubg));

        Long tpp = insertPost(insertUser(), "PUBG", PUBG_NORMAL_MODE, "REQUIRED", "{\"perspective\":\"TPP\"}", new String[0]);
        assertJoined(autoJoin(me, pubg), tpp, userIdOf(nickname));
    }

    // ---- ⑤ 포지션 ----

    @Test
    @DisplayName("포지션 — wantedPositions 에 내 포지션이 없으면 건너뛰고, 빈 배열이면 통과한다")
    void positionMustBeWantedOrAnyone() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        insertNormalPost(insertUser(), "REQUIRED", "TOP", "JUNGLE");
        expectNoMatchingPost(autoJoin(me, normalBody("MID", "REQUIRED")));

        Long anyone = insertNormalPost(insertUser(), "REQUIRED");
        assertJoined(autoJoin(me, normalBody("MID", "REQUIRED")), anyone, userIdOf(nickname));
    }

    @Test
    @DisplayName("포지션이 NONE 이거나 keyCondition 이 없으면 wantedPositions 가 빈 글만 맞는다")
    void nonePositionMatchesOnlyOpenPosts() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        insertNormalPost(insertUser(), "REQUIRED", "MID");
        expectNoMatchingPost(autoJoin(me, normalBody("NONE", "REQUIRED")));
        expectNoMatchingPost(autoJoin(me, body("LOL", LOL_NORMAL_MODE, null, null, null, "REQUIRED", null)));

        Long anyone = insertNormalPost(insertUser(), "REQUIRED");
        assertJoined(autoJoin(me, normalBody("NONE", "REQUIRED")), anyone, userIdOf(nickname));
    }

    // ---- ⑥ 티어 ----

    @Test
    @DisplayName("티어를 안 보는 모드(tierRule=NONE)에 tier 를 주면 400 — matching 과 같다")
    void tierOnNoneModeIs400() throws Exception
    {
        Cookie me = login(newNickname());
        autoJoin(me, body("LOL", LOL_NORMAL_MODE, "GOLD_4", "POSITION", "MID", "REQUIRED", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("tier"));
    }

    @Test
    @DisplayName("티어를 보는 모드(tierRule=EXIST)에 tier 가 없으면 · 사다리에 없는 티어면 · SOLO_ONLY 티어면 400")
    void tierOnExistModeIsValidated() throws Exception
    {
        Cookie me = login(newNickname());
        autoJoin(me, rankedBody(null, "MID"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("tier"));
        autoJoin(me, rankedBody(UNKNOWN_TIER, "MID"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("tier"));
        autoJoin(me, rankedBody("UNRANKED", "MID"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("tier"));
        // 없는 모드 · 모르는 게임 · 조건의 종류가 게임과 다르면 그 필드로 400
        autoJoin(me, body("LOL", UNKNOWN_MODE, "GOLD_4", "POSITION", "MID", "REQUIRED", null))
                .andExpect(status().isBadRequest()).andExpect(detailFor("modeKey"));
        autoJoin(me, body("LOL", LOL_MODE, "GOLD_4", "ROLE", "DUELIST", "REQUIRED", null))
                .andExpect(status().isBadRequest()).andExpect(detailFor("keyCondition.type"));
        autoJoin(me, body("LOL", LOL_MODE, "GOLD_4", "POSITION", "MID", "MAYBE", null))
                .andExpect(status().isBadRequest()).andExpect(detailFor("voicePreference"));
    }

    @Test
    @DisplayName("방장 티어의 허용 범위 밖이면 건너뛰고 안이면 들어간다 — 방장 티어가 없거나 SOLO_ONLY 면 건너뛴다")
    void hostTierRangeDecides() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        // EMERALD_4 의 범위 PLATINUM_4(17)..EMERALD_1(24) — 내 GOLD_4(13)는 밖이다
        insertRankedPost(hostWithTier("EMERALD_4"), "MID");
        // 게임 계정이 없는 방장 · 언랭(SOLO_ONLY) 방장
        insertRankedPost(insertUser(), "MID");
        insertRankedPost(hostWithTier("UNRANKED"), "MID");
        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));

        // GOLD_1 의 범위 SILVER_4(9)..PLATINUM_1(20) — 안이다
        Long inRange = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), inRange, userIdOf(nickname));
    }

    // ---- ⑦ 정원 · ⑧ 차단 · ⑨ 다른 방 · ⑩ 확정 ----

    @Test
    @DisplayName("방 안 인원이 그 모드의 targetPartySize(RANKED_SOLO 는 2) 이상이면 건너뛴다 — 방 정원 5 가 아니다")
    void skipsRoomAtModeCapacity() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        insertPost(hostWithTier("GOLD_1"), "LOL", LOL_MODE, "REQUIRED", "{}", new String[]{ "MID" }, insertUser());
        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));

        Long open = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), open, userIdOf(nickname));
    }

    @Test
    @DisplayName("차단 관계인 방장의 글은 건너뛴다 — 입장 검사(PostEntryGate)가 숨긴다")
    void skipsBlockedHost() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        Long blockedHost = hostWithTier("GOLD_1");
        insertRankedPost(blockedHost, "MID");
        block(me, blockedHost);
        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));

        Long other = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), other, userIdOf(nickname));
    }

    @Test
    @DisplayName("이미 다른 방에 있으면 409 IN_OTHER_ROOM — 다음 방으로 넘어가지 않는다")
    void inOtherRoomIs409() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        insertRankedPost(hostWithTier("GOLD_1"), "MID");
        // 내 글을 쓰면 그 방에 들어가 있다
        createLolPost(me, "TOP");

        autoJoin(me, rankedBody("GOLD_4", "MID"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_OTHER_ROOM"));
    }

    @Test
    @DisplayName("확정된 글(DB) · 확정 표시 키가 있는 방 · 사라진 방은 건너뛴다")
    void skipsConfirmedAndGoneRooms() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        java.sql.Timestamp at = java.sql.Timestamp.from(Instant.now());
        Long confirmedInDb = jdbcTemplate.queryForObject("insert into recruit_posts "
                + "(host_id, game, mode, title, voice, conditions, status, created_at, updated_at, confirmed_at) "
                + "values (?, 'LOL', ?, '끝난 글', 'REQUIRED', '{}'::jsonb, 'CONFIRMED', ?, ?, ?) returning id",
                Long.class, hostWithTier("GOLD_1"), LOL_MODE, at, at, at);
        openRoom(confirmedInDb, insertUser());
        Long confirmedInRedis = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        confirmRoom(confirmedInRedis);
        Long gone = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        closeRoom(gone);
        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));
        // 읽기만 했다 — 글의 상태를 옮겨 적지 않는다(그것은 목록 · 단건의 일이다)
        assertThat(statusOf(gone)).isEqualTo("RECRUITING");
    }

    // ---- ⑬ 나갔거나 강퇴당한 방은 10분 동안 건너뛴다 (2026-09-29 소유자 결정) ----

    @Test
    @DisplayName("스스로 나간 방은 10분 동안 자동 합류에서 건너뛴다 — 그 방뿐이면 404, 다른 방이 있으면(더 새 글이어도) 그리로 간다")
    void skipsRoomLeftWithinTenMinutes() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        Long meId = userIdOf(nickname);
        Long left = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), left, meId);

        // 나가기가 no-auto-join 에 그 방을 적는다(leave-room.lua)
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/me", left).cookie(me)).andExpect(status().isNoContent());
        assertThat(redisTemplate.opsForZSet().score("qm:room:no-auto-join:" + meId, String.valueOf(left))).isNotNull();

        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));
        assertThat(redisTemplate.opsForSet().members(membersKey(left))).doesNotContain(String.valueOf(meId));

        // 더 새 글(id 가 큰 방)이라도 나간 방 대신 그리로 간다 — "가장 오래된 방부터" 는 건너뛴 다음의 순서다
        Long other = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), other, meId);
    }

    @Test
    @DisplayName("풀리는 시각이 지난 방은 다시 후보다 — score 를 과거로 두면 그 방으로 들어간다")
    void rejoinsAfterBanExpires() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        Long meId = userIdOf(nickname);
        Long left = insertRankedPost(hostWithTier("GOLD_1"), "MID");
        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), left, meId);
        mockMvc.perform(delete("/api/v1/rooms/{roomId}/members/me", left).cookie(me)).andExpect(status().isNoContent());
        expectNoMatchingPost(autoJoin(me, rankedBody("GOLD_4", "MID")));

        // 10분을 기다리지 않는다 — 풀리는 시각(score)을 과거로
        redisTemplate.opsForZSet().add("qm:room:no-auto-join:" + meId, String.valueOf(left), 1);

        assertJoined(autoJoin(me, rankedBody("GOLD_4", "MID")), left, meId);
    }

    // ---- ⑫ playPurpose ----

    @Test
    @DisplayName("playPurpose 는 있어도 없어도 된다 — 받되 보지 않는다. 같은 사람이 다시 부르면 이미 들어와 있는 그 방으로 200 이다")
    void playPurposeIsOptionalAndIgnored() throws Exception
    {
        String nickname = newNickname();
        Cookie me = login(nickname);
        Long meId = userIdOf(nickname);
        Long postId = insertRankedPost(hostWithTier("GOLD_1"), "MID");

        assertJoined(autoJoin(me, body("LOL", LOL_MODE, "GOLD_4", "POSITION", "MID", "REQUIRED", null)), postId, meId);
        assertJoined(autoJoin(me, body("LOL", LOL_MODE, "GOLD_4", "POSITION", "MID", "REQUIRED", "FUN")), postId, meId);
        assertThat(redisTemplate.opsForSet().size(membersKey(postId))).isEqualTo(2);
    }
}
