package com.queuemate.platform.account;

import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.account.service.AccountDeletionService;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.PostTestSupport;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.room.RoomProperties;
import com.queuemate.platform.room.notification.BoardSubscriber;
import com.queuemate.platform.room.notification.PushSubscriber;
import com.queuemate.platform.room.service.RoomMemberService;
import com.queuemate.platform.room.service.RoomNotifier;
import com.queuemate.platform.room.service.RoomService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>회원 탈퇴</b> — {@code DELETE /api/v1/users/me}(2026-10-02 소유자 결정 · {@code contracts/platform-api.md} P-48). 카카오 · 디스코드 · 개인정보 보호법이 요구하는
 * "전부 파기" 를 HTTP 로 본다. 소유자가 정한 것 — ① 그 사람의 데이터를 지체 없이 전부 지운다 ② <b>확정된 파티 기록은 남긴다</b>(작성자 칸만 빈다 — V9)
 * ③ 방 안이면 평소 나가기와 같은 규칙으로 나간 뒤 탈퇴한다 ④ 매칭 대기 중이면 409 {@code ALREADY_QUEUED} ⑤ 제공자 쪽 연결 끊기는 하지 않는다.
 *
 * <p>방장이 빈 확정된 글은 뒷정리(사용자의 글을 지운다 — {@code ApiTestSupport})가 찾지 못해 {@link #hostless} 로 직접 지운다.
 */
class AccountDeletionTest extends PostTestSupport {

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomMemberService roomMemberService;

    @Autowired
    private PostService postService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private RoomProperties roomProperties;

    @Autowired
    private RoomNotifier roomNotifier;

    @Autowired
    private RedisScript<Long> createRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> confirmRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> leaveRoomScript;

    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> enterMatchRoomScript;

    /** 방장이 탈퇴해 {@code host_id} 가 빈 글 — 끝나면 지운다(파티 · 파티원은 글의 CASCADE 가 지운다) */
    private final List<Long> hostless = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteHostlessPosts()
    {
        hostless.forEach(postId -> jdbcTemplate.update("delete from recruit_posts where id = ?", postId));
        hostless.clear();
    }

    // ---- 지우는 범위 ----

    @Test
    @DisplayName("204 — 그 사람의 줄이 테이블마다 전부 지워진다(소셜 연결 · 게임 계정 · 전적 · 차단 양방향 · 친구 요청 · 친구 · 신고 낸 것 · 받은 것 · 최근 함께한 사람 · 파티원 줄 · 만료된 글). "
            + "남의 파티 · 남의 파티원 줄은 남고, 쿠키 둘을 지운다")
    void deletesEverythingOfTheUser() throws Exception
    {
        String goneName = newNickname();
        Cookie goneCookie = login(goneName);
        Long gone = userIdOf(goneName);
        Long other = insertUser();
        Long third = insertUser();
        jdbcTemplate.update("insert into social_identities (provider, provider_user_id, user_id, created_at) values ('KAKAO', ?, ?, now())",
                "del-" + UUID.randomUUID(), gone);
        Long accountId = insertGameAccount(gone, "LOL", "탈퇴#KR1", "GOLD_4");
        jdbcTemplate.update("insert into game_account_stats (game_account_id, games, source, synced_at) values (?, 10, 'API', now())", accountId);
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now()), (?, ?, now())", gone, other, third, gone);
        jdbcTemplate.update("insert into friend_requests (requester_id, receiver_id, status, created_at) values (?, ?, 'PENDING', now()), (?, ?, 'PENDING', now())",
                other, gone, gone, third);
        jdbcTemplate.update("insert into friendships (user_low_id, user_high_id, created_at) values (?, ?, now())",
                Math.min(gone, other), Math.max(gone, other));
        jdbcTemplate.update("insert into reports (reporter_id, target_user_id, reason, created_at) values (?, ?, 'ABUSE', now()), (?, ?, 'SPAM', now())",
                gone, other, third, gone);
        // 남의 확정된 게시판 파티의 파티원이다 — 그 사람의 줄만 빠지고 파티와 방장의 줄은 남는다
        Long othersPost = insertPostRow(other, "CONFIRMED");
        Long partyId = jdbcTemplate.queryForObject("insert into parties (source, post_id, game, status, created_at, closed_at) "
                + "values ('BOARD', ?, 'LOL', 'CLOSED', now(), now()) returning id", Long.class, othersPost);
        jdbcTemplate.update("insert into party_members (party_id, user_id, is_host, joined_at) values (?, ?, true, now()), (?, ?, false, now())",
                partyId, other, partyId, gone);
        jdbcTemplate.update("insert into recent_players (user_id, other_user_id, last_party_id, last_played_at) values (?, ?, ?, now()), (?, ?, ?, now())",
                gone, other, partyId, other, gone, partyId);
        // 그 사람이 쓴 만료된 글 — 지워진다(찾는 포지션의 줄도 딸려서)
        Long expired = insertPostRow(gone, "EXPIRED");
        jdbcTemplate.update("insert into recruit_post_positions (post_id, position) values (?, 'MID')", expired);

        MvcResult result = deleteMe(goneCookie).andExpect(status().isNoContent()).andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String access = setCookies.stream().filter(value -> value.startsWith("qm_access=")).findFirst().orElseThrow();
        String refresh = setCookies.stream().filter(value -> value.startsWith("qm_refresh=")).findFirst().orElseThrow();
        // 로그아웃과 같은 쿠키다 — refresh 는 요청에 실려 오지 않지만 지우는 Set-Cookie 는 그 Path 로 보낸다
        assertThat(access).startsWith("qm_access=;").contains("Max-Age=0").contains("Path=/");
        assertThat(refresh).startsWith("qm_refresh=;").contains("Max-Age=0").contains("Path=/api/v1/auth/refresh");

        assertThat(count("select count(*) from users where id = ?", gone)).isZero();
        assertThat(count("select count(*) from social_identities where user_id = ?", gone)).isZero();
        assertThat(count("select count(*) from game_accounts where user_id = ?", gone)).isZero();
        assertThat(count("select count(*) from game_account_stats where game_account_id = ?", accountId)).isZero();
        assertThat(count("select count(*) from blocks where blocker_id = ? or blocked_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from friend_requests where requester_id = ? or receiver_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from friendships where user_low_id = ? or user_high_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from reports where reporter_id = ? or target_user_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from recent_players where user_id = ? or other_user_id = ?", gone, gone)).isZero();
        assertThat(count("select count(*) from party_members where user_id = ?", gone)).isZero();
        assertThat(count("select count(*) from recruit_posts where host_id = ? or id = ?", gone, expired)).isZero();
        assertThat(count("select count(*) from recruit_post_positions where post_id = ?", expired)).isZero();
        // 남는 것 — 남의 파티와 그 방장의 파티원 줄 · 남은 사람들
        assertThat(count("select count(*) from parties where id = ?", partyId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("select user_id from party_members where party_id = ?", Long.class, partyId))
                .containsExactly(other);
        assertThat(count("select count(*) from users where id in (?, ?)", other, third)).isEqualTo(2);
    }

    // ---- 확정된 파티 기록은 남는다 ----

    @Test
    @DisplayName("확정한 방의 방장이 탈퇴하면 평소 나가기처럼 승계되고, 글 · 파티 · 남은 파티원 줄은 남고 작성자 칸만 빈다. "
            + "글 한 줄의 hostId · host 는 null 이고 단건 · 목록 · 차단 거르기 · 입장 · 지우기 · 다시 확정 · 파티 닫힘이 500 없이 돈다")
    void confirmedPostKeepsItsRecordWithoutHost() throws Exception
    {
        String host = newNickname();
        String guest = newNickname();
        String viewer = newNickname();
        String blocker = newNickname();
        Cookie hostCookie = login(host);
        Cookie guestCookie = login(guest);
        Cookie viewerCookie = login(viewer);
        Cookie blockerCookie = login(blocker);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);
        enterRoom(guestCookie, postId, "SUPPORT").andExpect(status().isCreated());
        track(postId, hostId, guestId);
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());
        hostless.add(postId);
        Long partyId = jdbcTemplate.queryForObject("select id from parties where post_id = ?", Long.class, postId);
        // 남은 파티원을 차단한 사람에게는 이 글이 계속 숨겨져야 한다 — 방장이 빈 뒤에도
        block(blockerCookie, guestId);

        try(PushSubscriber pushes = new PushSubscriber(connectionFactory, objectMapper, UnaryOperator.identity()))
        {
            deleteMe(hostCookie).andExpect(status().isNoContent());

            // 확정한 방의 방장이 나가면 승계다(D-23) — 남은 사람이 ROOM_MEMBER_LEFT 를 받는다
            List<PushSubscriber.Received> received = drainPushes(pushes, guestId);
            assertThat(received).extracting(one -> one.envelope().get("type").asString()).containsExactly("ROOM_MEMBER_LEFT");
            assertThat(received.getFirst().envelope().get("payload").get("userId").asString()).isEqualTo(Long.toString(hostId));
        }

        assertThat(redisTemplate.opsForValue().get(hostKey(postId))).isEqualTo(Long.toString(guestId));
        assertThat(memberIds(postId)).containsExactly(Long.toString(guestId));
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject("select host_id from recruit_posts where id = ?", Long.class, postId)).isNull();
        assertThat(jdbcTemplate.queryForObject("select title from recruit_posts where id = ?", String.class, postId)).isEqualTo("같이 하실 분");
        assertThat(jdbcTemplate.queryForObject("select status from parties where id = ?", String.class, partyId)).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForList("select user_id from party_members where party_id = ?", Long.class, partyId))
                .containsExactly(guestId);

        // 단건 · 목록 — 방장 칸이 null 이고 카드는 남은 파티원뿐이다
        for(JsonNode line : List.of(single(guestCookie, postId), find(list(viewerCookie, "LOL"), postId)))
        {
            assertThat(line).isNotNull();
            assertThat(line.get("hostId").isNull()).isTrue();
            assertThat(line.get("host").isNull()).isTrue();
            assertThat(longs(line.get("members"), "userId")).containsExactly(guestId);
            assertThat(line.get("members").get(0).get("host").asBoolean()).isFalse();
            assertThat(line.get("memberCount").asInt()).isEqualTo(1);
            assertThat(line.get("closed").asBoolean()).isFalse();
            assertThat(line.get("status").asString()).isEqualTo("CONFIRMED");
        }
        // 차단 거르기 — 남은 파티원과 본다
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(blockerCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        assertThat(find(list(blockerCookie, "LOL"), postId)).isNull();
        // 입장 · 지우기 · 다시 확정 — 누구의 글도 아니다
        enterRoom(viewerCookie, postId, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(guestCookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_POST_HOST"));
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(guestCookie)).andExpect(status().isOk());

        // 마지막 사람이 나가면 파티가 닫힌다 — 방장이 빈 글이어도 닫힘 · closed 가 그대로 돈다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(guestCookie)).andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select status from parties where id = ?", String.class, partyId)).isEqualTo("CLOSED");
        JsonNode closed = single(guestCookie, postId);
        assertThat(closed.get("closed").asBoolean()).isTrue();
        assertThat(closed.get("host").isNull()).isTrue();
    }

    @Test
    @DisplayName("확정한 방에 혼자 남은 방장이 탈퇴하면 그 나가기가 방을 없애고 파티가 닫힌다 — 글은 CONFIRMED 그대로 작성자 칸만 빈다. 그 사람의 최근 함께한 사람 줄은 남지 않는다")
    void lastMemberLeavingByDeletionClosesTheParty() throws Exception
    {
        String host = newNickname();
        String guest = newNickname();
        Cookie hostCookie = login(host);
        Cookie guestCookie = login(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);
        enterRoom(guestCookie, postId, "SUPPORT").andExpect(status().isCreated());
        track(postId, hostId, guestId);
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());
        hostless.add(postId);
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(guestCookie)).andExpect(status().isNoContent());

        deleteMe(hostCookie).andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(hostKey(postId))).isFalse();
        assertThat(redisTemplate.hasKey(confirmedKey(postId))).isFalse();
        assertThat(jdbcTemplate.queryForObject("select status from parties where post_id = ?", String.class, postId)).isEqualTo("CLOSED");
        assertThat(statusOf(postId)).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject("select host_id from recruit_posts where id = ?", Long.class, postId)).isNull();
        // 닫힐 때 서로 적힌 최근 함께한 사람 — 탈퇴한 사람이 낀 줄은 FK 가 지웠다
        assertThat(count("select count(*) from recent_players where user_id = ? or other_user_id = ?", hostId, hostId)).isZero();
        assertThat(count("select count(*) from recent_players where user_id = ?", guestId)).isZero();
        JsonNode line = single(guestCookie, postId);
        assertThat(line.get("closed").asBoolean()).isTrue();
        assertThat(longs(line.get("members"), "userId")).containsExactly(guestId);
    }

    // ---- 방 안이면 평소 나가기와 같은 규칙으로 ----

    @Test
    @DisplayName("확정 전 방의 방장이 탈퇴하면 방이 닫히고(방에 있던 사람이 ROOM_CLOSED) 글은 지워진다 — 방 키 · 입장 표시 키가 사라지고 게시판 신호가 나간다. 손님은 곧바로 새 글을 쓸 수 있다")
    void recruitingHostClosesTheRoomAndThePostIsGone() throws Exception
    {
        String host = newNickname();
        String guest = newNickname();
        Cookie hostCookie = login(host);
        Cookie guestCookie = login(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);
        enterRoom(guestCookie, postId, "SUPPORT").andExpect(status().isCreated());
        track(postId, hostId, guestId);

        try(BoardSubscriber board = new BoardSubscriber(connectionFactory, objectMapper);
            PushSubscriber pushes = new PushSubscriber(connectionFactory, objectMapper, UnaryOperator.identity()))
        {
            deleteMe(hostCookie).andExpect(status().isNoContent());

            assertThat(board.next()).isNotNull();
            List<PushSubscriber.Received> received = drainPushes(pushes, hostId, guestId);
            assertThat(received).extracting(PushSubscriber.Received::userId).containsExactly(Long.toString(guestId));
            assertThat(received.getFirst().envelope().get("type").asString()).isEqualTo("ROOM_CLOSED");
            assertThat(received.getFirst().envelope().get("payload").get("roomId").asString()).isEqualTo(Long.toString(postId));
        }

        assertThat(count("select count(*) from recruit_posts where id = ?", postId)).isZero();
        assertThat(redisTemplate.hasKey(hostKey(postId))).isFalse();
        assertThat(redisTemplate.hasKey(membersKey(postId))).isFalse();
        assertThat(redisTemplate.hasKey(needsKey(postId))).isFalse();
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + hostId)).isNull();
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + guestId)).isNull();
        createLolPost(guestCookie);
    }

    @Test
    @DisplayName("입장 표시 키가 없어 방을 못 찾아도 남은 모집 중인 글은 글 지우기와 같은 길로 끝난다 — 방장으로서 방을 닫아 ROOM_CLOSED 를 보내고 글을 지운다")
    void leftoverRecruitingPostIsClosedLikeDelete() throws Exception
    {
        String host = newNickname();
        String guest = newNickname();
        Cookie hostCookie = login(host);
        Cookie guestCookie = login(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createLolPost(hostCookie);
        enterRoom(guestCookie, postId, "SUPPORT").andExpect(status().isCreated());
        track(postId, hostId, guestId);
        // 방장의 입장 표시 키만 먼저 사라진 모양 — 탈퇴의 ② 가 방을 찾지 못한다
        redisTemplate.delete("qm:user:active-room:" + hostId);

        try(PushSubscriber pushes = new PushSubscriber(connectionFactory, objectMapper, UnaryOperator.identity()))
        {
            deleteMe(hostCookie).andExpect(status().isNoContent());

            List<PushSubscriber.Received> received = drainPushes(pushes, guestId);
            assertThat(received).extracting(one -> one.envelope().get("type").asString()).containsExactly("ROOM_CLOSED");
        }

        assertThat(count("select count(*) from recruit_posts where id = ?", postId)).isZero();
        assertThat(redisTemplate.hasKey(hostKey(postId))).isFalse();
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + guestId)).isNull();
    }

    @Test
    @DisplayName("방 안의 일반 멤버가 탈퇴하면 평소 나가기처럼 빠진다 — 방장이 ROOM_MEMBER_LEFT 를 받고, 고른 포지션이 남은 찾는 포지션에 돌아가고, 글은 그대로다")
    void memberLeavesLikeANormalLeave() throws Exception
    {
        String host = newNickname();
        String guest = newNickname();
        Cookie hostCookie = login(host);
        Cookie guestCookie = login(guest);
        Long hostId = userIdOf(host);
        Long guestId = userIdOf(guest);
        Long postId = createFivePersonLolPost(hostCookie);
        enterRoom(guestCookie, postId, "MID").andExpect(status().isCreated());
        track(postId, hostId, guestId);

        try(PushSubscriber pushes = new PushSubscriber(connectionFactory, objectMapper, UnaryOperator.identity()))
        {
            deleteMe(guestCookie).andExpect(status().isNoContent());

            List<PushSubscriber.Received> received = drainPushes(pushes, hostId);
            assertThat(received).extracting(one -> one.envelope().get("type").asString()).containsExactly("ROOM_MEMBER_LEFT");
            assertThat(received.getFirst().envelope().get("payload").get("userId").asString()).isEqualTo(Long.toString(guestId));
        }

        assertThat(memberIds(postId)).containsExactly(Long.toString(hostId));
        assertThat(redisTemplate.opsForSet().members(needsKey(postId))).contains("MID");
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + guestId)).isNull();
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");
        assertThat(count("select count(*) from users where id = ?", guestId)).isZero();
    }

    // ---- 거절 ----

    @Test
    @DisplayName("매칭 대기 중(활성 요청 키가 있다)이면 409 ALREADY_QUEUED 이고 아무것도 지우지 않는다 — matching 의 키는 EXISTS 로 보기만 한다")
    void queuedIsRejected() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        Long userId = userIdOf(nickname);
        insertGameAccount(userId, "LOL", "대기#KR1", null);
        String activeRequest = "qm:user:active-request:" + userId;
        redisTemplate.opsForValue().set(activeRequest, "{\"status\":\"WAITING\"}", Duration.ofSeconds(60));
        try
        {
            MvcResult result = deleteMe(cookie)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_QUEUED"))
                    .andReturn();

            assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
            assertThat(count("select count(*) from users where id = ?", userId)).isEqualTo(1);
            assertThat(count("select count(*) from game_accounts where user_id = ?", userId)).isEqualTo(1);
            assertThat(redisTemplate.opsForValue().get(activeRequest)).isEqualTo("{\"status\":\"WAITING\"}");
        }
        finally
        {
            redisTemplate.delete(activeRequest);
        }
        // 매칭을 그만두면 탈퇴할 수 있다
        deleteMe(cookie).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Redis 에 닿지 못하면 503 ROOM_STATE_UNAVAILABLE 이고 아무것도 지우지 않는다 — 매칭 중인지 · 어느 방에 있는지 모르는 채 지우지 않는다")
    void redisDownIsServiceUnavailable()
    {
        Long userId = insertUser();
        insertGameAccount(userId, "LOL", "장애#KR1", null);
        // 아무도 듣지 않는 포트다. 앱 전체의 Redis 를 죽일 수 없어 방의 서비스만 죽은 Redis 로 만들어 끼운다(RoomApiTest 와 같다)
        LettuceConnectionFactory dead = new LettuceConnectionFactory("localhost", 1);
        dead.afterPropertiesSet();
        try
        {
            RoomService broken = new RoomService(new StringRedisTemplate(dead), createRoomScript, roomProperties,
                    confirmRoomScript, roomNotifier, leaveRoomScript, enterMatchRoomScript);
            AccountDeletionService service = new AccountDeletionService(userRepository, broken, roomMemberService, postService,
                    transactionTemplate);

            assertThatThrownBy(() -> service.delete(userId))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(e.getCode()).isEqualTo("ROOM_STATE_UNAVAILABLE");
                    });
            assertThat(count("select count(*) from users where id = ?", userId)).isEqualTo(1);
            assertThat(count("select count(*) from game_accounts where user_id = ?", userId)).isEqualTo(1);
        }
        finally
        {
            dead.destroy();
        }
    }

    // ---- 탈퇴 뒤에 남는 토큰 ----

    @Test
    @DisplayName("refresh 의 Redis 줄은 탈퇴가 지우지 못한다(쿠키가 이 요청에 실려 오지 않는다) — 그 값으로 재발급을 부르면 401 INVALID_REFRESH_TOKEN 이고 그때 줄이 지워진다")
    void leftoverRefreshIsRejected() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Cookie refresh = refreshCookieFor(nickname);

        deleteMe(access).andExpect(status().isNoContent());
        assertThat(refreshTokenStored(refresh.getValue())).isTrue();

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        assertThat(refreshTokenStored(refresh.getValue())).isFalse();
    }

    @Test
    @DisplayName("탈퇴 뒤 남은 access 토큰(최대 15분)으로 오는 요청은 500 이 아니라 401 UNAUTHENTICATED 다 — 내 정보 · 친구 요청 · 글 쓰기(방도 안 생긴다) · 다시 탈퇴")
    void leftoverAccessIsUnauthenticated() throws Exception
    {
        String nickname = newNickname();
        Cookie access = login(nickname);
        Long gone = userIdOf(nickname);
        Long other = insertUser();

        deleteMe(access).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/friend-requests").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content(json("userId", other)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/posts").cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content(lolPostBody("유령의 글")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertThat(count("select count(*) from recruit_posts where host_id = ?", gone)).isZero();
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + gone)).isNull();
        deleteMe(access).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- 도우미 ----

    private ResultActions deleteMe(Cookie cookie) throws Exception
    {
        return mockMvc.perform(delete("/api/v1/users/me").cookie(cookie));
    }

    private JsonNode single(Cookie cookie, Long postId) throws Exception
    {
        return body(mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isOk()));
    }

    /** SQL 로 글 한 줄 — 방은 열지 않는다(확정 · 만료된 글이라 방이 없다) */
    private Long insertPostRow(Long hostId, String status)
    {
        return jdbcTemplate.queryForObject("insert into recruit_posts "
                        + "(host_id, game, mode, title, voice, status, created_at, updated_at, confirmed_at, expired_at) "
                        + "values (?, 'LOL', ?, 't', 'REQUIRED', ?, now(), now(), "
                        + ("CONFIRMED".equals(status) ? "now()" : "null") + ", " + ("EXPIRED".equals(status) ? "now()" : "null") + ") returning id",
                Long.class, hostId, LOL_MODE, status);
    }

    private int count(String sql, Object... args)
    {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    /** 이 사람들에게 온 알림을 더 오지 않을 때까지 모은다. 같은 Redis 를 다른 것이 쓸 수 있어 이 사람들 것만 남긴다 */
    private static List<PushSubscriber.Received> drainPushes(PushSubscriber pushes, Long... userIds) throws InterruptedException
    {
        List<String> wanted = java.util.Arrays.stream(userIds).map(String::valueOf).toList();
        List<PushSubscriber.Received> received = new ArrayList<>();
        PushSubscriber.Received one;
        while((one = pushes.next()) != null)
        {
            if(wanted.contains(one.userId()))
            {
                received.add(one);
            }
        }
        return received;
    }
}
