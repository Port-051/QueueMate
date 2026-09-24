package com.queuemate.platform.party;

import com.queuemate.platform.common.security.JwtKeys;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 * 방의 상태가 걸리는 것 — 목록 · 단건 · 만료 · 차단 거르기 · 입장권 · 방장 확정의 기록 ({@code contracts/platform-api.md} "모집 글 · 목록 · 입장권").
 * <b>방의 상태는 테스트가 {@code room} 인 척 Redis 에 직접 써서 만든다</b>({@link PostTestSupport}).
 */
class PostBoardTest extends PostTestSupport {

    @Autowired
    JwtKeys jwtKeys;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    // ---- 목록 · 카드 ----

    @Test
    @DisplayName("목록의 한 줄 — 인원 · 방 안 전원의 카드(게임 프로필 · 전적) · filledPositions · full. 방장 먼저, 나머지는 닉네임순, 가입하지 않은 사람은 맨 뒤에 null 로 남는다")
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
        // stranger 는 이 앱에 가입하지 않은 사용자 번호다 — room 이 지금 인증 없이 돌아 멤버 SET 에 들어올 수 있다
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
        // 찾는 포지션 ∩ 방 안 사람들의 주 포지션. DUELIST 는 다른 게임의 계정이라 세지 않는다
        assertThat(texts(line.get("filledPositions"), null)).containsExactly("MID", "SUPPORT");

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

