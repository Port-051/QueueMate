package com.queuemate.platform.social;

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개인 알림 — <b>구독해서 확인한다</b>(CLAUDE.md §3.2). 발행은 실패해도 조용하고 구독자 0명도 실패가 아니라서, 채널 접두사나 봉투가 틀려도
 * 다른 테스트는 전부 통과한다. {@code notification} 이 하는 것처럼 {@code qm:pubsub:push:{userId}} 를 구독해 도착한 글자를 본다.
 * <b>채널의 {@code userId} 는 사용자 번호다</b>(2026-09-22 소유자 결정 — 로그인 아이디가 아니다).
 *
 * <p>채널 접두사를 main 의 상수에서 가져오지 않고 <b>글자로 적었다</b> — 상수에 오타가 나면 이 테스트가 깨져야 한다.
 * 그 글자의 원본은 {@code matching} 의 {@code SharedKeys.PUSH_CHANNEL_PREFIX} 다.
 *
 * <p>사용자를 매번 새로 만드므로 이 채널들에는 이 테스트의 알림만 온다 — 그래서 개수를 정확히 센다.
 */
class FriendPushTest extends FriendTestSupport {

    private static final String PUSH_CHANNEL_PREFIX = "qm:pubsub:push:";

    @Autowired
    RedisConnectionFactory connectionFactory;

    private RedisMessageListenerContainer container;
    private final Map<Long, BlockingQueue<String>> received = new ConcurrentHashMap<>();

    @AfterEach
    void unsubscribe() throws Exception
    {
        if(container != null)
        {
            container.stop();
            container.destroy();
        }
    }

