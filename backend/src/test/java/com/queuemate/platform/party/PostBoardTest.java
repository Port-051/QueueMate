package com.queuemate.platform.party;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 방의 상태가 걸리는 것 — 목록 · 단건 · 만료 · 차단 거르기 · 확정의 자가 치유 ({@code contracts/platform-api.md} "모집 글 · 목록").
 * 글을 쓰면 방이 같이 생기고, 그 밖의 방의 상태는 테스트가 Redis 에 직접 써서 만든다({@link PostTestSupport}).
 * 입장 · 방장 확정을 진짜로 거치는 것은 {@link PostRoomFlowTest} 다.
 */
class PostBoardTest extends PostTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    // ---- 목록 · 카드 ----

    @Test
    @DisplayName("목록의 한 줄 — 인원 · 방 안 전원의 카드(게임 프로필 · 전적) · full. 방장 먼저, 나머지는 닉네임순, 가입하지 않은 사람은 맨 뒤에 null 로 남는다")
    void listShowsRoomMembers() throws Exception
    {
        String host = newLoginId();
        String support = newLoginId();
        String noAccount = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        putGameAccount(hostCookie, "LOL", json("gameNickname", "host#KR1", "tier", "EMERALD_4", "mainPosition", "MID"));
        insertStats(hostId, "LOL", 180, 184, "{\"mostChampions\":[{\"championId\":103,\"games\":40,\"winRate\":55}]}");
        putGameAccount(signupAndLogin(support), "LOL", json("gameNickname", "sup#KR1", "tier", "GOLD_1", "mainPosition", "SUPPORT"));
        Long supportId = userIdOf(support);
        // LOL 계정은 없고 VALORANT 계정만 있다 — 이 글(LOL)의 카드에서는 profile 이 null 이다
        putGameAccount(signupAndLogin(noAccount), "VALORANT", json("gameNickname", "val#1", "mainPosition", "DUELIST"));
        Long noAccountId = userIdOf(noAccount);
        // stranger 는 이 앱에 가입하지 않은 사용자 번호다 — 방에 들어온 뒤 사라진 계정이 이렇게 남는다
        Long stranger = unknownUserId();
        Cookie viewer = signupAndLogin(newLoginId());

        Long postId = createLolPost(hostCookie, "TOP", "MID", "SUPPORT");
        openRoom(postId, hostId, supportId, noAccountId, stranger);

        JsonNode line = find(list(viewer, "LOL"), postId);
        assertThat(line).isNotNull();
        assertThat(line.get("status").asString()).isEqualTo("RECRUITING");
        assertThat(line.get("memberCount").asInt()).isEqualTo(4);
        assertThat(line.get("capacity").asInt()).isEqualTo(5);
        assertThat(line.get("full").asBoolean()).isFalse();
        assertThat(texts(line.get("wantedPositions"), null)).containsExactly("TOP", "MID", "SUPPORT");

        JsonNode members = line.get("members");
        List<String> expectedOrder = new ArrayList<>(List.of(support, noAccount));
        expectedOrder.sort((a, b) -> nicknameOf(a).compareTo(nicknameOf(b)));
        assertThat(longs(members, "userId"))
                .containsExactly(hostId, userIdOf(expectedOrder.get(0)), userIdOf(expectedOrder.get(1)), stranger);
        assertThat(members.get(0).get("host").asBoolean()).isTrue();
        assertThat(members.get(1).get("host").asBoolean()).isFalse();
        // 방장의 카드 — 게임 프로필 전체와 전적이 실린다(화면이 글을 펼치지 않고 한 줄에 전원을 보여 준다)
        JsonNode hostProfile = members.get(0).get("profile");
        assertThat(hostProfile.get("gameNickname").asString()).isEqualTo("host#KR1");
        assertThat(hostProfile.get("verified").asBoolean()).isFalse();
        assertThat(hostProfile.get("tier").asString()).isEqualTo("EMERALD_4");
        assertThat(hostProfile.get("mainPosition").asString()).isEqualTo("MID");
        assertThat(hostProfile.get("server").isNull()).isTrue();
        assertThat(hostProfile.get("stats").get("games").asInt()).isEqualTo(364);
        assertThat(hostProfile.get("stats").get("wins").asInt()).isEqualTo(180);
        assertThat(hostProfile.get("stats").get("winRate").asInt()).isEqualTo(49);
        assertThat(hostProfile.get("stats").get("detail").get("mostChampions").get(0).get("championId").asInt()).isEqualTo(103);
        assertThat(line.get("host").get("userId").asLong()).isEqualTo(hostId);
        assertThat(line.get("host").get("profile").get("gameNickname").asString()).isEqualTo("host#KR1");

        JsonNode supportCard = find(members, "userId", supportId);
        assertThat(supportCard.get("nickname").asString()).isEqualTo(nicknameOf(support));
        assertThat(supportCard.get("profile").get("stats").isNull()).isTrue();
        JsonNode noAccountCard = find(members, "userId", noAccountId);
        assertThat(noAccountCard.get("nickname").asString()).isEqualTo(nicknameOf(noAccount));
        assertThat(noAccountCard.get("profile").isNull()).isTrue();
        // 가입하지 않은 사람 — 빼지 않는다(memberCount 와 어긋난다). 닉네임도 프로필도 null 이다
        JsonNode strangerCard = members.get(3);
        assertThat(strangerCard.get("nickname").isNull()).isTrue();
        assertThat(strangerCard.get("profile").isNull()).isTrue();

        // 한 명 더 들어오면 만석이다 — 목록에는 그대로 남는다
        openRoom(postId, hostId, unknownUserId());
        JsonNode fullLine = find(list(viewer, "LOL"), postId);
        assertThat(fullLine.get("memberCount").asInt()).isEqualTo(5);
        assertThat(fullLine.get("full").asBoolean()).isTrue();

        // 단건도 같은 줄이다
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(5))
                .andExpect(jsonPath("$.members[0].userId").value(equalTo(hostId), Long.class));
    }

    @Test
    @DisplayName("정렬과 game 필터 — 최신순 하나다(만료된 글도 제자리에 남는다). game 이 없으면 세 게임 전부다")
    void orderAndGameFilter() throws Exception
    {
        Cookie first = signupAndLogin(newLoginId());
        Cookie second = signupAndLogin(newLoginId());
        Cookie third = signupAndLogin(newLoginId());
        Cookie valorant = signupAndLogin(newLoginId());
        Cookie viewer = signupAndLogin(newLoginId());
        Long oldest = createLolPost(first);
        Long expired = createLolPost(second);
        Long newest = createLolPost(third);
        Long other = createdId(createPost(valorant, postBody("VALORANT", "발로 하실 분", "{}", "SENTINEL")).andExpect(status().isCreated()));
        // 순서는 쓴 순서(id)로 정해진다 — 시각을 벌려 둘 필요가 없다(2026-09-24). 이름대로 oldest 가 가장 먼저 쓴 글이다
        mockMvc.perform(delete("/api/v1/posts/" + expired).cookie(second)).andExpect(status().isNoContent());

        List<Long> lol = longs(list(viewer, "LOL"), "postId");
        assertThat(lol).contains(oldest, expired, newest).doesNotContain(other);
        // 새 글이 먼저다. 가운데 글이 만료돼도 <b>맨 아래로 내려가지 않고 제자리에 남는다</b> — 2026-09-24 로 정렬에서 상태가 빠졌다
        assertThat(lol.indexOf(newest)).isLessThan(lol.indexOf(expired));
        assertThat(lol.indexOf(expired)).isLessThan(lol.indexOf(oldest));

        // 게임은 필수라 "세 게임 전부" 를 받는 길이 없다(2026-09-25 소유자 결정) — 게임마다 따로 받고, 서로 섞이지 않는다
        assertThat(longs(list(viewer, "VALORANT"), "postId")).contains(other).doesNotContain(newest);
    }

    // ---- 만료 ----

    @Test
    @DisplayName("글과 방은 같이 태어난다 — 방금 쓴 글도 방장이 들어 있다. 방장 키가 사라지면 그 자리에서 만료된다 — 기다려 주는 시간이 없다(2026-09-25 2단계)")
    void expiresAsSoonAsTheRoomIsGone() throws Exception
    {
        String host = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        Cookie viewer = signupAndLogin(newLoginId());
        Long postId = createLolPost(hostCookie);

        JsonNode fresh = find(list(viewer, "LOL"), postId);
        assertThat(fresh.get("status").asString()).isEqualTo("RECRUITING");
        assertThat(fresh.get("memberCount").asInt()).isEqualTo(1);
        assertThat(longs(fresh.get("members"), "userId")).containsExactly(hostId);

        // 방장이 나갔다(또는 수명이 다했다) — 쓴 지 몇 초밖에 안 됐어도 만료다. 옛 규칙("방을 본 적이 없으면 10분 기다린다")은 없다
        closeRoom(postId);
        JsonNode line = find(list(viewer, "LOL"), postId);
        assertThat(line.get("status").asString()).isEqualTo("EXPIRED");
        assertThat(line.get("memberCount").asInt()).isZero();
        assertThat(line.get("members").isEmpty()).isTrue();
        assertThat(line.get("host").get("userId").asLong()).isEqualTo(hostId);
        assertThat(statusOf(postId)).isEqualTo("EXPIRED");

        // 같은 번호의 방이 다시 생겨도 만료된 글은 돌아오지 않는다
        openRoom(postId, hostId);
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("EXPIRED");
    }

    /**
     * 2026-09-25 소유자 결정 — <b>보존 기간을 없앴다.</b> 그 전에는 만료 · 확정된 지 10분이 지난 글이 목록에서 빠졌다
     * ({@code platform.board.closed-retention}). 이 테스트는 <b>그것을 누가 되살리는 것을 막는다.</b>
     */
    @Test
    @DisplayName("오래 전에 만료 · 확정된 글도 목록에 그대로 남는다 — 보존 기간이 없다(2026-09-25 소유자 결정)")
    void closedPostsStayOnTheBoardForever() throws Exception
    {
        String host = newLoginId();
        String otherHost = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie otherCookie = signupAndLogin(otherHost);
        Cookie viewer = signupAndLogin(newLoginId());
        Long hostId = userIdOf(host);
        Long expired = createLolPost(hostCookie);
        Long confirmed = createLolPost(otherCookie);

        // 만료는 방이 사라진 것으로 만든다 — 목록이 그 자리에서 옮겨 적는다
        closeRoom(expired);
        assertThat(find(list(viewer, "LOL"), expired).get("status").asString()).isEqualTo("EXPIRED");

        // 확정은 방장 확정 요청 그대로다 — 방의 확정과 글의 기록이 한 요청이다
        openRoom(confirmed, userIdOf(otherHost), hostId);
        mockMvc.perform(post("/api/v1/rooms/" + confirmed + "/confirm").cookie(otherCookie)).andExpect(status().isNoContent());
        assertThat(find(list(viewer, "LOL"), confirmed).get("status").asString()).isEqualTo("CONFIRMED");

        // 끝난 지 한참 됐다 — 옛 규칙이라면 둘 다 목록에서 빠졌다
        jdbcTemplate.update("update party.recruit_posts set expired_at = now() - interval '30 days' where id = ?", expired);
        jdbcTemplate.update("update party.recruit_posts set confirmed_at = now() - interval '30 days' where id = ?", confirmed);

        JsonNode board = list(viewer, "LOL");
        assertThat(find(board, expired).get("status").asString()).isEqualTo("EXPIRED");
        assertThat(find(board, confirmed).get("status").asString()).isEqualTo("CONFIRMED");
        // 끝난 글은 멤버를 비운다 — 그대로다
        assertThat(find(board, expired).get("members").isEmpty()).isTrue();
        assertThat(find(board, expired).get("host").get("userId").asLong()).isEqualTo(hostId);
        mockMvc.perform(get("/api/v1/posts/" + expired).cookie(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    // ---- 글 고치기와 방 안 사람 (2026-09-24 소유자 결정) ----

    @Test
    @DisplayName("방에 방장 말고 누가 있으면 글을 고칠 수 없다(409 ROOM_HAS_OTHER_MEMBERS) — 방장 혼자거나 방이 사라졌으면 고쳐지고, 그 사람이 나가면 다시 고쳐진다")
    void noEditWhileOthersInRoom() throws Exception
    {
        String host = newLoginId();
        String guest = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        signupAndLogin(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);

        // 글을 쓰면서 방이 생겼다 — 방장 혼자다
        editPost(hostCookie, postId, "{\"title\":\"혼자 있다\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("혼자 있다"));

        // 누가 들어왔다 — 이제 어느 칸도 고칠 수 없다(NO_VOICE 를 보고 들어온 사람에게 알려 줄 길이 없다)
        openRoom(postId, hostId, guestId);
        editPost(hostCookie, postId, "{\"title\":\"제목만 바꾼다\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_OTHER_MEMBERS"));
        editPost(hostCookie, postId, "{\"voice\":\"NO_VOICE\",\"purpose\":\"FUN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_HAS_OTHER_MEMBERS"));
        // 아무 칸도 바뀌지 않았다 — DB 를 다시 읽어 본다
        assertThat(columnOf(postId, "title")).isEqualTo("혼자 있다");
        assertThat(columnOf(postId, "voice")).isEqualTo("REQUIRED");
        assertThat(columnOf(postId, "purpose")).isEqualTo("RANK_UP");

        redisTemplate.opsForSet().remove(membersKey(postId), String.valueOf(guestId));
        editPost(hostCookie, postId, "{\"title\":\"다 나갔다\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("다 나갔다"));
        // 방이 통째로 사라진 글 — 멤버가 없어 이 검사는 통과하고, 잠금 안의 판정은 아직 모집 중이라 고쳐진다(만료로 옮겨 적는 것은 목록 · 단건의 일이다)
        redisTemplate.delete(List.of(hostKey(postId), membersKey(postId)));
        editPost(hostCookie, postId, "{\"title\":\"방이 없다\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("방장 · 상태 검사가 방 안 사람 검사보다 먼저다 — 방에 사람이 있어도 남의 글은 403, 만료된 글은 409 POST_NOT_RECRUITING 이다")
    void hostAndStatusCheckedBeforeRoom() throws Exception
    {
        String host = newLoginId();
        String guest = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie guestCookie = signupAndLogin(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);
        openRoom(postId, hostId, guestId);

        // 남의 글에 대고 "방에 사람이 있다"를 알려 주면 그 자체가 새는 정보다
        editPost(guestCookie, postId, "{\"title\":\"내 것처럼\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_POST_HOST"));

        // 방장이 글을 지운다(만료) — 방에는 그대로 사람이 있다
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(hostCookie)).andExpect(status().isNoContent());
        editPost(hostCookie, postId, "{\"title\":\"늦었다\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
    }

    // ---- 차단 ----

    @Test
    @DisplayName("차단 — 방 안의 누구든 · 어느 방향이든 걸리면 목록에서 빠지고 단건 · 입장이 404 다. 풀면 다시 보이고 들어갈 수 있다. 내가 쓴 글은 빠지지 않는다")
    void blockHidesPost() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        String iBlockMember = newLoginId();
        String memberBlocksMe = newLoginId();
        String bystander = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Cookie iBlockMemberCookie = signupAndLogin(iBlockMember);
        Cookie memberBlocksMeCookie = signupAndLogin(memberBlocksMe);
        Cookie bystanderCookie = signupAndLogin(bystander);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        openRoom(postId, hostId, memberId);

        block(iBlockMemberCookie, memberId);
        block(memberCookie, userIdOf(memberBlocksMe));
        // 방장이 방 안의 멤버를 차단했다 — 그래도 자기 글은 보인다(내보내는 것은 강퇴다)
        block(hostCookie, memberId);

        for(Cookie hidden : List.of(iBlockMemberCookie, memberBlocksMeCookie))
        {
            assertThat(find(list(hidden, "LOL"), postId)).isNull();
            mockMvc.perform(get("/api/v1/posts/" + postId).cookie(hidden))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
            mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(hidden))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }
        assertThat(find(list(bystanderCookie, "LOL"), postId)).isNotNull();
        assertThat(find(list(hostCookie, "LOL"), postId).get("memberCount").asInt()).isEqualTo(2);
        // 방장은 이미 들어 있다 — 다시 불러도 "이미 들어와 있다"(200)다
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(hostCookie)).andExpect(status().isOk());
        // 방 안에 있는 멤버라도 방장과 차단 관계면 그 글이 숨겨진다 — 어느 쪽이 차단했든 같다
        assertThat(find(list(memberCookie, "LOL"), postId)).isNull();

        // 차단을 풀면 다시 보인다
        mockMvc.perform(delete("/api/v1/blocks/" + memberId).cookie(iBlockMemberCookie)).andExpect(status().isNoContent());
        assertThat(find(list(iBlockMemberCookie, "LOL"), postId)).isNotNull();
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(iBlockMemberCookie)).andExpect(status().isCreated());
        track(postId, userIdOf(iBlockMember));
    }

    @Test
    @DisplayName("방장 혼자인 글과 만료된 글(멤버를 비운다)은 방장과의 사이를 본다")
    void blockAgainstHostOnly() throws Exception
    {
        String host = newLoginId();
        String blocked = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie blockedByHost = signupAndLogin(blocked);
        Cookie viewer = signupAndLogin(newLoginId());
        Long postId = createLolPost(hostCookie);
        block(hostCookie, userIdOf(blocked));

        assertThat(find(list(blockedByHost, "LOL"), postId)).isNull();
        assertThat(find(list(viewer, "LOL"), postId)).isNotNull();

        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(hostCookie)).andExpect(status().isNoContent());
        assertThat(find(list(blockedByHost, "LOL"), postId)).isNull();
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(blockedByHost)).andExpect(status().isNotFound());
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("EXPIRED");
    }

    // ---- 확정의 자가 치유 ----

    @Test
    @DisplayName("자가 치유 — 목록이 '모집 중인데 확정 표시 키가 있는 글'(확정의 커밋이 실패했다)을 보면 그 자리에서 기록한다. 확정된 글은 방장 키가 없어져도 만료되지 않는다")
    void listDiscoversConfirmation() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        signup(member, PASSWORD, nicknameOf(member)).andExpect(status().isCreated());
        Cookie viewer = signupAndLogin(newLoginId());
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        openRoom(postId, hostId, memberId);
        list(viewer, "LOL");
        // 확정 스크립트는 성공했는데 글의 기록이 커밋되지 않았다 — 확정 표시 키만 있고 DB 는 모집 중이다
        confirmRoom(postId);

        JsonNode line = find(list(viewer, "LOL"), postId);
        assertThat(line.get("status").asString()).isEqualTo("CONFIRMED");
        // 확정된 글은 목록에서 멤버를 비운다. 방장의 카드는 남는다
        assertThat(line.get("members").isEmpty()).isTrue();
        assertThat(line.get("host").get("userId").asLong()).isEqualTo(hostId);
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
        assertThat(partyMembers(postId)).containsExactlyInAnyOrder(hostId, memberId);

        // 확정한 방은 방장 키만 잠깐 없을 수 있고(D-23), 방이 통째로 없어져도 확정된 글은 끝까지 CONFIRMED 다
        redisTemplate.delete(hostKey(postId));
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("CONFIRMED");
        closeRoom(postId);
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("CONFIRMED");
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");

        // 확정된 방장은 (방에서 나온 뒤에) 새 글을 쓸 수 있다 — "모집 중인 글은 하나"에 걸리지 않는다. closeRoom 이 입장 표시 키도 지웠다
        createLolPost(hostCookie);
    }

    @Test
    @DisplayName("자가 치유 — 확정 표시 키는 있는데 멤버 SET 이 비어 있으면(이미 다 나갔다) 방장만 파티원으로 기록한다")
    void healWithEmptyMembers() throws Exception
    {
        String host = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        Long postId = createLolPost(hostCookie);
        redisTemplate.delete(membersKey(postId));
        confirmRoom(postId);

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(partyMembers(postId)).containsExactly(hostId);
    }

    @Test
    @DisplayName("방장 확정 요청과 목록(자가 치유)을 여러 스레드가 동시에 불러도 파티는 하나이고 파티원은 한 벌이다")
    void concurrentConfirms() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        openRoom(postId, hostId, memberId);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try
        {
            for(int i = 0; i < threads; i++)
            {
                // 짝수는 방장의 확정, 홀수는 멤버의 목록이다 — 확정이 Redis 에 먼저 쓰고 DB 는 커밋 전인 틈을 목록이 보면 자가 치유가 같은 기록을 하려 든다
                boolean viaList = (i % 2 == 1);
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return (viaList
                            ? mockMvc.perform(get("/api/v1/posts").param("game", "LOL").cookie(memberCookie))
                            : mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)))
                            .andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for(Future<Integer> future : futures)
            {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            // 확정은 하나만 204(확정했다)이고 나머지는 200(이미 확정)이다. 목록은 전부 200 이다
            assertThat(statuses).filteredOn(code -> code == 204).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(threads - 1);
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
        // "한 글에 파티 하나"를 지키는 것은 파티의 PK 가 아니라 UNIQUE (post_id) 다 — 파티의 id 는 DB 가 따로 매긴다
        assertThat(jdbcTemplate.queryForObject("select count(*) from party.parties where post_id = ?", Integer.class, postId)).isEqualTo(1);
        assertThat(partyMembers(postId)).containsExactlyInAnyOrder(hostId, memberId);
    }

    // ---- 읽기만 한다 · N+1 ----

    @Test
    @DisplayName("글의 읽기와 지우기는 방 키에 쓰지 않는다 — 목록 · 단건 · 거절된 고치기 · 지우기를 돈 뒤에도 qm:room:* · qm:user:* · qm:party:* 키가 그대로이고 값 · 수명도 그대로다")
    void readsNeverWriteRoomKeys() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        Long other = createLolPost(memberCookie);
        openRoom(postId, hostId, memberId);
        // 수명을 눈에 띄게 다르게 걸어 둔다 — main 이 EXPIRE 를 다시 걸면 드러난다
        redisTemplate.expire(hostKey(postId), Duration.ofSeconds(300));

        Set<String> before = foreignKeys();
        Long ttlBefore = redisTemplate.getExpire(hostKey(postId));

        list(hostCookie, "LOL");
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(memberCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts/" + other).cookie(hostCookie)).andExpect(status().isOk());
        editPost(hostCookie, postId, "{\"title\":\"사람이 있다\"}").andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/posts/" + other).cookie(memberCookie)).andExpect(status().isNoContent());

        assertThat(foreignKeys()).isEqualTo(before);
        // 방 키의 값은 전부 문자열이다 — 사용자 번호 · 글 번호를 십진 문자열로 적은 것이다
        assertThat(redisTemplate.opsForValue().get(hostKey(postId))).isEqualTo(Long.toString(hostId));
        assertThat(redisTemplate.opsForSet().members(membersKey(postId)))
                .containsExactlyInAnyOrder(Long.toString(hostId), Long.toString(memberId));
        assertThat(redisTemplate.getExpire(hostKey(postId))).isBetween(ttlBefore - 60, ttlBefore);
    }

    @Test
    @DisplayName("목록의 SQL 문장 수는 글 수 · 사람 수에 비례해 늘지 않는다 — 글 · 찾는 포지션 · 프로필(게임마다) · 차단")
    void listDoesNotIssueQueriesPerPost() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        List<Long> posts = new ArrayList<>();
        for(int i = 0; i < 5; i++)
        {
            String host = newLoginId();
            String member = newLoginId();
            Cookie hostCookie = signupAndLogin(host);
            putGameAccount(hostCookie, "LOL", json("gameNickname", "h" + i, "mainPosition", "MID"));
            putGameAccount(signupAndLogin(member), "LOL", json("gameNickname", "m" + i, "mainPosition", "TOP"));
            Long postId = createLolPost(hostCookie, "TOP", "MID");
            openRoom(postId, userIdOf(host), userIdOf(member), unknownUserId());
            posts.add(postId);
        }
        // 한 번 그려 둔다 — 다음 조회는 옮겨 적을 것이 없는 평소의 목록이다(이 DB 에 남은 다른 테스트의 글이 이번에 만료로 옮겨질 수 있다)
        list(viewer, "LOL");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try
        {
            statistics.clear();
            JsonNode lines = list(viewer, "LOL");
            long statements = statistics.getPrepareStatementCount();

            for(Long postId : posts)
            {
                assertThat(find(lines, postId).get("memberCount").asInt()).isEqualTo(3);
                assertThat(texts(find(lines, postId).get("wantedPositions"), null)).containsExactly("TOP", "MID");
            }
            // 글 1 + 찾는 포지션 1 + LOL 프로필 1 + 차단 1. 이 DB 에는 다른 테스트의 글도 섞여 있지만 문장 수는 같다.
            // 넉넉히 잡아도 글 5개 · 사람 15명에 비례했다면(글마다 1문장만 더해도 9) 넘는 값이다
            assertThat(statements).isLessThanOrEqualTo(5);
        }
        finally
        {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ---- 도우미 ----

    /** 글의 어느 칸이 정말 안 바뀌었는지 볼 때 쓴다 — 응답이 아니라 DB 를 읽는다 */
    private String columnOf(Long postId, String column)
    {
        return jdbcTemplate.queryForObject("select " + column + " from party.recruit_posts where id = ?", String.class, postId);
    }

    /** 그 글로 기록된 파티의 파티원. 파티의 id 는 DB 가 매긴 번호라 글의 번호로 찾는다({@code parties.post_id}) */
    private List<Long> partyMembers(Long postId)
    {
        return jdbcTemplate.queryForList("select user_id from party.party_members "
                + "where party_id = (select id from party.parties where post_id = ?)", Long.class, postId);
    }

    private static JsonNode find(JsonNode array, String field, Long value)
    {
        for(JsonNode one : array)
        {
            if(value.equals(one.get(field).asLong()))
            {
                return one;
            }
        }
        return null;
    }

    /** 게임사 API 가 채웠다고 치고 전적 줄을 직접 넣는다 — 가져오는 기능이 아직 없다. 판 수는 승 + 패다(LoL 이라 승/패가 있다) */
    private void insertStats(Long userId, String game, int wins, int losses, String detailJson)
    {
        Long gameAccountId = jdbcTemplate.queryForObject(
                "select id from account.game_accounts where user_id = ? and game = ?", Long.class, userId, game);
        jdbcTemplate.update("insert into account.game_account_stats "
                + "(game_account_id, games, wins, losses, avg_kills, avg_deaths, avg_assists, win_streak, detail, source, synced_at) "
                + "values (?, ?, ?, ?, 10.6, 5.7, 5.8, 3, ?::jsonb, 'API', now())",
                gameAccountId, wins + losses, wins, losses, detailJson);
    }
}
