package com.queuemate.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** handshake 인증부터 이벤트 수신까지 실제 소켓으로 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<WebSocketSession> opened = new ArrayList<>();

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
    }

    @Test
    void 연결하면_현재_상태_스냅샷을_먼저_받는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        Collector collector = new Collector();

        connect(tokenService.issueAccessToken(a), collector);

        JsonNode snapshot = collector.next();
        assertEquals("SESSION_SNAPSHOT", snapshot.get("type").asText());
        JsonNode parties = snapshot.get("payload").get("parties");
        assertEquals(1, parties.size());
        assertEquals(partyId.toString(), parties.get(0).get("id").asText());
    }

    @Test
    void 끊긴_동안_바뀐_상태가_재연결_스냅샷에_담긴다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        Collector before = new Collector();
        WebSocketSession first = connect(tokenService.issueAccessToken(a), before);
        before.next();
        assertTrue(waitUntil(() -> sessions.isOnline(a)));

        first.close(CloseStatus.NORMAL);
        assertTrue(waitUntil(() -> !sessions.isOnline(a)));
        // 끊긴 사이에 b가 준비를 눌렀다. a는 이 이벤트를 받지 못한다.
        partyService.changeReady(partyId, b, true);

        Collector after = new Collector();
        connect(tokenService.issueAccessToken(a), after);

        JsonNode parties = after.next().get("payload").get("parties");
        JsonNode members = parties.get(0).get("members");
        boolean bReady = false;
        for (JsonNode member : members) {
            if (b.toString().equals(member.get("userId").asText())) {
                bReady = member.get("ready").asBoolean();
            }
        }
        assertTrue(bReady, "끊긴 동안 바뀐 준비 상태가 스냅샷에 있어야 한다");
    }

    @Test
    void 파티가_없으면_빈_스냅샷을_받는다() throws Exception {
        UUID a = user("alpha");
        Collector collector = new Collector();

        connect(tokenService.issueAccessToken(a), collector);

        JsonNode snapshot = collector.next();
        assertEquals("SESSION_SNAPSHOT", snapshot.get("type").asText());
        assertEquals(0, snapshot.get("payload").get("parties").size());
    }

    @Test
    void 탭마다_각자_스냅샷을_받는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        party(a, b);
        Collector tab1 = new Collector();
        Collector tab2 = new Collector();

        connect(tokenService.issueAccessToken(a), tab1);
        connect(tokenService.issueAccessToken(a), tab2);

        // 세션 단위다. 새 탭이 붙었다고 기존 탭이 다시 받지는 않는다.
        assertEquals("SESSION_SNAPSHOT", tab1.next().get("type").asText());
        assertEquals("SESSION_SNAPSHOT", tab2.next().get("type").asText());
        assertTrue(tab1.isEmptyAfterWait(), "기존 탭에 두 번 가면 안 된다: " + tab1.received);
    }

    @Test
    void 유효한_token이면_연결되고_버전만_돌려받는다() throws Exception {
        UUID userId = user("alpha");
        Collector collector = new Collector();

        WebSocketSession session = connect(tokenService.issueAccessToken(userId), collector);

        assertTrue(session.isOpen());
        // 토큰을 그대로 되돌려주면 응답 헤더에 token이 남는다.
        assertEquals(WebSocketProtocol.VERSION, session.getAcceptedProtocol());
        assertTrue(waitUntil(() -> sessions.isOnline(userId)), "레지스트리에 등록돼야 한다");
    }

    @Test
    void token이_없으면_handshake에서_끊는다() {
        Collector collector = new Collector();
        // 버전만 보내고 bearer는 빼면 인증 정보가 없다.
        assertThrows(ExecutionException.class, () -> rawConnect(
                new String[]{WebSocketProtocol.VERSION}, collector));
    }

    @Test
    void 잘못된_token이면_handshake에서_끊는다() {
        Collector collector = new Collector();
        assertThrows(ExecutionException.class, () -> connect("not-a-real-token", collector));
    }

    @Test
    void 연결이_닫히면_레지스트리에서_빠진다() throws Exception {
        UUID userId = user("alpha");
        WebSocketSession session = connect(tokenService.issueAccessToken(userId), new Collector());
        assertTrue(waitUntil(() -> sessions.isOnline(userId)));

        session.close(CloseStatus.NORMAL);

        assertTrue(waitUntil(() -> !sessions.isOnline(userId)), "close 후 정리돼야 한다");
    }

    @Test
    void 한_사용자가_여러_탭을_열면_모두_받는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        Collector tab1 = new Collector();
        Collector tab2 = new Collector();
        connect(tokenService.issueAccessToken(a), tab1);
        connect(tokenService.issueAccessToken(a), tab2);
        assertTrue(waitUntil(() -> sessions.sessionsOf(a).size() == 2));
        // 연결 직후 스냅샷이 먼저 온다.
        tab1.next();
        tab2.next();

        partyService.changeReady(partyId, a, true);

        assertEquals("PARTY_READY_CHANGED", tab1.next().get("type").asText());
        assertEquals("PARTY_READY_CHANGED", tab2.next().get("type").asText());
    }

    @Test
    void 준비를_바꾸면_파티원_전원에게_이벤트가_간다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        Collector forA = new Collector();
        Collector forB = new Collector();
        connect(tokenService.issueAccessToken(a), forA);
        connect(tokenService.issueAccessToken(b), forB);
        assertTrue(waitUntil(() -> sessions.openSessionCount() == 2));
        forA.next();
        forB.next();

        partyService.changeReady(partyId, a, true);

        JsonNode received = forB.next();
        assertEquals("PARTY_READY_CHANGED", received.get("type").asText());
        assertEquals(partyId.toString(), received.get("payload").get("partyId").asText());
        assertEquals(a.toString(), received.get("payload").get("userId").asText());
        assertEquals("OPEN", received.get("payload").get("status").asText());
        assertNotNull(received.get("eventId").asText());
        // 본인도 받는다. 여러 탭을 열어둔 경우 다른 탭이 갱신돼야 한다.
        assertEquals("PARTY_READY_CHANGED", forA.next().get("type").asText());
    }

    @Test
    void 파티원이_아니면_그_파티_이벤트를_받지_않는다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID outsider = user("outsider");
        UUID partyId = party(a, b);
        Collector forOutsider = new Collector();
        connect(tokenService.issueAccessToken(outsider), forOutsider);
        connect(tokenService.issueAccessToken(a), new Collector());
        assertTrue(waitUntil(() -> sessions.openSessionCount() == 2));
        // 파티가 없는 사용자도 빈 스냅샷을 받는다. 그것을 먼저 소비한다.
        forOutsider.next();

        partyService.changeReady(partyId, a, true);

        assertTrue(forOutsider.isEmptyAfterWait(), "받은 이벤트: " + forOutsider.received);
    }

    private WebSocketSession connect(String token, Collector collector) throws Exception {
        return rawConnect(
                new String[]{WebSocketProtocol.VERSION, WebSocketProtocol.BEARER_PREFIX + token},
                collector);
    }

    private WebSocketSession rawConnect(String[] protocols, Collector collector) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of(protocols));
        WebSocketSession session = new StandardWebSocketClient()
                .execute(collector, headers, java.net.URI.create("ws://localhost:" + port + "/ws"))
                .get(10, TimeUnit.SECONDS);
        opened.add(session);
        return session;
    }

    private boolean waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
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

    /** 소켓으로 들어온 프레임을 순서대로 모은다. */
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
            assertNotNull(frame, "이벤트가 도착하지 않았다");
            return objectMapper.readTree(frame);
        }

        boolean isEmptyAfterWait() throws Exception {
            return frames.poll(1, TimeUnit.SECONDS) == null;
        }
    }
}
