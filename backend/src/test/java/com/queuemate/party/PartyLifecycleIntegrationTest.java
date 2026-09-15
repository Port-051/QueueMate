package com.queuemate.party;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.error.ConflictException;
import com.queuemate.matching.domain.PartyCreationPort.PartyCreationCommand;
import com.queuemate.party.domain.PartyStatus;
import com.queuemate.party.repository.PartyRepository;
import com.queuemate.party.service.PartyDepartureService;
import com.queuemate.party.service.PartyLifecycleService;
import com.queuemate.party.service.PartyService;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 사용자 행동 없이 시간만으로 일어나는 파티 전이 검증.
 *
 * 실제 시간을 기다리지 않는다. 전이 메서드가 기준 시각을 인자로 받으므로
 * 테스트가 시계를 대신 정한다. sleep으로 검증하면 느리고 불안정하다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartyLifecycleIntegrationTest {

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

    @Autowired PartyService partyService;
    @Autowired PartyLifecycleService lifecycle;
    @Autowired PartyDepartureService departures;
    @Autowired PartyRepository parties;
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "blocks, friendships, friend_requests, users CASCADE").update();
    }

    @Test
    void 준비가_유지되면_게임_시작으로_넘어간다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = readyParty(a, b);

        OffsetDateTime now = OffsetDateTime.now().plusMinutes(5);
        OffsetDateTime cutoff = now.minusMinutes(2);
        assertEquals(List.of(partyId), lifecycle.readySince(cutoff));
        assertTrue(lifecycle.startPlaying(partyId, cutoff, now));

        var party = parties.findById(partyId).orElseThrow();
        assertEquals(PartyStatus.PLAYING, party.getStatus());
        assertNotNull(party.getPlayedAt());
    }

    @Test
    void 준비_시간이_모자라면_후보에_잡히지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = readyParty(a, b);

        // 방금 READY가 됐다. 아직 게임 클라이언트에서 서로 추가하는 중일 수 있다.
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(2);
        assertTrue(lifecycle.readySince(cutoff).isEmpty());
        assertFalse(lifecycle.startPlaying(partyId, cutoff, OffsetDateTime.now()));
        assertEquals(PartyStatus.READY, parties.findById(partyId).orElseThrow().getStatus());
    }

    @Test
    void 준비를_풀면_게임_시작_기준이_사라진다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = readyParty(a, b);
        assertNotNull(parties.findById(partyId).orElseThrow().getReadyAt());

        partyService.changeReady(partyId, a, false);

        var party = parties.findById(partyId).orElseThrow();
        assertEquals(PartyStatus.OPEN, party.getStatus());
        assertNull(party.getReadyAt(), "준비가 풀렸는데 기준 시각이 남으면 다시 준비할 때 즉시 게임으로 넘어간다");
    }

    @Test
    void 사람이_드나들어도_준비_유지_시간은_밀리지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = partyService.createParty(new PartyCreationCommand(
                confirmedProposal(a, b, c), GameKey.PUBG, "SQUAD", 3, List.of(a, b, c), null));
        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, b, true);
        partyService.changeReady(partyId, c, true);
        OffsetDateTime firstReadyAt = parties.findById(partyId).orElseThrow().getReadyAt();

        // 준비를 마친 한 명이 나가도 남은 둘은 여전히 준비 상태다. 상태 재계산이 돌지만
        // 기준 시각까지 갱신하면 그 파티는 영원히 게임에 못 들어간다.
        departures.leave(partyId, c);

        var party = parties.findById(partyId).orElseThrow();
        assertEquals(PartyStatus.READY, party.getStatus());
        assertEquals(firstReadyAt, party.getReadyAt());
    }

    @Test
    void 잠근_뒤에_조건이_어긋나면_전이하지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = readyParty(a, b);

        // 후보 목록은 잠금 밖에서 읽는다. 그 사이에 준비가 풀릴 수 있으므로
        // 잠근 뒤 다시 확인하지 않으면 준비가 풀린 파티를 게임 중으로 만든다.
        partyService.changeReady(partyId, a, false);

        OffsetDateTime now = OffsetDateTime.now().plusMinutes(5);
        assertFalse(lifecycle.startPlaying(partyId, now.minusMinutes(2), now));
        assertEquals(PartyStatus.OPEN, parties.findById(partyId).orElseThrow().getStatus());
    }

    @Test
    void 동시에_전이를_시도해도_한_번만_넘어간다() throws Exception {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = readyParty(a, b);

        // 서버가 여러 대면 sweeper도 여러 개 돈다. 같은 파티를 동시에 집을 수 있다.
        OffsetDateTime now = OffsetDateTime.now().plusMinutes(5);
        OffsetDateTime cutoff = now.minusMinutes(2);
        List<Object> results = runConcurrently(3,
                () -> lifecycle.startPlaying(partyId, cutoff, now));

        assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count(),
                "전이는 한 번만 성공해야 한다: " + results);
    }

    @Test
    void 게임이_시작되면_준비를_바꿀_수_없다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = playingParty(a, b);

        // 허용하면 게임 중에 준비를 풀어 이탈 유예를 게임 전 기준으로 되돌릴 수 있다.
        ConflictException e = assertThrows(ConflictException.class,
                () -> partyService.changeReady(partyId, a, false));
        assertEquals("PARTY_PLAYING", e.getCode());
        assertEquals(PartyStatus.PLAYING, parties.findById(partyId).orElseThrow().getStatus());
    }

    @Test
    void 게임_중에_한_명이_나가도_상태는_되돌아가지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = partyService.createParty(new PartyCreationCommand(
                confirmedProposal(a, b, c), GameKey.PUBG, "SQUAD", 3, List.of(a, b, c), null));
        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, b, true);
        partyService.changeReady(partyId, c, true);
        OffsetDateTime now = OffsetDateTime.now().plusMinutes(5);
        assertTrue(lifecycle.startPlaying(partyId, now.minusMinutes(2), now));

        departures.leave(partyId, c);

        // 남은 둘은 게임을 계속한다. 준비 계산이 다시 돌아도 PLAYING을 건드리면 안 된다.
        assertEquals(PartyStatus.PLAYING, parties.findById(partyId).orElseThrow().getStatus());
    }

    @Test
    void 방치된_파티는_닫히고_플레이_기록은_남는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = playingParty(a, b);
        OffsetDateTime playedAt = parties.findById(partyId).orElseThrow().getPlayedAt();

        OffsetDateTime now = OffsetDateTime.now().plusHours(7);
        OffsetDateTime cutoff = now.minus(Duration.ofHours(6));
        assertEquals(List.of(partyId), lifecycle.playingSince(cutoff));
        assertTrue(lifecycle.closeAbandoned(partyId, cutoff, now));

        var party = parties.findById(partyId).orElseThrow();
        assertEquals(PartyStatus.CLOSED, party.getStatus());
        assertNotNull(party.getClosedAt());
        // 닫힌 뒤에도 남아야 최근 함께한 사람이 실제 플레이를 구분할 수 있다.
        assertEquals(playedAt, party.getPlayedAt());
    }

    @Test
    void 아직_시간이_안_된_게임은_닫지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = playingParty(a, b);

        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(6);
        assertTrue(lifecycle.playingSince(cutoff).isEmpty());
        assertFalse(lifecycle.closeAbandoned(partyId, cutoff, OffsetDateTime.now()));
        assertEquals(PartyStatus.PLAYING, parties.findById(partyId).orElseThrow().getStatus());
    }

    // ---- helpers ----

    private UUID readyParty(UUID a, UUID b) {
        UUID partyId = partyService.createParty(new PartyCreationCommand(
                confirmedProposal(a, b), GameKey.LOL, "SOLO_DUO", 2, List.of(a, b), null));
        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, b, true);
        return partyId;
    }

    private UUID playingParty(UUID a, UUID b) {
        UUID partyId = readyParty(a, b);
        OffsetDateTime now = OffsetDateTime.now().plusMinutes(5);
        assertTrue(lifecycle.startPlaying(partyId, now.minusMinutes(2), now));
        return partyId;
    }

    private List<Object> runConcurrently(int threads, Callable<?> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private UUID user(String nickname) {
        return users.save(User.create(nickname + "@queuemate.dev", "hash", nickname)).getId();
    }

    private UUID confirmedProposal(UUID... members) {
        UUID proposalId = UUID.randomUUID();
        jdbc.sql("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) "
                        + "VALUES (:id, 'REALTIME', 'CONFIRMED', now() + interval '1 minute', now())")
                .param("id", proposalId)
                .update();
        for (UUID member : members) {
            jdbc.sql("INSERT INTO proposal_members (proposal_id, user_id, source_request_id, acceptance) "
                            + "VALUES (:proposalId, :userId, :requestId, 'ACCEPTED')")
                    .param("proposalId", proposalId)
                    .param("userId", member)
                    .param("requestId", UUID.randomUUID())
                    .update();
        }
        return proposalId;
    }
}
