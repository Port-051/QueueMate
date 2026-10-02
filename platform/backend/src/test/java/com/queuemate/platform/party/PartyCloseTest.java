package com.queuemate.platform.party;

import com.queuemate.platform.party.service.PostLifecycle;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>파티 닫힘</b>(2026-09-26 소유자 결정) — 확정된 방이 없어질 때 파티가 닫히고({@code parties.status = 'CLOSED'} · {@code closed_at}),
 * 그 순간 파티원끼리 서로를 최근 함께한 사람에 적는다. 길이 둘이다 — ① 마지막 사람의 나가기(나가기 스크립트가 방 키를 지운다) · 그 뒤 남은 사람의 접속 확인
 * ② 전원이 말없이 사라져 키가 수명으로 없어졌으면 목록 · 단건이 방 키를 읽다 발견한다. 글은 {@code CONFIRMED} 그대로다 —
 * 그래서 글 응답의 {@code closed} 가 "확정(진행 중)" 과 "끝남" 을 가른다(2026-10-01 소유자 결정).
 */
class PartyCloseTest extends PostTestSupport {

    @Autowired
    private PostLifecycle postLifecycle;

    /**
     * 방장과 멤버들로 방을 채우고 확정한다. 돌려주는 것은 글 번호 = 방 번호. 셋이 들어가는 테스트가 있어 5인 모드의 글이다(정원은 모드의 인원 — P-41).
     * 멤버는 들어온 순서대로 찾는 포지션({@link #FIVE_PERSON_LOL_WANTED})을 하나씩 고른다(2026-09-30 — P-44. 참가할 때 남은 찾는 포지션 가운데 하나를 고른다)
     */
    private Long confirmedRoom(Cookie hostCookie, Cookie... memberCookies) throws Exception
    {
        Long postId = createFivePersonLolPost(hostCookie);
        track(postId);
        for(int i = 0; i < memberCookies.length; i++)
        {
            enterRoom(memberCookies[i], postId, FIVE_PERSON_LOL_WANTED[i]).andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());
        return postId;
    }

    private Map<String, Object> partyOf(Long postId)
    {
        return jdbcTemplate.queryForMap("select id, status, closed_at from parties where post_id = ?", postId);
    }

    /** 이 파티를 마지막으로 적힌 최근 함께한 사람의 줄 — (user_id, other_user_id) */
    private List<List<Long>> recentPairsOf(Long partyId)
    {
        return jdbcTemplate.query("select user_id, other_user_id from recent_players where last_party_id = ?",
                (rs, i) -> List.of(rs.getLong(1), rs.getLong(2)), partyId);
    }

    private int recentRowsOf(Long userId)
    {
        return jdbcTemplate.queryForObject("select count(*) from recent_players where user_id = ? or other_user_id = ?",
                Integer.class, userId, userId);
    }

