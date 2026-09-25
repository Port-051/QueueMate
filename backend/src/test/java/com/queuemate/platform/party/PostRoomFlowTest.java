package com.queuemate.platform.party;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>글과 방이 맞물리는 곳</b> — 2026-09-25 {@code room} 합치기 2단계({@code contracts/platform-api.md} P-22). 두 앱을 잇던 입장권 · 방 만들기 요청 ·
 * 확정 기록 요청이 없어지고 한 요청 안에서 끝나게 된 셋을 HTTP 로 본다.
 * <ul>
 *   <li><b>글 쓰기 = 방 만들기</b>(소유자 결정 C) — 방을 못 만들면 글도 안 써진다</li>
 *   <li><b>입장의 검사</b>(소유자 결정 ① — 경로는 그대로 {@code POST /api/v1/rooms/{roomId}/members}) — 글(404 · 409)을 먼저, 그 다음 방의 스크립트</li>
 *   <li><b>방장 확정 한 길</b> — {@code POST /api/v1/rooms/{roomId}/confirm} 한 요청이 방을 확정하고 파티를 적는다. 커밋이 실패했던 글은 자가 치유가 고친다</li>
 * </ul>
 */
class PostRoomFlowTest extends PostTestSupport {

    // ---- 글 쓰기 = 방 만들기 ----

