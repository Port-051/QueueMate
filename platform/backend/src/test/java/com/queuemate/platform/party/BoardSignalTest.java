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
    @DisplayName("글 쓰기(방까지 만든다) · 방장 확정 · 방장이 지우기에 신호가 한 번씩 온다. 거절된 요청(없어진 고치기의 405 포함) · 그냥 읽기에는 오지 않는다")
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

        // 거절 — 롤백된 것도, 아무것도 안 바뀐 것도 알리지 않는다. 글은 고칠 수 없다(2026-10-01 소유자 결정 — PATCH 는 405)
        mockMvc.perform(patch("/api/v1/posts/" + postId).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"바꿨다\"}")).andExpect(status().isMethodNotAllowed());
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

    // ---- 파티가 닫히면 (2026-10-02 소유자 결정 — 글 응답의 closed 가 바뀐다) ----

    /** 방장과 멤버 한 명으로 5인 글의 방을 채우고 확정한다 — 글 번호 = 방 번호. 그동안 나간 신호는 비운다 */
    private Long confirmedRoom(Cookie hostCookie, Cookie memberCookie, Long memberId) throws Exception
    {
        Long postId = createFivePersonLolPost(hostCookie);
        track(postId, memberId);
        enterRoom(memberCookie, postId, "TOP").andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/rooms/" + postId + "/confirm").cookie(hostCookie)).andExpect(status().isNoContent());
        drain();
        return postId;
    }

    private String partyStatusOf(Long postId)
    {
        return jdbcTemplate.queryForObject("select status from parties where post_id = ?", String.class, postId);
    }

    @Test
    @DisplayName("전원이 말없이 사라진 방을 단건이 보고 파티를 닫으면 신호가 한 번 온다 — 이미 닫힌 뒤의 단건 · 목록은 다시 내지 않는다(고리가 없다)")
    void closingAVanishedPartySignalsOnce() throws Exception
    {
        Cookie viewer = login(newNickname());
        String member = newNickname();
        Cookie memberCookie = login(member);
        Long postId = confirmedRoom(login(newNickname()), memberCookie, userIdOf(member));
        // 같은 DB 를 다른 테스트가 쓴다 — 이 페이지에서 옮겨 적을 남의 줄을 먼저 정리해 둔다
        list(viewer, "LOL");
        drain();

        closeRoom(postId);
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(viewer)).andExpect(status().isOk());
        assertThat(partyStatusOf(postId)).isEqualTo("CLOSED");
        assertThat(drain()).isEqualTo(1);

        // 신호를 받은 프런트가 다시 받는다 — 이미 닫힌 파티라 또 닫지 않고 신호도 없다
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(viewer)).andExpect(status().isOk());
        list(viewer, "LOL");
        assertThat(drain()).isZero();
    }

    @Test
    @DisplayName("목록이 사라진 방을 보고 파티를 닫아도 신호가 한 번이다 — 두 번째 목록은 0")
    void listClosingAPartySignalsOnce() throws Exception
    {
        Cookie viewer = login(newNickname());
        String member = newNickname();
        Cookie memberCookie = login(member);
        Long postId = confirmedRoom(login(newNickname()), memberCookie, userIdOf(member));
        list(viewer, "LOL");
        drain();

        closeRoom(postId);
        list(viewer, "LOL");
        assertThat(partyStatusOf(postId)).isEqualTo("CLOSED");
        assertThat(drain()).isEqualTo(1);

        list(viewer, "LOL");
        assertThat(drain()).isZero();
    }

    @Test
    @DisplayName("마지막 사람이 나가 파티가 닫히면 신호가 한 번 온다 — 방이 닫힌 신호와 파티가 닫힌 신호가 따로 나가지 않는다")
    void lastLeaveClosingThePartySignalsOnce() throws Exception
    {
        String member = newNickname();
        Cookie hostCookie = login(newNickname());
        Cookie memberCookie = login(member);
        Long postId = confirmedRoom(hostCookie, memberCookie, userIdOf(member));

        // 한 사람이 나간다 — 방의 인원이 바뀐 신호다. 파티는 열려 있다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(memberCookie)).andExpect(status().isNoContent());
        assertThat(partyStatusOf(postId)).isEqualTo("ACTIVE");
        assertThat(drain()).isEqualTo(1);

        // 마지막 사람(방장)이 나가 방이 없어지고 파티가 닫힌다 — 파티를 닫은 트랜잭션의 신호 하나가 방의 신호를 겸한다
        mockMvc.perform(delete("/api/v1/rooms/" + postId + "/members/me").cookie(hostCookie)).andExpect(status().isNoContent());
        assertThat(partyStatusOf(postId)).isEqualTo("CLOSED");
        assertThat(drain()).isEqualTo(1);
    }

    @Test
    @DisplayName("자동 매칭 파티를 목록이 닫아도 게시판 신호는 없다 — 게시판에 글이 없다")
    void closingAMatchPartySendsNoSignal() throws Exception
    {
        Cookie viewer = login(newNickname());
        list(viewer, "LOL");
        drain();
        // 방 키가 하나도 없는 열린 자동 매칭 파티 — 전원이 말없이 사라졌다
        String matchPartyId = UUID.randomUUID().toString();
        jdbcTemplate.update("insert into parties (source, match_party_id, game, status, created_at) values ('MATCH', ?, 'LOL', 'ACTIVE', now())",
                matchPartyId);
        try
        {
            list(viewer, "LOL");

            assertThat(jdbcTemplate.queryForObject("select status from parties where match_party_id = ?", String.class, matchPartyId))
                    .isEqualTo("CLOSED");
            assertThat(drain()).isZero();
        }
        finally
        {
            jdbcTemplate.update("delete from parties where match_party_id = ?", matchPartyId);
        }
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
