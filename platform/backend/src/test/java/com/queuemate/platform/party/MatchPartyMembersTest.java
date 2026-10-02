package com.queuemate.platform.party;

import com.queuemate.platform.ApiTestSupport;
import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.dto.MatchPartyMembersResponse;
import com.queuemate.platform.party.match.MatchPartyReader;
import com.queuemate.platform.party.service.MatchPartyService;
import com.queuemate.platform.party.service.MatchPartyStore;
import com.queuemate.platform.room.service.RoomService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>퀵 매칭 파티의 팀원 카드</b>(2026-10-01 소유자 결정) — {@code GET /api/v1/match-parties/{partyId}/members?game=}. 파티원만 볼 수 있고(공개 조회가 아니다),
 * 제안 중이면 {@code matching} 의 파티 HASH 를, 그것이 수명으로 사라진 뒤에는 DB 의 기록을 본다. 내용은 게시판 카드 수준이다 — 닉네임 · 고른 포지션 · 그 게임의 프로필.
 *
 * <p><b>{@code matching} 인 척 파티 HASH 를 이 테스트가 직접 심는다</b> — 제안 중({@code PENDING} · {@code game} 이 아직 없다)과 확정({@code CONFIRMED} · {@code game} 있음)의
 * 모양은 {@code matching} 의 {@code join-party*.lua} · {@code proposal/cleanup-confirmed.lua} 머리의 것이다. 확정 뒤 DB 의 기록({@code parties} · {@code party_members})도
 * SQL 로 넣는다. 둘 다 끝나면 지운다. <b>{@code FLUSHDB} 금지</b>는 그대로다.
 */
class MatchPartyMembersTest extends ApiTestSupport {

    @Autowired
    private MatchPartyStore matchPartyStore;

    @Autowired
    private RoomService roomService;

    @Autowired
    private GameProfileReader gameProfileReader;

    /** 이 테스트가 심은 파티 HASH · DB 의 파티 — 끝나면 지운다 */
    private final List<String> seeded = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteSeededParties()
    {
        for(String partyId : seeded)
        {
            redisTemplate.delete("qm:party:" + partyId);
            // 파티원은 parties 의 FK(ON DELETE CASCADE)로 같이 지워진다
            jdbcTemplate.update("delete from parties where match_party_id = ?", partyId);
        }
        seeded.clear();
    }

    // ---- 도우미 ----

    /**
     * 파티 HASH 를 심는다. {@code status} 가 {@code null} 이면 그 칸을 쓰지 않는다(아직 사람을 모으는 파티), {@code game} 이 {@code null} 이면 쓰지 않는다(제안 중).
     *
     * @param members 사용자 번호 → {@code member:} 값(keyValue)
     */
    private String seedHash(String status, String game, Map<Long, String> members)
    {
        String partyId = UUID.randomUUID().toString();
        seeded.add(partyId);
        String key = "qm:party:" + partyId;
        if(status != null)
        {
            redisTemplate.opsForHash().put(key, "status", status);
        }
        if(game != null)
        {
            redisTemplate.opsForHash().put(key, "game", game);
            redisTemplate.opsForHash().put(key, "modeKey", LOL_MODE_2);
        }
        redisTemplate.opsForHash().put(key, "target", String.valueOf(members.size()));
        members.forEach((userId, value) -> redisTemplate.opsForHash().put(key, "member:" + userId, value));
        redisTemplate.expire(key, Duration.ofSeconds(600));
        return partyId;
    }

    /** 확정 뒤 이 앱이 적은 기록 — {@code parties}({@code source = MATCH}) · {@code party_members}. 파티 HASH 는 없다(수명으로 사라졌다) */
    private String seedRecorded(String game, Long... members)
    {
        String partyId = UUID.randomUUID().toString();
        seeded.add(partyId);
        Long id = jdbcTemplate.queryForObject("insert into parties (source, match_party_id, game, status, created_at) "
                + "values ('MATCH', ?, ?, 'ACTIVE', now()) returning id", Long.class, partyId, game);
        for(int i = 0; i < members.length; i++)
        {
            jdbcTemplate.update("insert into party_members (party_id, user_id, is_host, joined_at) values (?, ?, ?, now())",
                    id, members[i], i == 0);
        }
        return partyId;
    }