    @Test
    @DisplayName("요청을 보내면 받는 사람의 채널에 FRIEND_REQUEST_RECEIVED 가 온다 — 봉투는 네 칸이고 payload 는 {requestId, fromUserId} 뿐이다")
    void requestReceived() throws Exception
    {
        String alice = newNickname();
        String bob = newNickname();
        Cookie aliceCookie = login(alice);
        login(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        subscribe(aliceId, bobId);

        long requestId = sendRequestOk(aliceCookie, bobId);

        String message = received.get(bobId).poll(10, TimeUnit.SECONDS);
        assertThat(message).as("알림이 오지 않았다").isNotNull();
        JsonNode envelope = assertEnvelope(message, "FRIEND_REQUEST_RECEIVED");
        JsonNode payload = envelope.get("payload");
        assertThat(new ArrayList<>(payload.propertyNames())).containsExactly("requestId", "fromUserId");
        assertThat(payload.get("requestId").isNumber()).isTrue();
        assertThat(payload.get("requestId").asLong()).isEqualTo(requestId);
        // 사용자 번호도 숫자로 싣는다 — 따옴표로 감싼 문자열이 아니다
        assertThat(payload.get("fromUserId").isNumber()).isTrue();
        assertThat(payload.get("fromUserId").asLong()).isEqualTo(aliceId);
        // 데이터를 싣지 않는다 — "다시 조회하라"는 신호다
        assertThat(message).doesNotContain("nickname").doesNotContain(alice);
        // 보낸 사람에게는 아무것도 가지 않는다. 받는 사람에게도 한 번뿐이다
        assertThat(drain(aliceId)).isEmpty();
        assertThat(drain(bobId)).isEmpty();
    }

    @Test
    @DisplayName("수락하면 보냈던 사람의 채널에 FRIEND_REQUEST_ACCEPTED 가 온다 — payload 는 {requestId, userId(수락한 사람)} 다")
    void requestAccepted() throws Exception
    {
        String alice = newNickname();
        String bob = newNickname();
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        long requestId = sendRequestOk(aliceCookie, bobId);
        subscribe(aliceId, bobId);

        accept(bobCookie, requestId).andExpect(status().isOk());

        String message = received.get(aliceId).poll(10, TimeUnit.SECONDS);
        assertThat(message).as("알림이 오지 않았다").isNotNull();
        JsonNode payload = assertEnvelope(message, "FRIEND_REQUEST_ACCEPTED").get("payload");
        assertThat(new ArrayList<>(payload.propertyNames())).containsExactly("requestId", "userId");
        assertThat(payload.get("requestId").asLong()).isEqualTo(requestId);
        assertThat(payload.get("userId").isNumber()).isTrue();
        assertThat(payload.get("userId").asLong()).isEqualTo(bobId);
        assertThat(message).doesNotContain("nickname");
        // 수락한 사람에게는 가지 않는다. 이미 처리된 요청을 또 수락해도(404) 더 나가지 않는다
        accept(bobCookie, requestId).andExpect(status().isNotFound());
        assertThat(drain(bobId)).isEmpty();
        assertThat(drain(aliceId)).isEmpty();
    }

    @Test
    @DisplayName("거절 · 거두기 · 친구 끊기 · 차단에는 아무것도 나가지 않는다. 롤백된 요청(409 · 404 · 400)에도 나가지 않는다")
    void silentOnes() throws Exception
    {
        String alice = newNickname();
        String bob = newNickname();
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);
        Long aliceId = userIdOf(alice);
        Long bobId = userIdOf(bob);
        subscribe(aliceId, bobId);

        // 거절
        long first = sendRequestOk(aliceCookie, bobId);
        assertThat(drain(bobId)).hasSize(1);
        decline(bobCookie, first).andExpect(status().isNoContent());
        assertThat(drain(aliceId)).isEmpty();
        assertThat(drain(bobId)).isEmpty();

        // 거두기
        long second = sendRequestOk(aliceCookie, bobId);
        assertThat(drain(bobId)).hasSize(1);
        cancel(aliceCookie, second).andExpect(status().isNoContent());
        assertThat(drain(aliceId)).isEmpty();
        assertThat(drain(bobId)).isEmpty();

        // 롤백된 요청 — 중복(409) · 반대 방향 대기 중(409) · 자기 자신(400) · 없는 사용자(404)
        sendRequestOk(aliceCookie, bobId);
        assertThat(drain(bobId)).hasSize(1);
        sendRequest(aliceCookie, bobId).andExpect(status().isConflict());
        sendRequest(bobCookie, aliceId).andExpect(status().isConflict());
        sendRequest(bobCookie, bobId).andExpect(status().isBadRequest());
        sendRequest(aliceCookie, unknownUserId()).andExpect(status().isNotFound());
        assertThat(drain(aliceId)).isEmpty();
        assertThat(drain(bobId)).isEmpty();

        // 친구가 된 뒤 끊기 · 차단
        long third = jdbcTemplate.queryForObject("select id from friend_requests "
                + "where requester_id = ? and receiver_id = ? and status = 'PENDING'", Long.class, aliceId, bobId);
        accept(bobCookie, third).andExpect(status().isOk());
        assertThat(drain(aliceId)).hasSize(1);
        sendRequest(aliceCookie, bobId).andExpect(status().isConflict());
        unfriend(aliceCookie, bobId).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/blocks").cookie(aliceCookie)
                        .contentType(MediaType.APPLICATION_JSON).content(json("userId", bobId)))
                .andExpect(status().isCreated());
        sendRequest(bobCookie, aliceId).andExpect(status().isNotFound());
        assertThat(drain(aliceId)).isEmpty();
        assertThat(drain(bobId)).isEmpty();
    }

    /** 봉투 네 칸을 확인하고 돌려준다 — {@code matching} · {@code room} 의 봉투와 같은 모양이다 */
    private JsonNode assertEnvelope(String message, String type)
    {
        JsonNode envelope = objectMapper.readTree(message);
        assertThat(new ArrayList<>(envelope.propertyNames())).containsExactly("type", "eventId", "occurredAt", "payload");
        assertThat(envelope.get("type").asString()).isEqualTo(type);
        assertThat(UUID.fromString(envelope.get("eventId").asString()).toString()).isEqualTo(envelope.get("eventId").asString());
        // ISO-8601 UTC, 밀리초까지(Instant 는 밀리초가 0 이면 소수부를 아예 쓰지 않는다 — room · matching 과 같다)
        assertThat(envelope.get("occurredAt").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z$");
        assertThat(envelope.get("payload").isObject()).isTrue();
        return envelope;
    }

    /** 그 사용자 번호들의 채널을 구독하고, <b>구독이 Redis 에 걸린 것을 본 뒤에</b> 돌아온다 — 그 전에 발행된 알림은 오지 않는다 */
    private void subscribe(Long... userIds) throws Exception
    {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        for(Long userId : userIds)
        {
            BlockingQueue<String> queue = new LinkedBlockingQueue<>();
            received.put(userId, queue);
            container.addMessageListener((message, pattern) -> queue.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                    new ChannelTopic(PUSH_CHANNEL_PREFIX + userId));
        }
        container.afterPropertiesSet();
        container.start();
        long deadline = System.currentTimeMillis() + 15_000;
        for(Long userId : userIds)
        {
            // 방금 만든 사용자라 다른 구독자가 없다 — 1 이 되면 이 테스트의 구독이다
            while(subscribers(PUSH_CHANNEL_PREFIX + userId) < 1)
            {
                assertThat(System.currentTimeMillis()).as("구독이 걸리지 않았다").isLessThan(deadline);
                Thread.sleep(50);
            }
        }
    }

    /** 잠깐 기다렸다가 그동안 온 알림을 돌려주고 비운다. 발행은 커밋 직후라 요청이 끝났을 때 이미 나가 있다 — 도착만 기다린다 */
    private List<String> drain(Long userId) throws Exception
    {
        Thread.sleep(300);
        List<String> messages = new ArrayList<>();
        received.get(userId).drainTo(messages);
        return messages;
    }

    /** {@code PUBSUB NUMSUB} — 그 채널을 지금 구독하고 있는 연결의 수({@code BoardSignalTest} 와 같은 방식이다) */
    @SuppressWarnings("unchecked")
    private long subscribers(String channel)
    {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
            if(!(connection.getNativeConnection() instanceof RedisClusterAsyncCommands<?, ?> commands))
            {
                throw new IllegalStateException("Lettuce 연결이 아니다: " + connection.getNativeConnection());
            }
            try
            {
                return ((RedisClusterAsyncCommands<byte[], byte[]>) commands)
                        .pubsubNumsub(channel.getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS)
                        .values().stream().mapToLong(Long::longValue).sum();
            }
            catch(Exception e)
            {
                throw new IllegalStateException(e);
            }
        });
        return count == null ? 0 : count;
    }
}
