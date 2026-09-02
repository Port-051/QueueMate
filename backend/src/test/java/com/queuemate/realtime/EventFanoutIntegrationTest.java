package com.queuemate.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.realtime.event.EventFanout;
import com.queuemate.realtime.event.EventFanout.FanoutMessage;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import com.queuemate.realtime.session.NodeIdentity;
import com.queuemate.realtime.session.SessionRegistry;
import com.queuemate.realtime.ws.WebSocketProtocol;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 다른 서버에 붙은 사용자에게 이벤트가 가는지 확인한다.
 *
 * 서버를 두 대 띄우는 대신 다른 노드가 보낸 것처럼 Redis 채널에 직접 넣는다.
 * 이 프로세스가 봐야 하는 것은 채널로 들어온 메시지뿐이라, 그것이 진짜 다른 JVM에서
 * 왔는지는 구분할 수 없고 구분할 필요도 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventFanoutIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate").withUsername("queuemate").withPassword("queuemate");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalServerPort int port;
    @Autowired JwtTokenService tokenService;
    @Autowired SessionRegistry sessions;
    @Autowired NodeIdentity node;
    @Autowired EventFanout fanout;
    @Autowired RealtimeEventPublisher publisher;
    @Autowired StringRedisTemplate redis;
    @Autowired RedisMessageListenerContainer listeners;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();
    private final List<WebSocketSession> opened = new ArrayList<>();
    private final List<MessageListener> extraListeners = new ArrayList<>();

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "blocks, friendships, friend_requests, users CASCADE").update();
    }

    @AfterEach
    void closeSessions() throws Exception {
        for (WebSocketSession session : opened) {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
        opened.clear();
        for (MessageListener listener : extraListeners) {
            listeners.removeMessageListener(listener);
        }
        extraListeners.clear();
    }

    @Test
    void 다른_노드가_보낸_이벤트를_내_session으로_전달한다() throws Exception {
        UUID a = user("alpha");
        Collector collector = connect(a);
        collector.next();

        // 다른 서버에서 파티 준비가 바뀐 상황이다. 그 서버에는 이 사용자의 session이 없다.
        publishAsOtherNode(UUID.randomUUID(), List.of(a),
                ServerEvent.of(EventType.PARTY_READY_CHANGED, Map.of("ready", true)));

        JsonNode event = collector.next();
        assertEquals("PARTY_READY_CHANGED", event.get("type").asText());
        assertTrue(event.get("payload").get("ready").asBoolean());
    }

    @Test
    void 내가_보낸_것은_다시_전달하지_않는다() throws Exception {
        UUID a = user("alpha");
        Collector collector = connect(a);
        collector.next();

        // publish가 로컬로 한 번 보내고 채널에도 넣는다. 구독한 자기 자신이 또 보내면
        // 클라이언트가 같은 이벤트를 두 번 받는다.
        publisher.publish(List.of(a), ServerEvent.of(EventType.PARTY_CLOSED, Map.of("n", 1)));

        assertEquals("PARTY_CLOSED", collector.next().get("type").asText());
        assertNull(collector.poll(), "같은 이벤트가 두 번 도착했다");
    }

    @Test
    void 내_session에_보낸_이벤트를_다른_노드에도_넘긴다() throws Exception {
        UUID a = user("alpha");
        Collector collector = connect(a);
        collector.next();
        BlockingQueue<String> onChannel = subscribeToChannel();

        // 이 노드에 대상이 전부 있어도 채널에 넣어야 한다. 같은 사용자가 다른 노드에도
        // 탭을 열어 둘 수 있어서, 로컬로 몇 개 보냈는지로는 남은 대상이 있는지 알 수 없다.
        publisher.publish(List.of(a), ServerEvent.of(EventType.PARTY_CLOSED, Map.of("n", 1)));

        String envelope = onChannel.poll(5, TimeUnit.SECONDS);
        assertNotNull(envelope, "채널에 아무것도 발행되지 않았다");
        JsonNode parsed = objectMapper.readTree(envelope);
        assertEquals(node.asString(), parsed.get("nodeId").asText());
        assertEquals(a.toString(), parsed.get("userIds").get(0).asText());
        assertEquals("PARTY_CLOSED", parsed.get("event").get("type").asText());
    }

    @Test
    void 이_노드에_없는_사용자의_이벤트는_버린다() throws Exception {
        UUID a = user("alpha");
        UUID elsewhere = user("elsewhere");
        Collector collector = connect(a);
        collector.next();

        // 모든 노드가 모든 이벤트를 받는다. 자기에게 없는 대상은 조용히 버려야 한다.
        publishAsOtherNode(UUID.randomUUID(), List.of(elsewhere),
                ServerEvent.of(EventType.PARTY_CLOSED, Map.of("n", 1)));

        assertNull(collector.poll(), "남의 이벤트가 배달됐다");
    }

    @Test
    void 형식이_깨진_메시지는_구독을_끊지_않는다() throws Exception {
        UUID a = user("alpha");
        Collector collector = connect(a);
        collector.next();

        redis.convertAndSend(EventFanout.CHANNEL, "{ this is not our envelope");

        // 한 건을 못 읽었다고 그 뒤 이벤트까지 못 받으면 노드 하나가 조용히 고립된다.
        publishAsOtherNode(UUID.randomUUID(), List.of(a),
                ServerEvent.of(EventType.PARTY_CLOSED, Map.of("n", 1)));
        assertEquals("PARTY_CLOSED", collector.next().get("type").asText());
    }

    @Test
    void 여러_이벤트의_순서가_유지된다() throws Exception {
        UUID a = user("alpha");
        UUID other = UUID.randomUUID();
        Collector collector = connect(a);
        collector.next();

        // signaling은 OFFER 다음에 ICE가 와야 한다. 순서가 섞이면 통화가 성립하지 않는다.
        for (int i = 0; i < 10; i++) {
            publishAsOtherNode(other, List.of(a),
                    ServerEvent.of(EventType.WEBRTC_SIGNAL, Map.of("seq", i)));
        }

        for (int i = 0; i < 10; i++) {
            assertEquals(i, collector.next().get("payload").get("seq").asInt());
        }
    }

    /** 다른 노드가 무엇을 받게 되는지 그대로 본다. */
    private BlockingQueue<String> subscribeToChannel() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        MessageListener listener = (Message message, byte[] pattern) ->
                received.add(new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        listeners.addMessageListener(listener, new ChannelTopic(EventFanout.CHANNEL));
        extraListeners.add(listener);
        // 구독 등록은 비동기다. 바로 발행하면 놓친다.
        Thread.sleep(300);
        return received;
    }

    /** 다른 JVM이 보낸 것처럼 채널에 직접 넣는다. */
    private void publishAsOtherNode(UUID nodeId, List<UUID> userIds, ServerEvent event)
            throws Exception {
        redis.convertAndSend(EventFanout.CHANNEL,
                objectMapper.writeValueAsString(new FanoutMessage(nodeId, userIds, event)));
    }

    private Collector connect(UUID userId) throws Exception {
        Collector collector = new Collector();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of(WebSocketProtocol.VERSION,
                WebSocketProtocol.BEARER_PREFIX + tokenService.issueAccessToken(userId)));
        opened.add(new StandardWebSocketClient()
                .execute(collector, headers, URI.create("ws://localhost:" + port + "/ws"))
                .get(10, TimeUnit.SECONDS));
        return collector;
    }

    private UUID user(String nickname) {
        return users.save(User.create(nickname + "@queuemate.dev", "hash", nickname)).getId();
    }

    private class Collector extends TextWebSocketHandler {

        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
        }

        JsonNode next() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "이벤트가 도착하지 않았다");
            return objectMapper.readTree(frame);
        }

        /** 오지 않아야 하는 것을 확인할 때 쓴다. 짧게 기다린다. */
        String poll() throws Exception {
            return frames.poll(1, TimeUnit.SECONDS);
        }
    }
}
