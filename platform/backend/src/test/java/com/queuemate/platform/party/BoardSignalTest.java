package com.queuemate.platform.party;

import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시판 채널 신호 — <b>구독해서 확인한다</b>(CLAUDE.md §3.2). 발행은 실패해도 조용하고 구독자 0명도 실패가 아니라서, 채널 이름이나 봉투가 틀려도
 * 다른 테스트는 전부 통과한다. {@code notification} 이 하는 것과 똑같이 {@code qm:pubsub:board} 를 구독해 도착한 글자를 본다.
 *
 * <p>채널 이름을 main 의 상수에서 가져오지 않고 <b>글자로 적었다</b> — 상수에 오타가 나면 이 테스트가 깨져야 한다.
 *
 * <p>같은 Redis 에 {@code room} 이 떠 있으면 그쪽 신호도 섞여 온다 — 그래서 "정확히 한 번"은 짧은 창 안에서만 센다.
 */
class BoardSignalTest extends PostTestSupport {

    private static final String BOARD_CHANNEL = "qm:pubsub:board";

    @Autowired
    RedisConnectionFactory connectionFactory;

    private RedisMessageListenerContainer container;
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void subscribe() throws Exception
    {
        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> received.add(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(BOARD_CHANNEL));
        container.afterPropertiesSet();
        // notification 이 같은 Redis 에 떠 있으면 구독자가 이미 있다 — "1 이상"이 아니라 "늘었다"를 본다
        long before = subscribers();
        container.start();
        // 구독이 Redis 에 걸린 것을 본 뒤에 움직인다 — 그 전에 발행된 신호는 오지 않는다
        long deadline = System.currentTimeMillis() + 15_000;
        while(subscribers() <= before)
        {
            assertThat(System.currentTimeMillis()).as("구독이 걸리지 않았다").isLessThan(deadline);
            Thread.sleep(50);
        }
        received.clear();
    }

    @AfterEach
    void unsubscribe() throws Exception
    {
        container.stop();
        container.destroy();
    }

    @Test
    @DisplayName("글을 쓰면 BOARD_CHANGED 가 온다 — 봉투는 네 칸이고 payload 는 {} 다. room 의 봉투와 같은 모양이다")
    void envelope() throws Exception
    {
        Cookie cookie = login(newNickname());
        createLolPost(cookie);

        String message = received.poll(10, TimeUnit.SECONDS);
        assertThat(message).as("신호가 오지 않았다").isNotNull();
        JsonNode envelope = objectMapper.readTree(message);

        List<String> fields = new ArrayList<>(envelope.propertyNames());
        assertThat(fields).containsExactly("type", "eventId", "occurredAt", "payload");
        assertThat(envelope.get("type").asString()).isEqualTo("BOARD_CHANGED");
        assertThat(UUID.fromString(envelope.get("eventId").asString()).toString()).isEqualTo(envelope.get("eventId").asString());
        // ISO-8601 UTC, 밀리초까지(Instant 는 밀리초가 0 이면 소수부를 아예 쓰지 않는다 — room · matching 과 같다)
        assertThat(envelope.get("occurredAt").asString()).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z$");
        // 데이터를 싣지 않는다 — roomId 도 game 도 없다. null 이 아니라 빈 객체다
        assertThat(envelope.get("payload").isObject()).isTrue();
        assertThat(envelope.get("payload").isEmpty()).isTrue();
        assertThat(message).doesNotContain("roomId").doesNotContain("postId").doesNotContain("LOL");
    }

