package com.queuemate.platform.party;

import com.queuemate.platform.room.RoomTestSupport;
import com.queuemate.platform.room.notification.PushSubscriber;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>자동 매칭 파티의 방</b>(2026-09-27 소유자 결정 — docs/11 D-42) — {@code POST /api/v1/match-parties/{partyId}/room}. {@code matching} 이 남긴 파티 HASH
 * ({@code qm:party:{partyId}})를 읽어 첫 사람은 방을 만들고(201) 나머지는 들어가며(200), 파티는 {@code parties}({@code source = MATCH} · {@code match_party_id})
 * 에 한 번만 적히고 {@code party_members} 에는 <b>들어온 사람만</b> 한 줄씩 적힌다(2026-09-28 소유자 결정 — HASH 의 파티원 전부가 아니다).
 * 방이 없어지면 파티가 닫히고 최근 함께한 사람이 적힌다({@code PartyCloseTest} 와 같은 단언).
 *
 * <p><b>{@code matching} 인 척 파티 HASH 를 이 테스트가 직접 심는다</b>({@link #seedConfirmedParty}) — 필드 이름은 {@code matching} 의
 * {@code proposal/cleanup-confirmed.lua} 머리의 것이다. 이 키는 이 테스트의 것이라 끝나면 지운다(방 키 셋도). 사용자의 입장 표시 키 · 활성 요청 키는
 * {@link RoomTestSupport#adopt} 로 이름표에 붙여 그쪽 뒷정리에 맡긴다. <b>{@code FLUSHDB} 금지</b>는 그대로다.
 */
class MatchPartyRoomTest extends RoomTestSupport {

    /** 이 테스트가 심은 파티 HASH 의 partyId — 끝나면 HASH 와 그 방의 키 셋을 지운다 */
    private final List<String> seededParties = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteSeededParties()
    {
        for(String partyId : seededParties)
        {
            redisTemplate.delete(List.of("qm:party:" + partyId, "qm:room:" + partyId + ":host",
                    "qm:room:" + partyId + ":members", "qm:room:" + partyId + ":confirmed"));
        }
        seededParties.clear();
    }

    /** 로그인한 사용자를 이름표에 붙인다 — 쿠키는 돌려주고 번호는 {@code u(label)} 로 꺼낸다 */
    private Cookie member(String label)
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        adopt(label, userIdOf(nickname));
        return cookie;
    }

    /** {@code matching} 이 확정 직후 남기는 모양 그대로 심는다. {@code target} 은 파티원 수와 같다 — 자동 매칭은 정원이 차야 확정된다 */
    private String seedConfirmedParty(String status, String... memberLabels)
    {
        String partyId = UUID.randomUUID().toString();
        seededParties.add(partyId);
        String key = "qm:party:" + partyId;
        redisTemplate.opsForHash().put(key, "status", status);
        redisTemplate.opsForHash().put(key, "confirmedAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().put(key, "game", "LOL");
        redisTemplate.opsForHash().put(key, "modeKey", LOL_MODE_2);
        redisTemplate.opsForHash().put(key, "voicePreference", "REQUIRED");
        redisTemplate.opsForHash().put(key, "playPurpose", "RANK_UP");
        redisTemplate.opsForHash().put(key, "target", String.valueOf(memberLabels.length));
        String[] positions = { "TOP", "JUNGLE", "MID", "ADC", "SUPPORT" };
        for(int i = 0; i < memberLabels.length; i++)
        {
            redisTemplate.opsForHash().put(key, "member:" + u(memberLabels[i]), positions[i % positions.length]);
        }
        redisTemplate.expire(key, Duration.ofSeconds(600));
        return partyId;
    }

    private Map<String, Object> partyOf(String matchPartyId)
    {
        return jdbcTemplate.queryForMap("select id, source, post_id, game, status, closed_at from parties where match_party_id = ?", matchPartyId);
    }

    private int partyCountOf(String matchPartyId)
    {
        return jdbcTemplate.queryForObject("select count(*) from parties where match_party_id = ?", Integer.class, matchPartyId);
    }

    private List<Map<String, Object>> partyMembersOf(String matchPartyId)
    {
        return jdbcTemplate.queryForList("select user_id, is_host from party_members "
                + "where party_id = (select id from parties where match_party_id = ?) order by user_id", matchPartyId);
    }

    private List<List<Long>> recentPairsOf(Long partyId)
    {
        return jdbcTemplate.query("select user_id, other_user_id from recent_players where last_party_id = ?",
                (rs, i) -> List.of(rs.getLong(1), rs.getLong(2)), partyId);
    }

    private Set<String> roomMembers(String partyId)
    {
        return redisTemplate.opsForSet().members("qm:room:" + partyId + ":members");
    }

    /** UUID 방의 방장 키 값(사용자 번호). {@link RoomTestSupport#host} 는 이름표의 방(숫자)을 위한 것이라 여기서는 쓰지 않는다 */
    private String roomHost(String partyId)
    {
        return redisTemplate.opsForValue().get("qm:room:" + partyId + ":host");
    }

    // ---- 만들기 · 들어가기 ----

    @Test
    @DisplayName("첫 파티원이 부르면 201 이고 roomId 는 partyId 다 — 방장 키 · 확정 표시 키 · 멤버 SET · 입장 표시 키가 서고, parties 에 MATCH 한 줄 · party_members 에는 들어온 그 사람 한 줄(is_host)")
    void firstCallerCreatesTheRoom() throws Exception
    {
        Cookie u1 = member("u1");
        member("u2");
        member("u3");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2", "u3");

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(partyId));

        assertThat(redisTemplate.opsForValue().get("qm:room:" + partyId + ":host")).isEqualTo(u("u1"));
        assertThat(redisTemplate.opsForValue().get("qm:room:" + partyId + ":confirmed")).isEqualTo(partyId);
        assertThat(roomMembers(partyId)).containsExactly(u("u1"));
        assertThat(marker("u1")).isEqualTo(partyId);
        assertThat(redisTemplate.getExpire("qm:room:" + partyId + ":host")).isBetween(1L, 600L);
        assertThat(redisTemplate.getExpire("qm:room:" + partyId + ":members")).isBetween(1L, 600L);
        // matching 의 HASH 는 건드리지 않는다 — 읽기만 한다
        assertThat(redisTemplate.opsForHash().get("qm:party:" + partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(redisTemplate.getExpire("qm:party:" + partyId)).isBetween(1L, 600L);

        Map<String, Object> party = partyOf(partyId);
        assertThat(party).containsEntry("source", "MATCH").containsEntry("game", "LOL").containsEntry("status", "ACTIVE");
        assertThat(party.get("post_id")).isNull();
        // party_members 는 들어온 사람만 — 아직 안 들어온 u2 · u3 은 HASH 에 있어도 적히지 않는다 (2026-09-28)
        List<Map<String, Object>> members = partyMembersOf(partyId);
        assertThat(members).extracting(m -> m.get("user_id")).containsExactly(Long.parseLong(u("u1")));
        assertThat(members).extracting(m -> m.get("is_host")).containsExactly(true);

        // 내 방 찾기도 UUID 를 그대로 준다
        mockMvc.perform(get("/api/v1/rooms/me").cookie(u1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomId").value(partyId));
    }

    @Test
    @DisplayName("두 번째 파티원은 200 으로 들어가고 먼저 있던 사람이 ROOM_MEMBER_ENTERED 를 받는다. 다시 불러도 200 이고 바뀌는 것이 없다 — 파티는 여전히 한 줄 · party_members 는 들어온 둘(둘째는 is_host 아님)")
    void othersEnterAndRetryIsIdempotent() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        member("u3");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2", "u3");
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());

        try(PushSubscriber pushes = new PushSubscriber(connectionFactory, objectMapper, this::labelOf))
        {
            mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roomId").value(partyId));

            PushSubscriber.Received received = pushes.next();
            assertThat(received).isNotNull();
            assertThat(received.userId()).isEqualTo("u1");
            assertThat(received.envelope().get("type").asString()).isEqualTo("ROOM_MEMBER_ENTERED");
            assertThat(received.envelope().get("payload").get("roomId").asString()).isEqualTo(partyId);
            assertThat(received.envelope().get("payload").get("userId").asString()).isEqualTo(u("u2"));
            assertThat(pushes.nothingMore()).isTrue();

            // 재시도 — 아무것도 바뀌지 않고 아무에게도 알리지 않는다
            mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2)).andExpect(status().isOk());
            assertThat(pushes.nothingMore()).isTrue();
        }

        assertThat(roomMembers(partyId)).containsExactlyInAnyOrder(u("u1"), u("u2"));
        assertThat(marker("u2")).isEqualTo(partyId);
        assertThat(roomHost(partyId)).isEqualTo(u("u1"));
        assertThat(partyCountOf(partyId)).isEqualTo(1);
        // 들어온 둘만 — 재시도가 줄을 늘리지 않고, 아직 안 들어온 u3 은 없다
        List<Map<String, Object>> members = partyMembersOf(partyId);
        assertThat(members).extracting(m -> m.get("user_id")).containsExactlyInAnyOrder(Long.parseLong(u("u1")), Long.parseLong(u("u2")));
        assertThat(members.stream().filter(m -> Boolean.TRUE.equals(m.get("is_host"))).map(m -> m.get("user_id")))
                .containsExactly(Long.parseLong(u("u1")));
    }

    // ---- 파티 HASH 가 수명(600초)으로 사라진 뒤 — 방에 있는 사람은 방 키로, 방에 없는 사람만 HASH 로 판정한다 (2026-09-28) ----

    @Test
    @DisplayName("방에 들어와 있는 사람은 파티 HASH 가 사라진 뒤에 다시 불러도(새로고침) 200 이고 방 키 · 멤버 SET 이 그대로다")
    void refreshAfterPartyHashExpiredIsStillOk() throws Exception
    {
        Cookie u1 = member("u1");
        member("u2");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());

        // 수명 만료 흉내 — matching 의 HASH 가 600초 뒤 사라진 상태
        redisTemplate.delete("qm:party:" + partyId);

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(partyId));

        assertThat(roomHost(partyId)).isEqualTo(u("u1"));
        assertThat(redisTemplate.opsForValue().get("qm:room:" + partyId + ":confirmed")).isEqualTo(partyId);
        assertThat(roomMembers(partyId)).containsExactly(u("u1"));
        assertThat(marker("u1")).isEqualTo(partyId);
        assertThat(partyCountOf(partyId)).isEqualTo(1);
        // HASH 를 되살리지 않는다 — 읽기만 한다
        assertThat(redisTemplate.hasKey("qm:party:" + partyId)).isFalse();
    }

    @Test
    @DisplayName("파티 HASH 가 사라진 뒤 아직 방에 안 들어온 파티원은 404 MATCH_PARTY_NOT_FOUND 이고 방에 들어가지 않는다")
    void newcomerAfterPartyHashExpiredIsNotFound() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());

        redisTemplate.delete("qm:party:" + partyId);

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));

        assertThat(roomMembers(partyId)).containsExactly(u("u1"));
        assertThat(marker("u2")).isNull();
        assertThat(partyCountOf(partyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("파티원이 아니면 403 NOT_PARTY_MEMBER 이고 아무 키도 · 아무 줄도 생기지 않는다")
    void outsiderIsRejected() throws Exception
    {
        member("u1");
        member("u2");
        Cookie stranger = member("stranger");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PARTY_MEMBER"));

        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":host")).isFalse();
        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":members")).isFalse();
        assertThat(marker("stranger")).isNull();
        assertThat(partyCountOf(partyId)).isZero();
    }

    @Test
    @DisplayName("확정된 파티가 없으면 404 MATCH_PARTY_NOT_FOUND — 없는 id · UUID 가 아닌 경로 · 아직 PENDING 인 파티. 어느 것도 방을 만들지 않는다")
    void missingOrPendingPartyIsNotFound() throws Exception
    {
        Cookie u1 = member("u1");
        member("u2");
        String pending = seedConfirmedParty("PENDING", "u1", "u2");

        mockMvc.perform(post("/api/v1/match-parties/" + UUID.randomUUID() + "/room").cookie(u1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/match-parties/not-a-uuid/room").cookie(u1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/match-parties/" + pending + "/room").cookie(u1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));

        assertThat(redisTemplate.hasKey("qm:room:" + pending + ":host")).isFalse();
        assertThat(marker("u1")).isNull();
        assertThat(partyCountOf(pending)).isZero();
        // 쿠키가 없으면 401 — 인증이 필요한 경로다
        mockMvc.perform(post("/api/v1/match-parties/" + pending + "/room")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("HASH 에 target 이 없으면(혼자 읽을 수 없는 HASH) 없는 파티로 다룬다 — 404 이고 파티를 적지 않는다")
    void partyWithoutTargetIsNotFound() throws Exception
    {
        Cookie u1 = member("u1");
        member("u2");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");
        redisTemplate.opsForHash().delete("qm:party:" + partyId, "target");

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_PARTY_NOT_FOUND"));

        assertThat(partyCountOf(partyId)).isZero();
        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":host")).isFalse();
    }

    @Test
    @DisplayName("이미 다른 방(게시판 방)에 들어가 있으면 409 IN_OTHER_ROOM 이다 — 파티는 적혔지만 방에는 들어가지 않는다")
    void inAnotherRoomIsRejected() throws Exception
    {
        Cookie u1 = member("u1");
        member("u2");
        roomService.create(r("board"), u("u1"));
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_OTHER_ROOM"));

        assertThat(marker("u1")).isEqualTo("board");
        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":host")).isFalse();
        // 파티는 이미 확정된 사실이라 DB 의 줄은 남는다(트랜잭션 밖의 스크립트 — MatchPartyService)
        assertThat(partyCountOf(partyId)).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 요청 키(status=PARTY — 확정 뒤 60초 남아 있다)가 있어도 들어갈 수 있다 — 그 활성 요청이 곧 이 파티다. 키는 건드리지 않는다")
    void activeRequestDoesNotBlock() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");
        String activeRequest = "qm:user:active-request:" + u("u1");
        redisTemplate.opsForHash().put(activeRequest, "status", "PARTY");
        redisTemplate.opsForHash().put(activeRequest, "partyId", partyId);
        redisTemplate.expire(activeRequest, Duration.ofSeconds(60));

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2)).andExpect(status().isOk());

        assertThat(roomMembers(partyId)).containsExactlyInAnyOrder(u("u1"), u("u2"));
        assertThat(redisTemplate.opsForHash().get(activeRequest, "status")).isEqualTo("PARTY");
        assertThat(redisTemplate.getExpire(activeRequest)).isBetween(1L, 60L);
        // 게시판 입장으로는 UUID 방에 못 들어온다 — 글이 없다
        mockMvc.perform(post("/api/v1/rooms/" + partyId + "/members").cookie(u2))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("파티원 셋이 동시에 불러도 파티는 한 줄 · 멤버 SET 은 셋 · party_members 는 들어온 셋 · 방장(is_host)은 한 명 · 201 은 한 번이다")
    void concurrentCallsMakeOneRoom() throws Exception
    {
        Cookie[] cookies = { member("u0"), member("u1"), member("u2") };
        String partyId = seedConfirmedParty("CONFIRMED", "u0", "u1", "u2");
        AtomicInteger created = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();

        runConcurrently(3, i -> {
            try
            {
                int status = mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(cookies[i]))
                        .andReturn().getResponse().getStatus();
                if(status == 201)
                {
                    created.incrementAndGet();
                }
                else if(status == 200)
                {
                    ok.incrementAndGet();
                }
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }
        });

        assertThat(created.get()).isEqualTo(1);
        assertThat(ok.get()).isEqualTo(2);
        assertThat(roomMembers(partyId)).containsExactlyInAnyOrder(u("u0"), u("u1"), u("u2"));
        assertThat(roomHost(partyId)).isIn(u("u0"), u("u1"), u("u2"));
        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":confirmed")).isTrue();
        assertThat(partyCountOf(partyId)).isEqualTo(1);
        List<Map<String, Object>> members = partyMembersOf(partyId);
        assertThat(members).hasSize(3);
        assertThat(members.stream().filter(m -> Boolean.TRUE.equals(m.get("is_host"))).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("정원(target)이 차면 파티원이라도 409 ROOM_FULL 이다 — 누가 손으로 멤버 SET 을 채운 경우. 확정된 방이라 enter-room 으로도 못 들어온다")
    void fullRoomIsRejected() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());
        redisTemplate.opsForSet().add("qm:room:" + partyId + ":members", Long.toString(unknownUserId()));

        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_FULL"));

        assertThat(marker("u2")).isNull();
    }

    // ---- 나가기 → 파티 닫힘 ----

    @Test
    @DisplayName("셋이 들어온 뒤 전부 나가면(방장이 나가면 승계 — D-23) 마지막 나가기에 파티가 CLOSED · closed_at 이다. 최근 함께한 사람 여섯 줄은 들어올 때 이미 적혀 있다")
    void lastLeaveClosesTheMatchParty() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        Cookie u3 = member("u3");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2", "u3");
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u3)).andExpect(status().isOk());
        Long partyRow = (Long) partyOf(partyId).get("id");

        // 방장이 먼저 나간다 — 확정된 방이라 승계이고 파티는 열려 있다
        mockMvc.perform(delete("/api/v1/rooms/" + partyId + "/members/me").cookie(u1)).andExpect(status().isNoContent());
        assertThat(partyOf(partyId)).containsEntry("status", "ACTIVE");
        assertThat(roomHost(partyId)).isIn(u("u2"), u("u3"));
        // 접속 확인 · 목록도 UUID 방에 그대로 된다
        mockMvc.perform(post("/api/v1/rooms/" + partyId + "/heartbeat").cookie(u2)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/rooms/" + partyId + "/members").cookie(u2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomId").value(partyId));

        mockMvc.perform(delete("/api/v1/rooms/" + partyId + "/members/me").cookie(u2)).andExpect(status().isNoContent());
        assertThat(partyOf(partyId)).containsEntry("status", "ACTIVE");
        mockMvc.perform(delete("/api/v1/rooms/" + partyId + "/members/me").cookie(u3)).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":host")).isFalse();
        assertThat(redisTemplate.hasKey("qm:room:" + partyId + ":confirmed")).isFalse();
        assertThat(marker("u1")).isNull();
        assertThat(marker("u2")).isNull();
        assertThat(marker("u3")).isNull();
        Map<String, Object> party = partyOf(partyId);
        assertThat(party).containsEntry("status", "CLOSED");
        assertThat(party.get("closed_at")).isNotNull();
        Long a = Long.parseLong(u("u1"));
        Long b = Long.parseLong(u("u2"));
        Long c = Long.parseLong(u("u3"));
        assertThat(recentPairsOf(partyRow)).containsExactlyInAnyOrder(
                List.of(a, b), List.of(a, c), List.of(b, a), List.of(b, c), List.of(c, a), List.of(c, b));
    }

    @Test
    @DisplayName("최근 함께한 사람은 들어올 때 적힌다 — 들어온 순간 방에 있던 사람과만 짝이 되고, 먼저 나간 사람과 뒤에 들어온 사람은 짝이 되지 않는다")
    void recentPlayersAreRecordedOnEntryWithWhoeverIsInTheRoom() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        Cookie u3 = member("u3");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2", "u3");
        Long a = Long.parseLong(u("u1"));
        Long b = Long.parseLong(u("u2"));
        Long c = Long.parseLong(u("u3"));

        // 첫 사람 — 방에 아무도 없어 적을 짝이 없다
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());
        Long partyRow = (Long) partyOf(partyId).get("id");
        assertThat(recentPairsOf(partyRow)).isEmpty();

        // 둘째 — 그 순간 방에 있던 첫 사람과 양방향으로. 파티는 아직 ACTIVE 다(닫힌 적 없다)
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2)).andExpect(status().isOk());
        assertThat(recentPairsOf(partyRow)).containsExactlyInAnyOrder(List.of(a, b), List.of(b, a));
        assertThat(partyOf(partyId)).containsEntry("status", "ACTIVE");

        // 다시 불러도(새로고침) 이미 파티원이라 짝을 또 적지 않는다
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2)).andExpect(status().isOk());
        assertThat(recentPairsOf(partyRow)).hasSize(2);

        // 첫 사람이 나간 뒤 셋째가 들어온다 — 셋째는 그 순간 방에 있던 둘째하고만 짝이 된다. party_members 에는 첫 사람이 남아 있지만
        // 짝은 스크립트가 돌려준 "먼저 있던 사람"(방 멤버 SET)으로 적으므로 서로 마주친 적 없는 첫째–셋째는 적히지 않는다
        mockMvc.perform(delete("/api/v1/rooms/" + partyId + "/members/me").cookie(u1)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u3)).andExpect(status().isOk());
        assertThat(recentPairsOf(partyRow)).containsExactlyInAnyOrder(
                List.of(a, b), List.of(b, a), List.of(c, b), List.of(b, c));
    }

    @Test
    @DisplayName("방이 사라진 뒤 남은 사람의 접속 확인(404 ROOM_NOT_FOUND)도 그 자리에서 자동 매칭 파티를 닫는다 — 두 번 닫지 않는다")
    void heartbeatOnAGoneRoomClosesTheMatchParty() throws Exception
    {
        Cookie u1 = member("u1");
        Cookie u2 = member("u2");
        String partyId = seedConfirmedParty("CONFIRMED", "u1", "u2");
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u1)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/match-parties/" + partyId + "/room").cookie(u2)).andExpect(status().isOk());
        // 수명이 다한 척 — 방 키 셋만 지운다. 입장 표시 키는 남아 있다
        redisTemplate.delete(List.of("qm:room:" + partyId + ":host", "qm:room:" + partyId + ":members", "qm:room:" + partyId + ":confirmed"));

        mockMvc.perform(post("/api/v1/rooms/" + partyId + "/heartbeat").cookie(u2))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));

        Map<String, Object> party = partyOf(partyId);
        assertThat(party).containsEntry("status", "CLOSED");
        Object closedAt = party.get("closed_at");
        assertThat(recentPairsOf((Long) party.get("id"))).hasSize(2);

        mockMvc.perform(post("/api/v1/rooms/" + partyId + "/heartbeat").cookie(u1)).andExpect(status().isNotFound());
        assertThat(partyOf(partyId).get("closed_at")).isEqualTo(closedAt);
    }
}