    @Test
    @DisplayName("글을 쓰면 방이 같이 생긴다 — 방장 키 · 멤버 SET · 입장 표시 키에 쓴 사람이 들어가고, 내 방 찾기가 그 글의 번호를 준다")
    void createMakesTheRoom() throws Exception
    {
        String host = newLoginId();
        Cookie cookie = signupAndLogin(host);
        Long hostId = userIdOf(host);

        Long postId = createLolPost(cookie);

        assertThat(redisTemplate.opsForValue().get(hostKey(postId))).isEqualTo(Long.toString(hostId));
        assertThat(redisTemplate.opsForSet().members(membersKey(postId))).containsExactly(Long.toString(hostId));
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + hostId)).isEqualTo(Long.toString(postId));
        assertThat(redisTemplate.getExpire(hostKey(postId))).isBetween(1L, 600L);
        mockMvc.perform(get("/api/v1/rooms/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(Long.toString(postId)));
    }

    @Test
    @DisplayName("이미 다른 방에 들어가 있으면 글을 쓸 수 없다 — 409 IN_OTHER_ROOM 이고 글도 방도 남지 않는다(방을 못 만들면 글이 되돌려진다)")
    void createRolledBackWhenInAnotherRoom() throws Exception
    {
        String host = newLoginId();
        String guest = newLoginId();
        Long postId = createLolPost(signupAndLogin(host));
        Cookie guestCookie = signupAndLogin(guest);
        Long guestId = userIdOf(guest);
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(guestCookie)).andExpect(status().isCreated());

        createPost(guestCookie, lolPostBody("내 방도 열자"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_OTHER_ROOM"));

        assertThat(postCountOf(guestId)).isZero();
        // 들어가 있던 방은 그대로다
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + guestId)).isEqualTo(Long.toString(postId));
        assertThat(redisTemplate.keys("qm:room:*:host").stream()
                .filter(key -> Long.toString(guestId).equals(redisTemplate.opsForValue().get(key)))).isEmpty();
    }

    @Test
    @DisplayName("자동 매칭을 돌리는 중(활성 요청 키가 있다)이면 글을 쓸 수 없다 — 409 ALREADY_QUEUED 이고 글이 남지 않는다. 활성 요청 키는 건드리지 않는다")
    void createRolledBackWhileQueued() throws Exception
    {
        String host = newLoginId();
        Cookie cookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        String activeRequest = "qm:user:active-request:" + hostId;
        redisTemplate.opsForValue().set(activeRequest, "{\"status\":\"WAITING\"}", java.time.Duration.ofSeconds(60));
        try
        {
            createPost(cookie, lolPostBody("매칭 중인데"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_QUEUED"));

            assertThat(postCountOf(hostId)).isZero();
            assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + hostId)).isNull();
            // matching 의 키다 — EXISTS 로 보기만 한다
            assertThat(redisTemplate.opsForValue().get(activeRequest)).isEqualTo("{\"status\":\"WAITING\"}");
        }
        finally
        {
            redisTemplate.delete(activeRequest);
        }
        // 매칭을 그만두면 쓸 수 있다
        createLolPost(cookie);
    }

    // ---- 입장의 검사 ----

    @Test
    @DisplayName("입장은 글을 먼저 본다 — 없는 글 404 POST_NOT_FOUND · 끝난 글 409 POST_NOT_RECRUITING(방이 살아 있어도) · 글이 모집 중이면 방의 스크립트가 답한다(방이 사라졌으면 404 ROOM_NOT_FOUND)")
    void enterChecksThePostFirst() throws Exception
    {
        String expiredHost = newLoginId();
        String vanishedHost = newLoginId();
        String guest = newLoginId();
        Cookie expiredHostCookie = signupAndLogin(expiredHost);
        Long expired = createLolPost(expiredHostCookie);
        Long vanished = createLolPost(signupAndLogin(vanishedHost));
        Cookie guestCookie = signupAndLogin(guest);
        Long guestId = userIdOf(guest);

        mockMvc.perform(post("/api/v1/rooms/" + NO_SUCH_POST + "/members").cookie(guestCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/rooms/not-a-number/members").cookie(guestCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        // 방장이 글을 지웠다(만료) — 방은 아직 살아 있지만 새 사람은 못 들어온다
        mockMvc.perform(delete("/api/v1/posts/" + expired).cookie(expiredHostCookie)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/rooms/" + expired + "/members").cookie(guestCookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
        assertThat(redisTemplate.opsForSet().members(membersKey(expired))).doesNotContain(Long.toString(guestId));

        // 글은 아직 모집 중인데 방이 사라졌다(아무도 목록을 안 봐서 만료로 옮겨 적히기 전이다) — 스크립트가 답한다
        closeRoom(vanished);
        mockMvc.perform(post("/api/v1/rooms/" + vanished + "/members").cookie(guestCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));

        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + guestId)).isNull();
    }

    @Test
    @DisplayName("차단으로 숨겨진 글은 끝난 글이어도 404 POST_NOT_FOUND 다 — 숨김을 상태보다 먼저 본다(차단 관계인 사람에게 '모집이 끝났다'도 알려 주지 않는다)")
    void hiddenBeforeStatus() throws Exception
    {
        String host = newLoginId();
        String blocked = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie blockedCookie = signupAndLogin(blocked);
        Long postId = createLolPost(hostCookie);
        block(hostCookie, userIdOf(blocked));
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(hostCookie)).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(blockedCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("방 안의 누구와든 차단 관계면 못 들어간다(404) — 나중에 들어온 사람도 방 안의 나와 대조된다(D-20)")
    void blockedAgainstAnyoneInside() throws Exception
    {
        String host = newLoginId();
        String first = newLoginId();
        String second = newLoginId();
        Long postId = createLolPost(signupAndLogin(host));
        Cookie firstCookie = signupAndLogin(first);
        Cookie secondCookie = signupAndLogin(second);
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(firstCookie)).andExpect(status().isCreated());
        // second 가 방 안의 first 를 차단했다 — 방장과는 아무 사이도 아니다
        block(secondCookie, userIdOf(first));

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(secondCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        assertThat(redisTemplate.opsForSet().members(membersKey(postId))).hasSize(2);
    }

    @Test
    @DisplayName("이미 방에 들어와 있는 사람은 글이 끝나도 다시 불러 200(이미 들어와 있다)이다 — 새 사람만 409 POST_NOT_RECRUITING 이다")
    void memberRetryPassesTheGate() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Cookie latecomer = signupAndLogin(newLoginId());
        Long postId = createLolPost(hostCookie);
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(memberCookie)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");

        // 새로고침 — 확정된 방의 파티원이다
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(memberCookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(hostCookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(latecomer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
    }

    // ---- 방장 확정 한 길 ----

    @Test
    @DisplayName("방장 확정 한 요청이 방을 확정하고 파티를 적는다 — 204, 글은 CONFIRMED, 파티원은 확정 순간의 멤버. 다시 부르면 200 이고 파티는 하나다")
    void confirmRecordsTheParty() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(memberCookie)).andExpect(status().isCreated());
        // 사용자 번호일 수 없는 값 — 누가 손으로 넣었다. 파티원으로 적지 않는다
        redisTemplate.opsForSet().add(membersKey(postId), "NOT-A-NUMBER-" + "x".repeat(30));

        // 방장이 아니면 방의 스크립트가 거절한다 — 글은 그대로다
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(memberCookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_HOST"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());

        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
        assertThat(redisTemplate.opsForValue().get(confirmedKey(postId))).isEqualTo(Long.toString(postId));
        Map<String, Object> party = jdbcTemplate.queryForMap("select * from party.parties where post_id = ?", postId);
        assertThat(party).containsEntry("source", "BOARD").containsEntry("post_id", postId)
                .containsEntry("game", "LOL").containsEntry("status", "ACTIVE");
        assertThat(partyMembers(postId)).containsExactlyInAnyOrder(hostId, memberId);
        assertThat(jdbcTemplate.queryForList("select user_id from party.party_members "
                + "where party_id = (select id from party.parties where post_id = ?) and is_host", Long.class, postId))
                .containsExactly(hostId);

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select count(*) from party.parties where post_id = ?", Integer.class, postId)).isEqualTo(1);
        // 확정된 글은 지울 수 없다 — 되돌릴 수 없다
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(hostCookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_CONFIRMED"));
        // 목록 · 단건은 확정된 글의 멤버를 비운다
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.memberCount").value(0));
    }

    @Test
    @DisplayName("확정의 거절 — 혼자면 409 NOT_ENOUGH_MEMBERS · 없는 글 404 ROOM_NOT_FOUND · 지운(만료된) 글의 방은 409 POST_NOT_RECRUITING 이고 방도 확정되지 않는다")
    void confirmRejections() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Long postId = createLolPost(hostCookie);

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ENOUGH_MEMBERS"));
        mockMvc.perform(post("/api/v1/rooms/" + NO_SUCH_POST + "/confirm").cookie(hostCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(memberCookie)).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(hostCookie)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(confirmedKey(postId)))).isFalse();
        assertThat(statusOf(postId)).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("자가 치유 — 확정 표시 키는 있는데 글은 모집 중이면(앞선 확정의 커밋이 실패했다) 방장이 다시 눌렀을 때 200 이고 그때 기록된다")
    void confirmAgainHealsAnUnrecordedConfirmation() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        signupAndLogin(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        openRoom(postId, hostId, memberId);
        confirmRoom(postId);

        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isOk());

        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
        assertThat(partyMembers(postId)).containsExactlyInAnyOrder(hostId, memberId);
    }

    // ---- 도우미 ----

    private int postCountOf(Long hostId)
    {
        return jdbcTemplate.queryForObject("select count(*) from party.recruit_posts where host_id = ?", Integer.class, hostId);
    }

    private List<Long> partyMembers(Long postId)
    {
        return jdbcTemplate.queryForList("select user_id from party.party_members "
                + "where party_id = (select id from party.parties where post_id = ?)", Long.class, postId);
    }
}
