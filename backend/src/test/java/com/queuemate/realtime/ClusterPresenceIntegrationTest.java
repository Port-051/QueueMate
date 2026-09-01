package com.queuemate.realtime;

import com.queuemate.common.security.JwtTokenService;
import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.party.service.PartyService;
import com.queuemate.realtime.presence.DeparturePendingStore;
import com.queuemate.realtime.presence.DepartureSweeper;
import com.queuemate.realtime.session.ClusterPresence;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 다른 서버에 붙은 사용자를 접속 중으로 보는지 확인한다.
 *
 * 이걸 틀리면 게임 중인 사람이 파티에서 빠지고 파티가 닫힌다. 이벤트를 놓치는 것과 달리
 * 데이터가 바뀌므로 되돌릴 수 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClusterPresenceIntegrationTest {

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
    @Autowired ClusterPresence presence;
    @Autowired SessionRegistry sessions;
    @Autowired NodeIdentity node;
    @Autowired DeparturePendingStore pending;
    @Autowired DepartureSweeper sweeper;
    @Autowired PartyService partyService;
    @Autowired PartyRepository parties;
    @Autowired JwtTokenService tokenService;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final List<WebSocketSession> opened = new ArrayList<>();

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "blocks, friendships, friend_requests, users CASCADE").update();
        redis.delete(redis.keys("qm:ws:presence:*"));
        redis.delete("qm:party:departure-due");
    }

    @AfterEach
    void closeSessions() throws Exception {
        for (WebSocketSession session : opened) {
            if (session.isOpen()) {
                session.close(CloseStatus.NORMAL);
            }
        }
        opened.clear();
        redis.delete(redis.keys("qm:ws:node:*"));
    }

    @Test
    void 다른_노드에_붙어_있으면_접속_중이다() {
        UUID a = user("alpha");
        UUID otherNode = UUID.randomUUID();
        markOnlineAt(a, otherNode, true);

        // 이 프로세스에는 session이 없다. 그것만 보면 오프라인이다.
        assertFalse(sessions.hasLocalSession(a));
        assertTrue(presence.isOnline(a));
    }

    @Test
    void 죽은_노드가_남긴_기록은_접속으로_치지_않는다() {
        UUID a = user("alpha");
        UUID deadNode = UUID.randomUUID();
        // 서버가 죽으면 close 콜백이 안 돌아 presence에서 자기를 못 지운다.
        // 생존 키만 만료되므로 그것으로 판정한다.
        markOnlineAt(a, deadNode, false);

        assertFalse(presence.isOnline(a));
        // 판정하면서 낡은 기록을 걷는다. 안 걷으면 죽은 노드 수만큼 계속 쌓인다.
        assertEquals(0L, redis.opsForSet().size("qm:ws:presence:" + a));
    }

    @Test
    void 연결하면_이_노드가_접속_기록에_들어간다() throws Exception {
        UUID a = user("alpha");
        WebSocketSession session = connect(a);

        assertTrue(waitUntil(() -> presenceContains(a, node.asString())));

        session.close(CloseStatus.NORMAL);
        assertTrue(waitUntil(() -> !presenceContains(a, node.asString())),
                "마지막 탭이 닫히면 빠져야 한다");
    }

    @Test
    void 탭이_남아_있으면_접속_기록도_남는다() throws Exception {
        UUID a = user("alpha");
        WebSocketSession first = connect(a);
        connect(a);
        assertTrue(waitUntil(() -> presenceContains(a, node.asString())));

        first.close(CloseStatus.NORMAL);

        // 탭 하나를 닫았다고 기록을 지우면 다른 노드가 이 사용자를 오프라인으로 본다.
        assertTrue(waitUntil(() -> sessions.sessionsOf(a).size() == 1));
        assertTrue(presenceContains(a, node.asString()));
    }

    @Test
    void 다른_노드에_붙어_있는_사람은_파티에서_내보내지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        markOnlineAt(a, UUID.randomUUID(), true);
        // 유예가 이미 지난 상태로 만든다.
        pending.schedule(a, Duration.ZERO);

        sweeper.sweep();

        // 이 프로세스만 보면 a는 오프라인이다. 그대로 믿으면 게임 중인 사람이 빠진다.
        assertEquals(PartyStatus.OPEN, parties.findById(partyId).orElseThrow().getStatus());
        assertEquals(2, activeMemberCount(partyId));
    }

    @Test
    void 어느_노드에도_없으면_파티에서_내보낸다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = party(a, b);
        markOnlineAt(a, UUID.randomUUID(), false);
        pending.schedule(a, Duration.ZERO);

        sweeper.sweep();

        // 2인 파티에서 한 명이 빠지면 남은 인원이 1명이라 닫힌다.
        assertEquals(PartyStatus.CLOSED, parties.findById(partyId).orElseThrow().getStatus());
    }

    // ---- helpers ----

    /** 다른 서버에 붙어 있는 상황을 만든다. alive=false면 그 서버가 죽은 상황이다. */
    private void markOnlineAt(UUID userId, UUID nodeId, boolean alive) {
        redis.opsForSet().add("qm:ws:presence:" + userId, nodeId.toString());
        if (alive) {
            redis.opsForValue().set("qm:ws:node:" + nodeId, "1", Duration.ofMinutes(5));
        }
    }

    private boolean presenceContains(UUID userId, String nodeId) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember("qm:ws:presence:" + userId, nodeId));
    }

    private long activeMemberCount(UUID partyId) {
        return jdbc.sql("SELECT count(*) FROM party_members WHERE party_id = :id AND left_at IS NULL")
                .param("id", partyId).query(Long.class).single();
    }

    private WebSocketSession connect(UUID userId) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of(WebSocketProtocol.VERSION,
                WebSocketProtocol.BEARER_PREFIX + tokenService.issueAccessToken(userId)));
        WebSocketSession session = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler(), headers,
                        URI.create("ws://localhost:" + port + "/ws"))
                .get(10, TimeUnit.SECONDS);
        opened.add(session);
        return session;
    }

    private boolean waitUntil(BooleanSupplier condition) throws Exception {
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
}
