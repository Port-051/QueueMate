package com.queuemate.platform.social;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 친구 요청과 친구 — {@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람" 의 일곱 요청.
 * 알림은 {@link FriendPushTest}, 테이블의 제약은 {@link SocialMigrationTest} 가 본다.
 *
 * <p>주고받는 것은 전부 <b>사용자 번호</b>(숫자)다 — 로그인 아이디는 가입 · 로그인에만 쓴다(2026-09-22 소유자 결정).
 * 응답의 {@code userId} · {@code requestId} 는 JSON 숫자라 {@code jsonPath(…, equalTo(번호), Long.class)} 로 본다 —
 * Jackson 이 {@code int} 로 읽어 {@code value(long)} 은 맞지 않는다.
 */
class FriendApiTest extends FriendTestSupport {

    @Test
    @DisplayName("요청 → 받은 목록 · 보낸 목록 → 수락 → 양쪽의 친구 목록 → 끊기(두 번 다 204)")
    void requestAcceptListUnfriend() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);

        String body = sendRequest(aliceCookie, bobId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber())
                .andExpect(jsonPath("$.requester.userId", equalTo(aliceId), Long.class))
                .andExpect(jsonPath("$.requester.nickname").value(nicknameOf(alice)))
                .andExpect(jsonPath("$.receiver.userId", equalTo(bobId), Long.class))
                .andExpect(jsonPath("$.receiver.nickname").value(nicknameOf(bob)))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn().getResponse().getContentAsString();
        long requestId = objectMapper.readTree(body).get("requestId").asLong();

        // 받은 사람 — direction 을 안 주면 RECEIVED 다. 보낸 목록에는 없다
        mockMvc.perform(get("/api/v1/friend-requests").cookie(bobCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].requestId", equalTo(requestId), Long.class))
                .andExpect(jsonPath("$.requests[0].requester.userId", equalTo(aliceId), Long.class))
                .andExpect(jsonPath("$.requests[0].requester.nickname").value(nicknameOf(alice)))
                .andExpect(jsonPath("$.requests[0].receiver.userId", equalTo(bobId), Long.class))
                .andExpect(jsonPath("$.requests[0].createdAt").isString());
        mockMvc.perform(get("/api/v1/friend-requests").param("direction", "SENT").cookie(bobCookie))
                .andExpect(jsonPath("$.requests").isEmpty());
        // 보낸 사람
        mockMvc.perform(get("/api/v1/friend-requests").param("direction", "SENT").cookie(aliceCookie))
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].requestId", equalTo(requestId), Long.class));
        mockMvc.perform(get("/api/v1/friend-requests").param("direction", "RECEIVED").cookie(aliceCookie))
                .andExpect(jsonPath("$.requests").isArray())
                .andExpect(jsonPath("$.requests").isEmpty());
        // 아직 친구가 아니다
        mockMvc.perform(get("/api/v1/friends").cookie(aliceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friends").isArray())
                .andExpect(jsonPath("$.friends").isEmpty());

        accept(bobCookie, requestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", equalTo(aliceId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nicknameOf(alice)))
                .andExpect(jsonPath("$.since").isString());

        // 대기 중인 것만 보인다 — 수락된 요청은 어느 목록에도 없다
        mockMvc.perform(get("/api/v1/friend-requests").cookie(bobCookie)).andExpect(jsonPath("$.requests").isEmpty());
        mockMvc.perform(get("/api/v1/friend-requests").param("direction", "SENT").cookie(aliceCookie))
                .andExpect(jsonPath("$.requests").isEmpty());
        mockMvc.perform(get("/api/v1/friends").cookie(aliceCookie))
                .andExpect(jsonPath("$.friends.length()").value(1))
                .andExpect(jsonPath("$.friends[0].userId", equalTo(bobId), Long.class))
                .andExpect(jsonPath("$.friends[0].nickname").value(nicknameOf(bob)))
                .andExpect(jsonPath("$.friends[0].since").isString());
        mockMvc.perform(get("/api/v1/friends").cookie(bobCookie))
                .andExpect(jsonPath("$.friends.length()").value(1))
                .andExpect(jsonPath("$.friends[0].userId", equalTo(aliceId), Long.class));
        assertThat(statusOf(requestId)).isEqualTo("ACCEPTED");
        assertThat(friendshipsBetween(aliceId, bobId)).isEqualTo(1);

        // 끊는 것은 어느 쪽이든 할 수 있고 멱등이다 — 친구가 아니어도, 없는 사용자여도, 자기 자신이어도 204 다
        unfriend(bobCookie, aliceId).andExpect(status().isNoContent());
        unfriend(bobCookie, aliceId).andExpect(status().isNoContent());
        unfriend(bobCookie, unknownUserId()).andExpect(status().isNoContent());
        unfriend(bobCookie, bobId).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/friends").cookie(aliceCookie)).andExpect(jsonPath("$.friends").isEmpty());
        mockMvc.perform(get("/api/v1/friends").cookie(bobCookie)).andExpect(jsonPath("$.friends").isEmpty());
        assertThat(friendshipsBetween(aliceId, bobId)).isZero();

        // 끊은 뒤에는 다시 요청할 수 있다
        sendRequest(bobCookie, aliceId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("친구 목록은 닉네임순이고 내 친구만 있다. 받은 목록은 새것이 먼저다")
    void listOrders() throws Exception
    {
        String me = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        Long myId = userIdOf(me);
        // 닉네임은 로그인 아이디에서 짓는다 — 아이디순으로 가입시키지 않고 요청 순서도 섞는다
        List<String> others = List.of(newLoginId(), newLoginId(), newLoginId());
        List<Long> requestIds = new ArrayList<>();
        List<Cookie> cookies = new ArrayList<>();
        for(String other : others)
        {
            cookies.add(signupAndLogin(other));
            requestIds.add(sendRequestOk(cookies.get(cookies.size() - 1), myId));
            Thread.sleep(5);
        }
        // 남들끼리의 친구는 내 목록에 나오지 않는다
        String stranger = newLoginId();
        Cookie strangerCookie = signupAndLogin(stranger);
        accept(strangerCookie, sendRequestOk(cookies.get(0), userIdOf(stranger))).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/friend-requests").cookie(myCookie))
                .andExpect(jsonPath("$.requests.length()").value(3))
                .andExpect(jsonPath("$.requests[0].requestId", equalTo(requestIds.get(2)), Long.class))
                .andExpect(jsonPath("$.requests[1].requestId", equalTo(requestIds.get(1)), Long.class))
                .andExpect(jsonPath("$.requests[2].requestId", equalTo(requestIds.get(0)), Long.class));

        for(Long requestId : requestIds)
        {
            accept(myCookie, requestId).andExpect(status().isOk());
        }
        List<String> byNickname = others.stream().sorted((a, b) -> nicknameOf(a).compareToIgnoreCase(nicknameOf(b))).toList();
        mockMvc.perform(get("/api/v1/friends").cookie(myCookie))
                .andExpect(jsonPath("$.friends.length()").value(3))
                .andExpect(jsonPath("$.friends[0].userId", equalTo(userIdOf(byNickname.get(0))), Long.class))
                .andExpect(jsonPath("$.friends[1].userId", equalTo(userIdOf(byNickname.get(1))), Long.class))
                .andExpect(jsonPath("$.friends[2].userId", equalTo(userIdOf(byNickname.get(2))), Long.class));
    }

    @Test
    @DisplayName("거절 · 거두기는 204 이고 그 뒤에 다시 요청할 수 있다. 처리된 요청에 또 응답하면 404 다")
    void declineAndCancel() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);

        long first = sendRequestOk(aliceCookie, bobId);
        decline(bobCookie, first).andExpect(status().isNoContent());
        assertThat(statusOf(first)).isEqualTo("DECLINED");
        assertThat(jdbcTemplate.queryForObject(
                "select responded_at is not null from friend_requests where id = ?", Boolean.class, first)).isTrue();
        mockMvc.perform(get("/api/v1/friend-requests").cookie(bobCookie)).andExpect(jsonPath("$.requests").isEmpty());
        // 이미 처리됐다 — 거절한 것을 수락으로 뒤집을 수 없다
        decline(bobCookie, first).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));
        accept(bobCookie, first).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));
        cancel(aliceCookie, first).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));
        assertThat(friendshipsBetween(aliceId, bobId)).isZero();

        long second = sendRequestOk(aliceCookie, bobId);
        assertThat(second).isNotEqualTo(first);
        cancel(aliceCookie, second).andExpect(status().isNoContent());
        assertThat(statusOf(second)).isEqualTo("CANCELED");
        mockMvc.perform(get("/api/v1/friend-requests").cookie(bobCookie)).andExpect(jsonPath("$.requests").isEmpty());
        accept(bobCookie, second).andExpect(status().isNotFound());

        long third = sendRequestOk(aliceCookie, bobId);
        accept(bobCookie, third).andExpect(status().isOk());
        assertThat(friendshipsBetween(aliceId, bobId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 요청을 또 보내면 409 FRIEND_REQUEST_ALREADY_SENT, 상대가 이미 보냈으면 409 FRIEND_REQUEST_ALREADY_RECEIVED, 이미 친구면 409 ALREADY_FRIENDS")
    void conflicts() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);

        long requestId = sendRequestOk(aliceCookie, bobId);
        sendRequest(aliceCookie, bobId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_SENT"))
                .andExpect(jsonPath("$.details").isArray());
        sendRequest(bobCookie, aliceId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_ALREADY_RECEIVED"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from friend_requests "
                + "where requester_id in (?, ?)", Integer.class, aliceId, bobId)).isEqualTo(1);

        accept(bobCookie, requestId).andExpect(status().isOk());
        sendRequest(aliceCookie, bobId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
        sendRequest(bobCookie, aliceId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_FRIENDS"));
    }

    @Test
    @DisplayName("같은 요청을 여러 스레드가 동시에 보내면 하나만 201 이고 나머지는 409 다 — partial unique index 가 지킨다")
    void concurrentRequests() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        signup(bob, PASSWORD, nicknameOf(bob)).andExpect(status().isCreated());
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);

        List<Integer> statuses = concurrently(8, () -> sendRequest(aliceCookie, bobId).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(7);
        assertThat(jdbcTemplate.queryForObject("select count(*) from friend_requests "
                + "where requester_id = ? and receiver_id = ?", Integer.class, aliceId, bobId)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 요청을 여러 스레드가 동시에 수락하면 200 은 하나이고 나머지는 404 다 — 친구는 한 줄이다")
    void concurrentAccepts() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        long requestId = sendRequestOk(aliceCookie, bobId);

        List<Integer> statuses = concurrently(8, () -> accept(bobCookie, requestId).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 404).hasSize(7);
        assertThat(friendshipsBetween(aliceId, bobId)).isEqualTo(1);
        assertThat(statusOf(requestId)).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("양방향 PENDING 이 둘 있어도(서로 동시에 보냈다) 한쪽을 수락하면 둘 다 닫히고 친구는 한 줄이다")
    void acceptClosesOppositePending() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        // 앱의 조회(FRIEND_REQUEST_ALREADY_RECEIVED)를 비껴간 경쟁의 결과를 직접 만든다 — DB 는 이것을 막지 않는다(방향이 다른 줄이다)
        long aliceToBob = insertPending(aliceId, bobId);
        long bobToAlice = insertPending(bobId, aliceId);

        accept(bobCookie, aliceToBob).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", equalTo(aliceId), Long.class));

        assertThat(statusOf(aliceToBob)).isEqualTo("ACCEPTED");
        assertThat(statusOf(bobToAlice)).isEqualTo("ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                "select responded_at is not null from friend_requests where id = ?", Boolean.class, bobToAlice)).isTrue();
        assertThat(friendshipsBetween(aliceId, bobId)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/friend-requests").cookie(aliceCookie)).andExpect(jsonPath("$.requests").isEmpty());
        // 같이 닫힌 요청은 더 수락할 수 없다
        accept(aliceCookie, bobToAlice).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미 친구인 사람의 대기 중 요청이 남아 있어도(조회를 비껴갔다) 수락은 200 이고 친구는 한 줄 그대로다 — since 는 처음 친구가 된 시각이다")
    void acceptWhenAlreadyFriends() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        accept(bobCookie, sendRequestOk(aliceCookie, bobId)).andExpect(status().isOk());
        String since = objectMapper.readTree(mockMvc.perform(get("/api/v1/friends").cookie(bobCookie))
                .andReturn().getResponse().getContentAsString()).get("friends").get(0).get("since").asString();
        long leftover = insertPending(aliceId, bobId);

        accept(bobCookie, leftover).andExpect(status().isOk()).andExpect(jsonPath("$.since").value(since));

        assertThat(friendshipsBetween(aliceId, bobId)).isEqualTo(1);
    }

    @Test
    @DisplayName("자기 자신은 400 CANNOT_FRIEND_SELF, 빈 본문은 400, 모르는 direction · 소문자 direction 은 400, 숫자가 아닌 requestId 는 400 이다")
    void invalidRequests() throws Exception
    {
        String me = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        Long myId = userIdOf(me);

        sendRequest(myCookie, myId).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_FRIEND_SELF"));
        mockMvc.perform(post("/api/v1/friend-requests").cookie(myCookie).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("userId"));
        mockMvc.perform(get("/api/v1/friend-requests").param("direction", "all").cookie(myCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("direction"));
        // 소문자도 400 이다 — 대문자 그대로만 받는다(게시판 목록의 game 과 같다)
        mockMvc.perform(get("/api/v1/friend-requests").param("direction", "sent").cookie(myCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("direction"));
        mockMvc.perform(post("/api/v1/friend-requests/abc/accept").cookie(myCookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from friend_requests where requester_id = ?", Integer.class, myId)).isZero();
    }

    @Test
    @DisplayName("없는 사용자와 차단 관계(어느 방향이든)는 글자까지 같은 404 USER_NOT_FOUND 다 — 차단당한 사실이 새지 않는다")
    void notFoundAndBlockedLookTheSame() throws Exception
    {
        String me = newLoginId();
        String blockedByMe = newLoginId();
        String blocksMe = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        signup(blockedByMe, PASSWORD, nicknameOf(blockedByMe)).andExpect(status().isCreated());
        Cookie theirCookie = signupAndLogin(blocksMe);
        Long myId = userIdOf(me);
        Long blockedByMeId = userIdOf(blockedByMe);
        Long blocksMeId = userIdOf(blocksMe);
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())",
                myId, blockedByMeId);
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())",
                blocksMeId, myId);

        String missing = sendRequest(myCookie, unknownUserId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        // 숫자가 아닌 번호도 같다 — 있을 수 없는 사용자라 400 이 아니라 같은 404 다
        String malformed = sendRequestRaw(myCookie, "NOT A VALID ID").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String iBlocked = sendRequest(myCookie, blockedByMeId).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String theyBlocked = sendRequest(myCookie, blocksMeId).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        // 차단한 쪽이 보내도 같다
        String fromBlocker = sendRequest(theirCookie, myId).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(List.of(malformed, iBlocked, theyBlocked, fromBlocker)).containsOnly(missing);
        assertThat(jdbcTemplate.queryForObject("select count(*) from friend_requests "
                + "where requester_id in (?, ?)", Integer.class, myId, blocksMeId)).isZero();
    }

    @Test
    @DisplayName("남의 요청은 수락 · 거절 · 거두기가 전부 404 다 — 보낸 사람은 수락 · 거절을, 받은 사람은 거두기를 할 수 없다. 없는 요청도 같은 404 다")
    void othersRequests() throws Exception
    {
        String alice = newLoginId();
        String bob = newLoginId();
        Cookie aliceCookie = signupAndLogin(alice);
        Cookie bobCookie = signupAndLogin(bob);
        Cookie strangerCookie = signupAndLogin(newLoginId());
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        long requestId = sendRequestOk(aliceCookie, bobId);

        String missing = accept(bobCookie, Long.MAX_VALUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        // 남의 요청이 "있다"는 것도 알려 주지 않는다 — 없는 요청과 같은 본문이다
        assertThat(accept(strangerCookie, requestId).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(missing);
        decline(strangerCookie, requestId).andExpect(status().isNotFound());
        cancel(strangerCookie, requestId).andExpect(status().isNotFound());
        // 보낸 사람이 자기 요청을 수락 · 거절할 수 없다
        accept(aliceCookie, requestId).andExpect(status().isNotFound());
        decline(aliceCookie, requestId).andExpect(status().isNotFound());
        // 받은 사람이 거둘 수 없다
        cancel(bobCookie, requestId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FRIEND_REQUEST_NOT_FOUND"));

        assertThat(statusOf(requestId)).isEqualTo("PENDING");
        assertThat(friendshipsBetween(aliceId, bobId)).isZero();
    }

    @Test
    @DisplayName("사람을 찾는 길이 없다 — 검색처럼 보이는 경로는 전부 없는 경로다")
    void noUserSearch() throws Exception
    {
        Cookie myCookie = signupAndLogin(newLoginId());

        for(String path : List.of("/api/v1/users", "/api/v1/users/search", "/api/v1/friends/search/n", "/api/v1/friend-requests/search"))
        {
            mockMvc.perform(get(path).param("nickname", "n").param("q", "n").cookie(myCookie))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.friends").doesNotExist())
                    .andExpect(jsonPath("$.users").doesNotExist());
        }
        // 친구 목록은 파라미터를 주어도 내 친구만 돌려준다 — 거르거나 찾는 파라미터가 없다
        mockMvc.perform(get("/api/v1/friends").param("nickname", "n").param("q", "n").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friends").isEmpty());
    }

    @Test
    @DisplayName("친구 요청 · 친구는 로그인해야 하고, POST · DELETE 는 Origin 검사를 거친다")
    void requiresLoginAndOrigin() throws Exception
    {
        Cookie myCookie = signupAndLogin(newLoginId());

        mockMvc.perform(get("/api/v1/friends")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/friend-requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/friend-requests").contentType(MediaType.APPLICATION_JSON).content(json("userId", 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/friend-requests").cookie(myCookie).header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(json("userId", 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    /** 같은 일을 여러 스레드에서 한꺼번에 시키고 상태 코드를 모은다 */
    private static List<Integer> concurrently(int threads, Callable<Integer> call) throws Exception
    {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try
        {
            for(int i = 0; i < threads; i++)
            {
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return call.call();
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
            return statuses;
        }
        finally
        {
            pool.shutdownNow();
        }
    }
}