    /** 단건 조회의 응답 본문 */
    private JsonNode single(Cookie cookie, Long postId) throws Exception
    {
        return body(mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isOk()));
    }

    // ---- 응답의 closed (2026-10-01 소유자 결정) ----

    @Test
    @DisplayName("글 응답의 closed 는 확정된 글의 파티가 닫혔을 때만 참이다 — 모집 중(글 쓰기의 응답부터) · 만료 · 확정 직후(파티가 열려 있다)는 false, 전원이 나가 파티가 닫히면 목록 · 단건이 true")
    void closedFlagFollowsTheParty() throws Exception
    {
        String host = newNickname();
        String member = newNickname();
        Cookie hostCookie = login(host);
        Cookie memberCookie = login(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Cookie viewer = login(newNickname());

        JsonNode created = body(createPost(hostCookie, lolPostBody("모집 중")).andExpect(status().isCreated()));
        Long recruiting = created.get("postId").asLong();
        assertThat(created.get("closed").asBoolean()).isFalse();
        assertThat(single(viewer, recruiting).get("closed").asBoolean()).isFalse();
        // 지운 글(만료)도 false 다 — 파티가 없다
        mockMvc.perform(delete("/api/v1/posts/" + recruiting).cookie(hostCookie)).andExpect(status().isNoContent());
        JsonNode expired = single(viewer, recruiting);
        assertThat(expired.get("status").asString()).isEqualTo("EXPIRED");
        assertThat(expired.get("closed").asBoolean()).isFalse();

        Long postId = confirmedRoom(hostCookie, memberCookie);
        track(postId, hostId, memberId);
        JsonNode open = find(list(viewer, "LOL"), postId);
        assertThat(open.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(open.get("closed").asBoolean()).isFalse();

        // 전원이 나가 파티가 닫힌다(길 ①) — 글은 CONFIRMED 그대로이고 closed 가 그것을 가른다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(memberCookie)).andExpect(status().isNoContent());
        assertThat(single(viewer, postId).get("closed").asBoolean()).isFalse();
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(hostCookie)).andExpect(status().isNoContent());
        assertThat(partyOf(postId)).containsEntry("status", "CLOSED");

        JsonNode closed = find(list(viewer, "LOL"), postId);
        assertThat(closed.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(closed.get("closed").asBoolean()).isTrue();
        assertThat(single(viewer, postId).get("closed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("단건 · 목록이 사라진 방을 보고 그 자리에서 파티를 닫으면(길 ②) 그 응답부터 closed 가 참이다 — 닫기 전에 읽은 ACTIVE 를 내보내지 않는다")
    void closedOnTheResponseThatClosesIt() throws Exception
    {
        Cookie viewer = login(newNickname());
        String host1 = newNickname();
        String member1 = newNickname();
        String host2 = newNickname();
        String member2 = newNickname();
        Cookie host1Cookie = login(host1);
        Cookie member1Cookie = login(member1);
        Cookie host2Cookie = login(host2);
        Cookie member2Cookie = login(member2);

        // 단건이 처음 본다 — 전원이 말없이 사라져 방 키가 수명으로 없어졌다(손으로 지운다)
        Long byGet = confirmedRoom(host1Cookie, member1Cookie);
        track(byGet, userIdOf(host1), userIdOf(member1));
        closeRoom(byGet);
        assertThat(partyOf(byGet)).containsEntry("status", "ACTIVE");
        assertThat(single(viewer, byGet).get("closed").asBoolean()).isTrue();
        assertThat(partyOf(byGet)).containsEntry("status", "CLOSED");

        // 목록이 처음 본다
        Long byList = confirmedRoom(host2Cookie, member2Cookie);
        track(byList, userIdOf(host2), userIdOf(member2));
        closeRoom(byList);
        assertThat(partyOf(byList)).containsEntry("status", "ACTIVE");
        assertThat(find(list(viewer, "LOL"), byList).get("closed").asBoolean()).isTrue();
        assertThat(partyOf(byList)).containsEntry("status", "CLOSED");
    }

    // ---- 길 ①: 마지막 사람의 나가기 ----

    @Test
    @DisplayName("확정한 방에서 전원이 나가면 파티가 닫힌다 — CLOSED · closed_at, 최근 함께한 사람에 양방향으로 적히고 GET /recent-players 에 상대가 나온다. 글은 CONFIRMED 그대로")
    void lastLeaveClosesTheParty() throws Exception
    {
        String host = newNickname();
        String member = newNickname();
        Cookie hostCookie = login(host);
        Cookie memberCookie = login(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = confirmedRoom(hostCookie, memberCookie);
        track(postId, hostId, memberId);

        // 한 사람이 나가도 방은 남는다 — 파티는 열려 있다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(memberCookie)).andExpect(status().isNoContent());
        assertThat(partyOf(postId)).containsEntry("status", "ACTIVE");
        assertThat(recentRowsOf(hostId)).isZero();

        // 마지막 사람(방장)이 나가면 넘길 사람이 없어 방이 없어진다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(hostCookie)).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(hostKey(postId))).isFalse();
        assertThat(redisTemplate.hasKey(confirmedKey(postId))).isFalse();
        Map<String, Object> party = partyOf(postId);
        assertThat(party).containsEntry("status", "CLOSED");
        assertThat(party.get("closed_at")).isNotNull();
        Long partyId = (Long) party.get("id");
        assertThat(recentPairsOf(partyId)).containsExactlyInAnyOrder(List.of(hostId, memberId), List.of(memberId, hostId));
        Instant closedAt = ((Timestamp) party.get("closed_at")).toInstant();
        assertThat(jdbcTemplate.queryForObject("select last_played_at from recent_players where user_id = ? and other_user_id = ?",
                Timestamp.class, hostId, memberId).toInstant()).isEqualTo(closedAt);
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");

        mockMvc.perform(get("/api/v1/recent-players").cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].userId", equalTo(memberId), Long.class))
                .andExpect(jsonPath("$.players[0].nickname").value(member))
                .andExpect(jsonPath("$.players[0].lastPartyId", equalTo(partyId), Long.class));
        mockMvc.perform(get("/api/v1/recent-players").cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].userId", equalTo(hostId), Long.class));

        // 길 ② 가 뒤따라 와도(목록 · 단건) 다시 닫지 않는다 — 닫힌 파티의 글은 방 키를 읽지도 않는다
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(hostCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        list(hostCookie, "LOL");
        assertThat(((Timestamp) partyOf(postId).get("closed_at")).toInstant()).isEqualTo(closedAt);
        assertThat(recentPairsOf(partyId)).hasSize(2);
        // 직접 불러도 한 번뿐이다
        assertThat(postLifecycle.closeParty(postId, Instant.now().truncatedTo(ChronoUnit.MILLIS))).isFalse();
        assertThat(((Timestamp) partyOf(postId).get("closed_at")).toInstant()).isEqualTo(closedAt);
    }

    @Test
    @DisplayName("방장이 먼저 나가면 승계이고 파티는 열려 있다 — 넘겨받은 사람이 마지막으로 나가면 닫힌다. 셋이면 여섯 줄이다")
    void successorLeavingLastClosesTheParty() throws Exception
    {
        String host = newNickname();
        String a = newNickname();
        String b = newNickname();
        Cookie hostCookie = login(host);
        Cookie aCookie = login(a);
        Cookie bCookie = login(b);
        Long hostId = userIdOf(host);
        Long aId = userIdOf(a);
        Long bId = userIdOf(b);
        Long postId = confirmedRoom(hostCookie, aCookie, bCookie);
        track(postId, hostId, aId, bId);

        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(hostCookie)).andExpect(status().isNoContent());
        assertThat(partyOf(postId)).containsEntry("status", "ACTIVE");
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(aCookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(bCookie)).andExpect(status().isNoContent());
        // 나가기 스크립트는 방장이 나갈 때마다 남은 사람에게 넘기므로 마지막 한 사람은 늘 방장이다 — 그 나가기가 방을 없앤다
        assertThat(redisTemplate.hasKey(confirmedKey(postId))).isFalse();

        Map<String, Object> party = partyOf(postId);
        assertThat(party).containsEntry("status", "CLOSED");
        assertThat(recentPairsOf((Long) party.get("id"))).containsExactlyInAnyOrder(
                List.of(hostId, aId), List.of(hostId, bId), List.of(aId, hostId),
                List.of(aId, bId), List.of(bId, hostId), List.of(bId, aId));
    }

    @Test
    @DisplayName("파티원이 한 명이면(나머지는 가입하지 않은 번호) 닫혀도 최근 함께한 사람은 비어 있다")
    void singleMemberPartyRecordsNothing() throws Exception
    {
        String host = newNickname();
        Cookie hostCookie = login(host);
        Long hostId = userIdOf(host);
        Long postId = createLolPost(hostCookie);
        track(postId, hostId);
        // 가입하지 않은 번호 — 스크립트는 2명으로 세지만 파티원으로 적히지 않는다
        addMember(postId, unknownUserId());
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());

        // 남은 번호는 입장 표시 키가 없어 넘겨받지 못한다 — 방이 없어진다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(hostCookie)).andExpect(status().isNoContent());

        assertThat(partyOf(postId)).containsEntry("status", "CLOSED");
        assertThat(recentRowsOf(hostId)).isZero();
    }

    @Test
    @DisplayName("길 ① 과 ② 가 동시에 와도 파티는 한 번만 닫힌다 — 조건부 UPDATE 가 한 호출만 통과시킨다")
    void concurrentClosesCloseOnce() throws Exception
    {
        Cookie hostCookie = login(newNickname());
        String member = newNickname();
        Cookie memberCookie = login(member);
        Long postId = confirmedRoom(hostCookie, memberCookie);
        track(postId, userIdOf(member));

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try
        {
            Callable<Boolean> close = () -> postLifecycle.closeParty(postId, now);
            List<Future<Boolean>> results = pool.invokeAll(List.of(close, close, close, close));
            long closed = 0;
            for(Future<Boolean> result : results)
            {
                closed += result.get() ? 1 : 0;
            }
            assertThat(closed).isEqualTo(1);
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(recentPairsOf((Long) partyOf(postId).get("id"))).hasSize(2);
    }

    // ---- 길 ①': 방이 없어진 뒤 남은 사람의 접속 확인 ----

    @Test
    @DisplayName("확정한 방의 키 셋이 수명으로 없어진 뒤 남은 사람이 접속 확인을 보내면 404 ROOM_NOT_FOUND 이고 그 자리에서 파티가 닫힌다")
    void heartbeatOnAGoneRoomClosesTheParty() throws Exception
    {
        Cookie hostCookie = login(newNickname());
        String member = newNickname();
        Cookie memberCookie = login(member);
        Long memberId = userIdOf(member);
        Long postId = confirmedRoom(hostCookie, memberCookie);
        track(postId, memberId);
        // 수명이 다한 척 — 방 키 셋만 지운다. 멤버의 입장 표시 키는 남아 있다
        redisTemplate.delete(List.of(hostKey(postId), membersKey(postId), confirmedKey(postId)));

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/heartbeat").cookie(memberCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));

        assertThat(partyOf(postId)).containsEntry("status", "CLOSED");
        assertThat(recentRowsOf(memberId)).isEqualTo(2);
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
    }

    // ---- 길 ②: 목록 · 단건이 발견한다 ----

    @Test
    @DisplayName("전원이 말없이 사라져 키 셋이 다 없으면 단건 조회가 파티를 닫는다 — 글은 CONFIRMED 그대로이고 응답도 그대로다")
    void readerClosesAVanishedParty() throws Exception
    {
        String host = newNickname();
        String member = newNickname();
        Cookie hostCookie = login(host);
        Cookie memberCookie = login(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = confirmedRoom(hostCookie, memberCookie);
        track(postId, hostId, memberId);
        closeRoom(postId);
        assertThat(partyOf(postId)).containsEntry("status", "ACTIVE");

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                // 카드는 방이 아니라 확정 순간의 파티원이다(2026-09-30 — P-40) — 방이 없어지고 파티가 닫혀도 그대로다
                .andExpect(jsonPath("$.memberCount").value(2));

        Map<String, Object> party = partyOf(postId);
        assertThat(party).containsEntry("status", "CLOSED");
        assertThat(recentPairsOf((Long) party.get("id")))
                .containsExactlyInAnyOrder(List.of(hostId, memberId), List.of(memberId, hostId));
        Object closedAt = party.get("closed_at");
        // 목록이 다시 봐도 그대로다
        list(hostCookie, "LOL");
        assertThat(partyOf(postId).get("closed_at")).isEqualTo(closedAt);
    }

    @Test
    @DisplayName("확정한 방에서 방장 키만 없고 멤버 HASH · 확정 표시 키가 남아 있으면(승계를 기다리는 중 — D-23) 닫지 않는다. 확정 키만 남아도 닫지 않는다")
    void hostKeyAloneMissingDoesNotClose() throws Exception
    {
        Cookie hostCookie = login(newNickname());
        String member = newNickname();
        Cookie memberCookie = login(member);
        Long postId = confirmedRoom(hostCookie, memberCookie);
        track(postId, userIdOf(member));
        redisTemplate.delete(hostKey(postId));

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(hostCookie)).andExpect(status().isOk());
        list(hostCookie, "LOL");
        assertThat(partyOf(postId)).containsEntry("status", "ACTIVE");

        redisTemplate.delete(membersKey(postId));
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(hostCookie)).andExpect(status().isOk());
        assertThat(partyOf(postId)).containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("확정 전에 방이 없어지면 파티가 없으니 파티 쪽은 아무 일도 없다 — 글이 만료될 뿐이다(나가기 · 목록 둘 다)")
    void unconfirmedRoomHasNoParty() throws Exception
    {
        String host = newNickname();
        Cookie hostCookie = login(host);
        Long postId = createLolPost(hostCookie);
        track(postId);
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(hostCookie)).andExpect(status().isNoContent());
        assertThat(statusOf(postId)).isEqualTo("EXPIRED");

        Long other = createLolPost(hostCookie);
        track(other);
        closeRoom(other);
        list(hostCookie, "LOL");
        assertThat(statusOf(other)).isEqualTo("EXPIRED");

        assertThat(jdbcTemplate.queryForObject("select count(*) from parties where post_id in (?, ?)", Integer.class, postId, other))
                .isZero();
        assertThat(recentRowsOf(userIdOf(host))).isZero();
    }
}
