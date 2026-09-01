package com.queuemate.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.ratelimit.RateLimiter;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.party.service.PartyService;
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
import org.springframework.context.ApplicationContext;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WebRTC signaling relay. 서버는 중개만 하고 내용은 보지 않는다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalRelayIntegrationTest {

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
    @Autowired PartyService partyService;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;
    @Autowired ApplicationContext context;
    @Autowired RateLimiter rateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<WebSocketSession> opened = new ArrayList<>();
    private final List<Collector> collectors = new ArrayList<>();

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
        collectors.clear();
    }

    @Test
    void 실제_서버가_뜨면_프레임_상한_설정이_적용된다() {
        // @ConditionalOnBean은 조건이 안 맞으면 조용히 빈을 만들지 않는다.
        // 상한이 적용되지 않은 채로 배포되는 걸 막으려면 이 확인이 필요하다.
        assertTrue(context.containsBean("webSocketContainer"),
                "웹 서버가 뜬 환경에서는 프레임 상한 빈이 있어야 한다");
    }

    @Test
    void 같은_파티원에게_signal이_전달된다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        fromA.sendMessage(signal(partyId, b, "OFFER", "{\"sdp\":\"v=0 fake\"}"));

        JsonNode received = forB.next();
        assertEquals("WEBRTC_SIGNAL", received.get("type").asText());
        assertEquals(partyId.toString(), received.get("payload").get("partyId").asText());
        assertEquals("OFFER", received.get("payload").get("signalType").asText());
        // 서버는 data를 해석하지 않고 그대로 넘긴다.
        assertEquals("v=0 fake", received.get("payload").get("data").get("sdp").asText());
    }

    @Test
    void 보낸_사람은_세션_주체로_덮어쓴다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID impostor = user("impostor");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        // 클라이언트가 fromUserId를 위조해서 보낸다.
        String payload = """
                {"type":"WEBRTC_SIGNAL","partyId":"%s","targetUserId":"%s",
                 "fromUserId":"%s","signalType":"ICE","data":{}}
                """.formatted(partyId, b, impostor);
        fromA.sendMessage(new TextMessage(payload));

        assertEquals(a.toString(), forB.next().get("payload").get("fromUserId").asText(),
                "위조한 값이 아니라 인증된 주체가 실려야 한다");
    }

    @Test
    void 파티_밖의_사람에게는_전달되지_않는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID outsider = user("outsider");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forOutsider = new Collector();
        connect(outsider, forOutsider);
        waitForSessions(2);

        fromA.sendMessage(signal(partyId, outsider, "OFFER", "{}"));

        assertTrue(forOutsider.isEmptyAfterWait(), "받은 것: " + forOutsider.received);
    }

    @Test
    void 파티원이_아닌_사람이_보내면_전달되지_않는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID outsider = user("outsider");
        UUID partyId = party(a, b);
        WebSocketSession fromOutsider = connect(outsider, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        fromOutsider.sendMessage(signal(partyId, b, "OFFER", "{}"));

        assertTrue(forB.isEmptyAfterWait(), "받은 것: " + forB.received);
    }

    @Test
    void 자기_자신에게는_보낼_수_없다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        Collector forA = new Collector();
        WebSocketSession fromA = connect(a, forA);
        waitForSessions(1);

        fromA.sendMessage(signal(partyId, a, "OFFER", "{}"));

        assertTrue(forA.isEmptyAfterWait(), "받은 것: " + forA.received);
    }

    @Test
    void 계약에_없는_signalType은_거절한다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        fromA.sendMessage(signal(partyId, b, "HANGUP", "{}"));

        assertTrue(forB.isEmptyAfterWait(), "받은 것: " + forB.received);
    }

    @Test
    void 깨진_메시지를_보내도_연결이_유지된다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        fromA.sendMessage(new TextMessage("이건 JSON이 아닙니다"));
        fromA.sendMessage(signal(partyId, b, "ICE", "{}"));

        // 앞 프레임이 깨졌어도 뒤 프레임은 정상 전달된다.
        assertEquals("WEBRTC_SIGNAL", forB.next().get("type").asText());
        assertTrue(fromA.isOpen());
    }

    @Test
    void 한도를_넘긴_signal은_상대에게_가지_않는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        // 운영 한도는 300이라 테스트에서 다 채우면 느리다. 같은 창의 카운터를 미리 소진시킨다.
        for (int i = 0; i < 300; i++) {
            rateLimiter.tryAcquire("signal", a.toString(), 300, java.time.Duration.ofSeconds(10),
                    RateLimiter.OnUnavailable.ALLOW);
        }
        fromA.sendMessage(signal(partyId, b, "ICE", "{}"));

        assertTrue(forB.isEmptyAfterWait(), "한도를 넘겼는데 전달됐다: " + forB.received);
        assertTrue(fromA.isOpen(), "한도를 넘겼다고 연결을 끊지는 않는다");
    }

    @Test
    void 보낸_순서대로_도착한다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        WebSocketSession fromA = connect(a, new Collector());
        Collector forB = new Collector();
        connect(b, forB);
        waitForSessions(2);

        // ICE 후보는 찾는 족족 보내므로 순서가 섞이면 협상이 꼬인다.
        fromA.sendMessage(signal(partyId, b, "OFFER", "{\"seq\":0}"));
        for (int i = 1; i <= 20; i++) {
            fromA.sendMessage(signal(partyId, b, "ICE", "{\"seq\":%d}".formatted(i)));
        }

        for (int i = 0; i <= 20; i++) {
            assertEquals(i, forB.next().get("payload").get("data").get("seq").asInt(),
                    i + "번째 프레임의 순서가 어긋났다");
        }
    }

    private TextMessage signal(UUID partyId, UUID target, String signalType, String data) {
        return new TextMessage("""
                {"type":"WEBRTC_SIGNAL","partyId":"%s","targetUserId":"%s","signalType":"%s","data":%s}
                """.formatted(partyId, target, signalType, data));
    }

    private WebSocketSession connect(UUID userId, Collector collector) throws Exception {
        collectors.add(collector);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of(
                WebSocketProtocol.VERSION,
                WebSocketProtocol.BEARER_PREFIX + tokenService.issueAccessToken(userId)));
        WebSocketSession session = new StandardWebSocketClient()
                .execute(collector, headers, java.net.URI.create("ws://localhost:" + port + "/ws"))
                .get(10, TimeUnit.SECONDS);
        opened.add(session);
        return session;
    }

    private void waitForSessions(int expected) throws Exception {
        for (int i = 0; i < 100 && sessions.openSessionCount() < expected; i++) {
            Thread.sleep(20);
        }
        assertEquals(expected, sessions.openSessionCount());
        // 연결 직후 서버가 SESSION_SNAPSHOT을 한 번 보낸다. signaling 검증에 섞이지 않게 비운다.
        for (Collector collector : collectors) {
            collector.drainSnapshot();
        }
    }

    private class Collector extends TextWebSocketHandler {
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        private final List<String> received = new ArrayList<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            frames.add(message.getPayload());
            received.add(message.getPayload());
        }

        JsonNode next() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "프레임이 도착하지 않았다");
            return objectMapper.readTree(frame);
        }

        boolean isEmptyAfterWait() throws Exception {
            return frames.poll(1, TimeUnit.SECONDS) == null;
        }

        void drainSnapshot() throws Exception {
            String frame = frames.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "스냅샷이 도착하지 않았다");
            assertEquals("SESSION_SNAPSHOT", objectMapper.readTree(frame).get("type").asText());
        }
    }

    private UUID user(String nickname) {
        return users.save(User.create(nickname + "@queuemate.dev", "hash", nickname)).getId();
    }

    private UUID party(UUID... members) {
        UUID proposalId = UUID.randomUUID();
        jdbc.sql("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) "
                        + "VALUES (:id, 'REALTIME', 'CONFIRMED', now() + interval '1 minute', :now)")
                .param("id", proposalId).param("now", OffsetDateTime.now()).update();
        for (UUID member : members) {
            jdbc.sql("INSERT INTO proposal_members (proposal_id, user_id, source_request_id, acceptance) "
                            + "VALUES (:p, :u, :r, 'ACCEPTED')")
                    .param("p", proposalId).param("u", member).param("r", UUID.randomUUID()).update();
        }
        return partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", members.length, null);
    }
}