    @Test
    @DisplayName("글 쓰기(방까지 만든다) · 고치기 · 방장 확정 · 방장이 지우기에 신호가 한 번씩 온다. 거절된 요청 · 그냥 읽기에는 오지 않는다")
    void signalsOnChangesOnly() throws Exception
    {
        String host = newNickname();
        String otherLogin = newNickname();
        Cookie cookie = login(host);
        Cookie other = login(otherLogin);
        Long hostId = userIdOf(host);
        Long postId = createLolPost(cookie);
        // 글과 방이 한 트랜잭션에서 생긴다 — 방 만들기의 신호와 글의 신호가 합쳐져 커밋 뒤에 한 번이다
        assertThat(drain()).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/posts/" + postId).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"바꿨다\"}")).andExpect(status().isOk());
        assertThat(drain()).isEqualTo(1);

        // 거절 — 롤백된 것도, 아무것도 안 바뀐 것도 알리지 않는다
        createPost(cookie, lolPostBody("또 쓴다")).andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(other)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(other)).andExpect(status().isOk());
        list(other, "LOL");
        assertThat(drain()).isZero();

        // 방장 확정 — 방의 확정과 글의 기록이 한 트랜잭션이라 신호도 한 번이다
        openRoom(postId, hostId, userIdOf(otherLogin));
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(cookie)).andExpect(status().isNoContent());
        assertThat(drain()).isEqualTo(1);
        // 이미 확정된 방을 또 확정해도 바뀐 것이 없다
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(cookie)).andExpect(status().isOk());
        assertThat(drain()).isZero();

        // 새 글을 쓰려면 먼저 방에서 나온다(방에 있으면 409 IN_OTHER_ROOM) — 나가기의 신호는 여기서 세지 않는다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(cookie)).andExpect(status().isNoContent());
        drain();
        Long second = createLolPost(cookie);
        assertThat(drain()).isEqualTo(1);
        mockMvc.perform(delete("/api/v1/posts/" + second).cookie(cookie)).andExpect(status().isNoContent());
        assertThat(drain()).isEqualTo(1);
        // 이미 만료된 글을 또 지워도 바뀐 것이 없다
        mockMvc.perform(delete("/api/v1/posts/" + second).cookie(cookie)).andExpect(status().isNoContent());
        assertThat(drain()).isZero();
    }

    @Test
    @DisplayName("목록 조회 한 번에 여러 글이 만료돼도 신호는 한 번이다. 옮겨 적을 것이 없는 평소의 목록은 신호를 내지 않는다")
    void oneSignalPerListCall() throws Exception
    {
        Cookie viewer = login(newNickname());
        List<Long> posts = new ArrayList<>();
        for(int i = 0; i < 3; i++)
        {
            String host = newNickname();
            posts.add(createLolPost(login(host)));
        }
        drain();

        // 방이 다 떠 있다 — 옮겨 적을 것이 없어 신호가 없다
        list(viewer, "LOL");
        assertThat(drain()).isZero();

        posts.forEach(this::closeRoom);
        list(viewer, "LOL");
        posts.forEach(postId -> assertThat(statusOf(postId)).isEqualTo("EXPIRED"));
        assertThat(drain()).isEqualTo(1);

        // 다시 그려도 더 만료시킬 것이 없다
        list(viewer, "LOL");
        assertThat(drain()).isZero();
    }

    /** 잠깐 기다렸다가 그동안 온 신호의 수를 돌려주고 비운다. 발행은 커밋 직후라 요청이 끝났을 때 이미 나가 있다 — 도착만 기다린다 */
    private int drain() throws Exception
    {
        Thread.sleep(400);
        List<String> messages = new ArrayList<>();
        received.drainTo(messages);
        return messages.size();
    }

    /**
     * {@code PUBSUB NUMSUB} — 그 채널을 지금 구독하고 있는 연결의 수. Spring 의 {@code connection.execute()} 는 이 명령의 응답 모양을 몰라서
     * Lettuce 의 명령을 직접 부른다. 확인하려고 채널에 시험 메시지를 발행하지는 않는다 — {@code notification} 이 떠 있으면 브라우저까지 간다.
     */
    @SuppressWarnings("unchecked")
    private long subscribers()
    {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
            if(!(connection.getNativeConnection() instanceof RedisClusterAsyncCommands<?, ?> commands))
            {
                throw new IllegalStateException("Lettuce 연결이 아니다: " + connection.getNativeConnection());
            }
            try
            {
                return ((RedisClusterAsyncCommands<byte[], byte[]>) commands)
                        .pubsubNumsub(BOARD_CHANNEL.getBytes(StandardCharsets.UTF_8)).get(5, TimeUnit.SECONDS)
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
