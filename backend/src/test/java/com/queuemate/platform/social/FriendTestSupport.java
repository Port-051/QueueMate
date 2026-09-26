package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 친구 테스트가 같이 쓰는 요청들. 설정을 바꾸지 않으므로 {@link ApiTestSupport} 의 스프링 컨텍스트를 그대로 같이 쓴다 */
abstract class FriendTestSupport extends ApiTestSupport {

    /** {@code userId} 는 상대의 사용자 번호다. JSON 숫자로 보낸다 */
    protected ResultActions sendRequest(Cookie cookie, Long targetUserId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/friend-requests").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json("userId", targetUserId)));
    }

    /** 본문의 {@code userId} 를 글자 그대로 보낸다 — 숫자가 아닌 값을 보내 볼 때 쓴다 */
    protected ResultActions sendRequestRaw(Cookie cookie, String targetUserId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/friend-requests").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json("userId", targetUserId)));
    }

    /** 요청을 보내고(201) 그 {@code requestId} 를 돌려준다 */
    protected long sendRequestOk(Cookie cookie, Long targetUserId) throws Exception
    {
        String body = sendRequest(cookie, targetUserId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("requestId").asLong();
    }

    protected ResultActions accept(Cookie cookie, long requestId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/friend-requests/" + requestId + "/accept").cookie(cookie));
    }

    protected ResultActions decline(Cookie cookie, long requestId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/friend-requests/" + requestId + "/decline").cookie(cookie));
    }

    protected ResultActions cancel(Cookie cookie, long requestId) throws Exception
    {
        return mockMvc.perform(delete("/api/v1/friend-requests/" + requestId).cookie(cookie));
    }

    protected ResultActions unfriend(Cookie cookie, Long otherUserId) throws Exception
    {
        return mockMvc.perform(delete("/api/v1/friends/" + otherUserId).cookie(cookie));
    }

    protected String statusOf(long requestId)
    {
        return jdbcTemplate.queryForObject("select status from friend_requests where id = ?", String.class, requestId);
    }

    /** 두 사람 사이의 친구 줄 수 — 순서를 가리지 않는다. 늘 0 아니면 1 이어야 한다 */
    protected int friendshipsBetween(Long a, Long b)
    {
        return jdbcTemplate.queryForObject("select count(*) from friendships "
                + "where (user_low_id = ? and user_high_id = ?) or (user_low_id = ? and user_high_id = ?)",
                Integer.class, a, b, b, a);
    }

    /** 앱을 거치지 않고 대기 중 요청을 넣는다 — 조회(친절한 에러)를 비껴간 경쟁의 결과를 만들 때 쓴다 */
    protected long insertPending(Long requesterId, Long receiverId)
    {
        return jdbcTemplate.queryForObject("insert into friend_requests (requester_id, receiver_id, status, created_at) "
                + "values (?, ?, 'PENDING', now()) returning id", Long.class, requesterId, receiverId);
    }
}