    private ResultActions members(Cookie cookie, String partyId, String game) throws Exception
    {
        MockHttpServletRequestBuilder request = get("/api/v1/match-parties/" + partyId + "/members").cookie(cookie);
        if(game != null)
        {
            request.param("game", game);
        }
        return mockMvc.perform(request);
    }

    private static Map<Long, String> membersOf(Object... userIdsAndValues)
    {
        Map<Long, String> members = new LinkedHashMap<>();
        for(int i = 0; i < userIdsAndValues.length; i += 2)
        {
            members.put((Long) userIdsAndValues[i], (String) userIdsAndValues[i + 1]);
        }
        return members;
    }

    // ---- 파티 HASH 로 답한다 ----

    @Test
    @DisplayName("제안 중인 파티(PENDING · game 이 아직 없다)는 ?game= 으로 그 게임의 카드를 준다 — 포지션은 HASH 의 값, NONE 은 null, 가입하지 않은 번호도 빼지 않는다. 닉네임순")
    void pendingPartyWithGameQuery() throws Exception
    {
        String a = newNickname();
        String b = newNickname();
        Cookie aCookie = login(a);
        login(b);
        Long aId = userIdOf(a);
        Long bId = userIdOf(b);
        Long stranger = unknownUserId();
        insertGameAccount(aId, "LOL", "a#KR1", "GOLD_1");
        String partyId = seedHash("PENDING", null, membersOf(aId, "TOP", bId, "MID", stranger, "NONE"));

        List<Long> expectedOrder = new ArrayList<>(a.compareTo(b) < 0 ? List.of(aId, bId) : List.of(bId, aId));
        expectedOrder.add(stranger);
        String body = members(aCookie, partyId, "LOL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyId").value(partyId))
                .andExpect(jsonPath("$.members.length()").value(3))
                .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].nickname", contains(a)))
                .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].position", contains("TOP")))
                .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].profile.gameNickname", contains("a#KR1")))
                .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].profile.tiers.SOLO", contains("GOLD_1")))
                .andExpect(jsonPath("$.members[?(@.userId == " + bId + ")].position", contains("MID")))
                .andExpect(jsonPath("$.members[?(@.userId == " + bId + ")].profile", contains(nullValue())))
                // 가입하지 않은 번호 — 게시판 카드처럼 닉네임 · 프로필이 null 로 남는다. NONE(포지션이 없는 모드의 값)은 포지션이 아니다
                .andExpect(jsonPath("$.members[2].nickname").value(nullValue()))
                .andExpect(jsonPath("$.members[2].profile").value(nullValue()))
                .andExpect(jsonPath("$.members[2].position").value(nullValue()))
                // 게시판 카드와 달리 방장이 없다
                .andExpect(jsonPath("$.members[0].host").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // 닉네임순 → 닉네임이 없는 사람은 뒤로(게시판 카드와 같은 순서)
        List<Long> order = new ArrayList<>();
        objectMapper.readTree(body).get("members").forEach(card -> order.add(card.get("userId").asLong()));
        assertThat(order).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    @DisplayName("제안 중인 파티에 game 을 안 주거나 모르는 이름이면 400 VALIDATION_FAILED(game) 다")
    void pendingPartyNeedsGame() throws Exception
    {
        String a = newNickname();
        Cookie aCookie = login(a);
        String partyId = seedHash("PENDING", null, membersOf(userIdOf(a), "TOP"));

        for(String game : new String[]{null, "", "lol", "OVERWATCH"})
        {
            members(aCookie, partyId, game)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(detailFor("game"));
        }
    }

    @Test
    @DisplayName("확정된 파티는 HASH 의 game 을 쓴다 — 쿼리가 없어도 · 다른 게임을 줘도 그 파티의 게임(LoL)으로 답한다. PUBG 는 포지션이 없어 null 이다")
    void confirmedPartyUsesItsGame() throws Exception
    {
        String a = newNickname();
        String b = newNickname();
        Cookie aCookie = login(a);
        Cookie bCookie = login(b);
        Long aId = userIdOf(a);
        Long bId = userIdOf(b);
        insertGameAccount(aId, "LOL", "a#KR1", "GOLD_1");
        String partyId = seedHash("CONFIRMED", "LOL", membersOf(aId, "JUNGLE", bId, "SUPPORT"));

        for(String game : new String[]{null, "PUBG", "nonsense"})
        {
            members(aCookie, partyId, game)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(2))
                    .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].position", contains("JUNGLE")))
                    .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].profile.gameNickname", contains("a#KR1")))
                    .andExpect(jsonPath("$.members[?(@.userId == " + bId + ")].position", contains("SUPPORT")));
        }

        // PUBG 파티 — member: 값은 자리 채움(EXIST)이라 포지션이 아니다
        String pubg = seedHash("CONFIRMED", "PUBG", membersOf(aId, "EXIST", bId, "EXIST"));
        members(bCookie, pubg, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].position", everyItem(nullValue())));
    }

    @Test
    @DisplayName("VALORANT HASH 에 섞인 tier:{userId} · tierLo · tierHi 는 사람이 아니다 — member: 필드만 팀원이고 역할군이 포지션이다. 값이 \"\" 이면 null")
    void onlyMemberFieldsArePeople() throws Exception
    {
        String a = newNickname();
        String b = newNickname();
        Cookie aCookie = login(a);
        login(b);
        Long aId = userIdOf(a);
        Long bId = userIdOf(b);
        String partyId = seedHash("CONFIRMED", "VALORANT", membersOf(aId, "DUELIST", bId, ""));
        // matching 의 VALORANT 배정 스크립트가 같이 적는 칸들 — 사용자 번호가 들어 있어도 팀원이 아니다
        String key = "qm:party:" + partyId;
        redisTemplate.opsForHash().put(key, "tier:" + aId, "12");
        redisTemplate.opsForHash().put(key, "tier:" + bId, "14");
        redisTemplate.opsForHash().put(key, "tierLo", "12");
        redisTemplate.opsForHash().put(key, "tierHi", "14");

        members(aCookie, partyId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].position", contains("DUELIST")))
                .andExpect(jsonPath("$.members[?(@.userId == " + bId + ")].position", contains(nullValue())));
    }

    // ---- DB 의 기록으로 답한다 ----

    @Test
    @DisplayName("파티 HASH 가 사라진 뒤에는 DB 의 기록(party_members)으로 답한다 — 게임은 parties.game, 포지션은 null")
    void recordedPartyAfterTheHashExpired() throws Exception
    {
        String a = newNickname();
        String b = newNickname();
        Cookie aCookie = login(a);
        login(b);
        Long aId = userIdOf(a);
        Long bId = userIdOf(b);
        insertGameAccount(bId, "LOL", "b#KR1", "SILVER_1");
        String partyId = seedRecorded("LOL", aId, bId);

        members(aCookie, partyId, "VALORANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partyId").value(partyId))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[*].position", everyItem(nullValue())))
                .andExpect(jsonPath("$.members[?(@.userId == " + bId + ")].profile.gameNickname", contains("b#KR1")))
                .andExpect(jsonPath("$.members[?(@.userId == " + aId + ")].nickname", contains(a)));
    }

    // ---- 자격 ----

    @Test
    @DisplayName("파티원이 아니면 403 NOT_PARTY_MEMBER 이고 팀원 정보가 하나도 나가지 않는다 — 제안 중 · 확정 HASH · DB 의 기록 셋 다")
    void outsiderIsForbidden() throws Exception
    {
        String a = newNickname();
        String outsider = newNickname();
        login(a);
        Cookie outsiderCookie = login(outsider);
        Long aId = userIdOf(a);
        insertGameAccount(aId, "LOL", "secret#KR1", "GOLD_1");

        for(String partyId : List.of(seedHash("PENDING", null, membersOf(aId, "TOP")),
                seedHash("CONFIRMED", "LOL", membersOf(aId, "TOP")),
                seedRecorded("LOL", aId)))
        {
            // 남의 파티 번호를 알아도 — 게임을 줘도 안 줘도 — 게임보다 자격을 먼저 본다
            for(String game : new String[]{null, "LOL"})
            {
                String body = members(outsiderCookie, partyId, game)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("NOT_PARTY_MEMBER"))
                        .andExpect(jsonPath("$.members").doesNotExist())
                        .andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain(a).doesNotContain("secret#KR1").doesNotContain(String.valueOf(aId));
            }
        }
    }

    @Test
    @DisplayName("없는 파티는 404 MATCH_PARTY_NOT_FOUND 다 — 아무 데도 없는 id · UUID 가 아닌 경로 · 아직 사람을 모으는 파티(status 가 없다)")
    void unknownPartyIsNotFound() throws Exception
    {
        String a = newNickname();
        Cookie aCookie = login(a);

        members(aCookie, UUID.randomUUID().toString(), "LOL")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));
        members(aCookie, "not-a-uuid", "LOL")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));
        // 정원이 차기 전의 파티는 매칭된 파티가 아니다 — 내가 그 안에 있어도 없는 파티다
        String forming = seedHash(null, null, membersOf(userIdOf(a), "TOP"));
        members(aCookie, forming, "LOL")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));
    }

    @Test
    @DisplayName("읽기만 한다 — 파티 HASH 의 칸 · 수명이 그대로이고 DB 에 파티 줄이 생기지 않는다")
    void readsOnly() throws Exception
    {
        String a = newNickname();
        Cookie aCookie = login(a);
        String partyId = seedHash("CONFIRMED", "LOL", membersOf(userIdOf(a), "TOP"));
        redisTemplate.expire("qm:party:" + partyId, Duration.ofSeconds(300));
        Map<Object, Object> before = redisTemplate.opsForHash().entries("qm:party:" + partyId);

        members(aCookie, partyId, null).andExpect(status().isOk());

        assertThat(redisTemplate.opsForHash().entries("qm:party:" + partyId)).isEqualTo(before);
        assertThat(redisTemplate.getExpire("qm:party:" + partyId)).isBetween(240L, 300L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from parties where match_party_id = ?", Integer.class, partyId)).isZero();
    }

    // ---- Redis 를 못 읽을 때 ----

    @Test
    @DisplayName("Redis 를 못 읽으면 DB 의 기록으로 답하고, DB 에도 없으면 503 ROOM_STATE_UNAVAILABLE 이다 — 못 읽은 것을 '없는 파티'(404)로 읽지 않는다")
    void redisDownFallsBackToTheRecordThenFailsClosed()
    {
        String a = newNickname();
        login(a);
        Long aId = userIdOf(a);
        String recorded = seedRecorded("LOL", aId);
        // 아무도 듣지 않는 포트 — 파티 HASH 를 읽는 쪽만 죽인다(DB 와 프로필은 앱의 진짜 빈이다)
        LettuceConnectionFactory dead = new LettuceConnectionFactory("localhost", 1);
        dead.afterPropertiesSet();
        try
        {
            MatchPartyService service = new MatchPartyService(new MatchPartyReader(new StringRedisTemplate(dead)), matchPartyStore,
                    roomService, gameProfileReader);

            assertThat(service.members(aId, recorded, null).members())
                    .extracting(MatchPartyMembersResponse.Member::userId).containsExactly(aId);
            assertThatThrownBy(() -> service.members(aId, UUID.randomUUID().toString(), "LOL"))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(e.getCode()).isEqualTo("ROOM_STATE_UNAVAILABLE");
                    });
        }
        finally
        {
            dead.destroy();
        }
    }
}