        assertThat(longs(list(viewer, "VALORANT"), "postId")).contains(other).doesNotContain(newest);
        List<Long> all = longs(body(mockMvc.perform(get("/api/v1/posts").cookie(viewer)).andExpect(status().isOk())).get("posts"), "postId");
        assertThat(all).contains(oldest, expired, newest, other);
    }

    // ---- 만료 ----

    @Test
    @DisplayName("방금 쓴 글(방이 아직 없다)은 목록을 그려도 만료되지 않는다. 방장 키를 한 번 본 뒤에 키가 사라지면 만료된다 — 멤버는 비운다")
    void expiresOnlyAfterRoomWasSeen() throws Exception
    {
        String host = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        Cookie viewer = signupAndLogin(newLoginId());
        Long postId = createLolPost(hostCookie);

        // 방 만들기를 아직 안 불렀다 — 방장 키가 없지만 만료가 아니다
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("RECRUITING");
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");
        assertThat(roomSeenAt(postId)).isNull();

        openRoom(postId, hostId);
        assertThat(find(list(viewer, "LOL"), postId).get("memberCount").asInt()).isEqualTo(1);
        Instant seenAt = roomSeenAt(postId);
        assertThat(seenAt).isNotNull();
        // "처음 본 순간"이다 — 다시 봐도 덮어쓰지 않는다
        list(viewer, "LOL");
        assertThat(roomSeenAt(postId)).isEqualTo(seenAt);

        closeRoom(postId);
        JsonNode line = find(list(viewer, "LOL"), postId);
        assertThat(line.get("status").asString()).isEqualTo("EXPIRED");
        assertThat(line.get("memberCount").asInt()).isZero();
        assertThat(line.get("members").isEmpty()).isTrue();
        assertThat(line.get("host").get("userId").asLong()).isEqualTo(hostId);
        assertThat(statusOf(postId)).isEqualTo("EXPIRED");

        // 같은 id 로 방이 다시 생겨도 만료된 글은 돌아오지 않는다
        openRoom(postId, hostId);
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("방이 한 번도 안 생긴 글은 쓴 지 10분이 지나야 만료된다. 만료 · 확정된 글은 10분 뒤 목록에서 빠진다(단건은 남는다)")
    void graceAndRetention() throws Exception
    {
        Cookie hostCookie = signupAndLogin(newLoginId());
        Cookie viewer = signupAndLogin(newLoginId());
        Long postId = createLolPost(hostCookie);

        jdbcTemplate.update("update party.recruit_posts set created_at = now() - interval '9 minutes' where id = ?", postId);
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("RECRUITING");

        jdbcTemplate.update("update party.recruit_posts set created_at = now() - interval '11 minutes' where id = ?", postId);
        assertThat(find(list(viewer, "LOL"), postId).get("status").asString()).isEqualTo("EXPIRED");
        assertThat(statusOf(postId)).isEqualTo("EXPIRED");

        // 만료된 지 10분이 안 됐다 — 목록에 남아 있다
        assertThat(find(list(viewer, "LOL"), postId)).isNotNull();
        jdbcTemplate.update("update party.recruit_posts set expired_at = now() - interval '11 minutes' where id = ?", postId);
        assertThat(find(list(viewer, "LOL"), postId)).isNull();
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    // ---- 차단 ----

    @Test
    @DisplayName("차단 — 방 안의 누구든 · 어느 방향이든 걸리면 목록에서 빠지고 단건 · 입장권이 404 다. 풀면 다시 보인다. 내가 쓴 글은 빠지지 않는다")
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
            mockMvc.perform(post("/api/v1/posts/" + postId + "/ticket").cookie(hidden))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        }
        assertThat(find(list(bystanderCookie, "LOL"), postId)).isNotNull();
        assertThat(find(list(hostCookie, "LOL"), postId).get("memberCount").asInt()).isEqualTo(2);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ticket").cookie(hostCookie)).andExpect(status().isOk());
        // 방 안에 있는 멤버라도 방장과 차단 관계면 그 글이 숨겨진다 — 어느 쪽이 차단했든 같다
        assertThat(find(list(memberCookie, "LOL"), postId)).isNull();

        // 차단을 풀면 다시 보인다
        mockMvc.perform(delete("/api/v1/blocks/" + memberId).cookie(iBlockMemberCookie)).andExpect(status().isNoContent());
        assertThat(find(list(iBlockMemberCookie, "LOL"), postId)).isNotNull();
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ticket").cookie(iBlockMemberCookie)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("방이 아직 없는 글과 만료된 글은 방장과의 사이를 본다")
    void blockAgainstHostWithoutRoom() throws Exception
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

    // ---- 입장권 ----

    @Test
    @DisplayName("입장권의 클레임은 계약대로다 — token_use=room_ticket · room_id · host_id · 수명 60초. access 토큰으로는 통하지 않는다")
    void ticketClaims() throws Exception
    {
        String host = newLoginId();
        String guest = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie guestCookie = signupAndLogin(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);
        openRoom(postId, hostId);

        JsonNode response = body(mockMvc.perform(post("/api/v1/posts/" + postId + "/ticket").cookie(guestCookie))
                .andExpect(status().isOk()));
        // 본문의 roomId · hostId 는 숫자다 — 토큰 안의 room_id · host_id 는 그 숫자의 십진 문자열이다
        assertThat(response.get("roomId").asLong()).isEqualTo(postId);
        assertThat(response.get("hostId").asLong()).isEqualTo(hostId);

        // token_use 를 보지 않는 디코더로 연다 — 앱의 디코더는 access 만 받는다
        Jwt ticket = NimbusJwtDecoder.withPublicKey(jwtKeys.publicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build()
                .decode(response.get("ticket").asString());
        assertThat(ticket.getHeaders()).containsEntry("alg", "RS256").containsKey("kid");
        assertThat(ticket.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "sub", "iat", "exp", "jti", "token_use", "room_id", "host_id");
        assertThat(ticket.getClaimAsString("iss")).isEqualTo("queuemate-platform");
        assertThat(ticket.getSubject()).isEqualTo(Long.toString(guestId));
        assertThat(ticket.getClaimAsString("token_use")).isEqualTo("room_ticket");
        assertThat(ticket.getClaimAsString("room_id")).isEqualTo(Long.toString(postId));
        assertThat(ticket.getClaimAsString("host_id")).isEqualTo(Long.toString(hostId));
        assertThat(UUID.fromString(ticket.getId())).isNotNull();
        assertThat(Duration.between(ticket.getIssuedAt(), ticket.getExpiresAt())).isEqualTo(Duration.ofSeconds(60));
        assertThat(Instant.parse(response.get("expiresAt").asString())).isEqualTo(ticket.getExpiresAt());

        // 방장도 같은 요청으로 받는다 — sub 가 host_id 와 같다
        JsonNode hostResponse = body(mockMvc.perform(post("/api/v1/posts/" + postId + "/ticket").cookie(hostCookie))
                .andExpect(status().isOk()));
        assertThat(hostResponse.get("hostId").asLong()).isEqualTo(hostId);

        // 같은 키로 서명했지만 입장권을 쿠키에 넣어 와도 로그인이 되지 않는다
        mockMvc.perform(get("/api/v1/users/me").cookie(new Cookie("qm_access", response.get("ticket").asString())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("모집 중이 아닌 글에는 입장권을 내주지 않는다(409 POST_NOT_RECRUITING) — 발급 직전에 방 키를 새로 읽어 만료 · 확정을 가린다")
    void noTicketForClosedPosts() throws Exception
    {
        String vanishedHost = newLoginId();
        String confirmedHost = newLoginId();
        String confirmedMember = newLoginId();
        Cookie guest = signupAndLogin(newLoginId());
        Long vanished = createLolPost(signupAndLogin(vanishedHost));
        Long confirmed = createLolPost(signupAndLogin(confirmedHost));
        signup(confirmedMember, PASSWORD, nicknameOf(confirmedMember)).andExpect(status().isCreated());
        Long confirmedHostId = userIdOf(confirmedHost);
        Long confirmedMemberId = userIdOf(confirmedMember);

        // 방이 떠 있는 동안에는 받는다(이때 room_seen_at 이 적힌다)
        openRoom(vanished, userIdOf(vanishedHost));
        mockMvc.perform(post("/api/v1/posts/" + vanished + "/ticket").cookie(guest)).andExpect(status().isOk());
        assertThat(roomSeenAt(vanished)).isNotNull();
        // 목록을 다시 그리지 않아도 입장권 발급이 스스로 알아챈다
        closeRoom(vanished);
        mockMvc.perform(post("/api/v1/posts/" + vanished + "/ticket").cookie(guest))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
        assertThat(statusOf(vanished)).isEqualTo("EXPIRED");

        // 확정 표시 키가 있다 — 입장권 발급이 길 ② 로 확정을 기록하고 거절한다
        openRoom(confirmed, confirmedHostId, confirmedMemberId);
        confirmRoom(confirmed);
        mockMvc.perform(post("/api/v1/posts/" + confirmed + "/ticket").cookie(guest))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
        assertThat(statusOf(confirmed)).isEqualTo("CONFIRMED");
        assertThat(partyMembers(confirmed)).containsExactlyInAnyOrder(confirmedHostId, confirmedMemberId);

        mockMvc.perform(post("/api/v1/posts/" + NO_SUCH_POST + "/ticket").cookie(guest))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ---- 방장 확정의 기록 ----

    @Test
    @DisplayName("confirm(길 ①) — 확정 표시 키가 없으면 409 ROOM_NOT_CONFIRMED, 있으면 글이 CONFIRMED 가 되고 파티와 파티원이 기록된다. 두 번 불러도 하나다")
    void confirmRecordsParty() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        // 숫자가 아닌 값은 사용자 번호일 수 없다 — room 이 인증 없이 도는 동안 멤버 SET 에 들어올 수 있다
        openRoom(postId, hostId, memberId, "NOT-A-NUMBER-" + "x".repeat(30));

        mockMvc.perform(post("/api/v1/posts/" + postId + "/confirm").cookie(hostCookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_NOT_CONFIRMED"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");

        confirmRoom(postId);
        // 부르는 사람은 방장이 아니어도 된다 — 이 앱은 그 말을 믿지 않고 읽은 것만 기록한다
        mockMvc.perform(post("/api/v1/posts/" + postId + "/confirm").cookie(memberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.members[0].userId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.members[0].host").value(true))
                .andExpect(jsonPath("$.members[1].userId").value(equalTo(memberId), Long.class));
        mockMvc.perform(post("/api/v1/posts/" + postId + "/confirm").cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.memberCount").value(2));

        // 파티의 id 는 DB 가 매긴다 — 글의 id 가 아니다. 글과 파티를 잇는 것은 post_id 다
        Map<String, Object> party = jdbcTemplate.queryForMap("select * from party.parties where post_id = ?", postId);
        assertThat(party).containsEntry("source", "BOARD").containsEntry("post_id", postId)
                .containsEntry("game", "LOL").containsEntry("status", "ACTIVE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from party.parties where post_id = ?", Integer.class, postId)).isEqualTo(1);
        // 사용자 번호일 수 없는 값(숫자가 아닌 값)은 파티원으로 적지 않는다
        assertThat(partyMembers(postId)).containsExactlyInAnyOrder(hostId, memberId);
        assertThat(jdbcTemplate.queryForList("select user_id from party.party_members "
                + "where party_id = (select id from party.parties where post_id = ?) and is_host",
                Long.class, postId)).containsExactly(hostId);
        assertThat(jdbcTemplate.queryForObject("select confirmed_at is not null from party.recruit_posts where id = ?",
                Boolean.class, postId)).isTrue();

        // 확정된 글은 지울 수 없다 — 되돌릴 수 없다
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(hostCookie))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_CONFIRMED"));
        mockMvc.perform(post("/api/v1/posts/" + NO_SUCH_POST + "/confirm").cookie(hostCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("길 ② — 목록이 '모집 중인데 확정 표시 키가 있는 글'을 보면 그 자리에서 기록한다. 확정된 글은 방장 키가 없어져도 만료되지 않는다")
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

        // 확정된 방장은 새 글을 쓸 수 있다 — "모집 중인 글은 하나"에 걸리지 않는다
        createLolPost(hostCookie);
    }

    @Test
    @DisplayName("확정 표시 키는 있는데 멤버 SET 이 비어 있으면(이미 다 나갔다) 방장만 파티원으로 기록한다")
    void confirmWithEmptyMembers() throws Exception
    {
        String host = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        Long postId = createLolPost(hostCookie);
        confirmRoom(postId);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/confirm").cookie(hostCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(equalTo(hostId), Long.class));
        assertThat(partyMembers(postId)).containsExactly(hostId);
    }

    @Test
    @DisplayName("confirm 과 목록(길 ②)을 여러 스레드가 동시에 불러도 파티는 하나이고 파티원은 한 벌이다")
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
        confirmRoom(postId);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try
        {
            for(int i = 0; i < threads; i++)
            {
                boolean viaList = (i % 4 == 3);
                Cookie cookie = (i % 2 == 0) ? hostCookie : memberCookie;
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return (viaList
                            ? mockMvc.perform(get("/api/v1/posts").param("game", "LOL").cookie(cookie))
                            : mockMvc.perform(post("/api/v1/posts/" + postId + "/confirm").cookie(cookie)))
                            .andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for(Future<Integer> future : futures)
            {
                assertThat(future.get(60, TimeUnit.SECONDS)).isEqualTo(200);
            }
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
    @DisplayName("main 코드는 방 키에 쓰지 않는다 — 목록 · 단건 · 입장권 · confirm 을 돈 뒤에도 qm:room:* · qm:user:* · qm:party:* 키가 그대로이고 값 · 수명도 그대로다")
    void neverWritesForeignKeys() throws Exception
    {
        String host = newLoginId();
        String member = newLoginId();
        Cookie hostCookie = signupAndLogin(host);
        Cookie memberCookie = signupAndLogin(member);
        Long hostId = userIdOf(host);
        Long memberId = userIdOf(member);
        Long postId = createLolPost(hostCookie);
        Long roomless = createLolPost(memberCookie);
        openRoom(postId, hostId, memberId);
        confirmRoom(postId);
        // 수명을 눈에 띄게 다르게 걸어 둔다 — main 이 EXPIRE 를 다시 걸면 드러난다
        redisTemplate.expire(hostKey(postId), Duration.ofSeconds(300));

        Set<String> before = foreignKeys();
        Long ttlBefore = redisTemplate.getExpire(hostKey(postId));

        list(hostCookie, "LOL");
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(memberCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts/" + roomless).cookie(hostCookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/posts/" + roomless + "/ticket").cookie(hostCookie)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/ticket").cookie(memberCookie)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/posts/" + roomless).cookie(memberCookie)).andExpect(status().isNoContent());

        assertThat(foreignKeys()).isEqualTo(before);
        // 방 키의 값은 전부 문자열이다 — 사용자 번호 · 글 번호를 십진 문자열로 적은 것이다
        assertThat(redisTemplate.opsForValue().get(hostKey(postId))).isEqualTo(Long.toString(hostId));
        assertThat(redisTemplate.opsForSet().members(membersKey(postId)))
                .containsExactlyInAnyOrder(Long.toString(hostId), Long.toString(memberId));
        assertThat(redisTemplate.opsForValue().get(confirmedKey(postId))).isEqualTo(Long.toString(postId));
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
        // 한 번 그려서 room_seen_at 을 적어 둔다 — 다음 조회는 옮겨 적을 것이 없는 평소의 목록이다
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
                assertThat(texts(find(lines, postId).get("filledPositions"), null)).containsExactly("TOP", "MID");
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

    private Instant roomSeenAt(Long postId)
    {
        java.sql.Timestamp seenAt = jdbcTemplate.queryForObject(
                "select room_seen_at from party.recruit_posts where id = ?", java.sql.Timestamp.class, postId);
        return seenAt == null ? null : seenAt.toInstant();
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
