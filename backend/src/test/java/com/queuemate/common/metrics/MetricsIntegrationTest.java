package com.queuemate.common.metrics;

import com.queuemate.auth.api.AuthDtos.SignupRequest;
import com.queuemate.common.error.ConflictException;
import com.queuemate.party.service.PartyDepartureService;
import com.queuemate.party.service.PartyService;
import com.queuemate.realtime.presence.DeparturePendingStore;
import com.queuemate.realtime.presence.DepartureSweeper;
import com.queuemate.realtime.presence.PresenceReconciler;
import com.queuemate.social.service.BlockService;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 노트마다 적어 둔 되돌아볼 조건은 전부 이런 게 보이면으로 끝난다.
 * 볼 수단이 실제로 있는지 확인한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MetricsIntegrationTest {

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

    @Autowired MeterRegistry meters;
    @Autowired PartyService partyService;
    @Autowired PartyDepartureService departures;
    @Autowired DeparturePendingStore pending;
    @Autowired DepartureSweeper sweeper;
    @Autowired PresenceReconciler reconciler;
    @Autowired BlockService blockService;
    @Autowired UserRepository users;
    @Autowired TestRestTemplate http;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE party_members, parties, proposal_members, match_proposals, "
                + "blocks, friendships, friend_requests, users CASCADE").update();
        // 앞 테스트가 남긴 예약과 접속 기록이 다음 테스트의 수를 바꾼다.
        redis.delete("qm:party:departure-due");
        redis.delete(redis.keys("qm:ws:presence:*"));
        redis.delete(redis.keys("qm:rate:*"));
    }

    @Test
    void 불변식_방어가_발동하면_지표에_잡힌다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID proposalId = confirmedProposal(a, b);
        blockService.block(a, b);
        double before = counter("queuemate.invariant.violation", "invariant", "BLOCKED_MEMBERS");

        assertThrows(ConflictException.class,
                () -> partyService.createFromProposal(proposalId, "LOL", "SOLO_DUO", 2, null));

        assertEquals(before + 1,
                counter("queuemate.invariant.violation", "invariant", "BLOCKED_MEMBERS"));
    }

    @Test
    void 평범한_충돌은_불변식_위반으로_세지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID c = user("charlie");
        UUID partyId = partyService.createFromProposal(
                confirmedProposal(a, b, c), "PUBG", "SQUAD", 3, null);
        departures.leave(partyId, a);
        double before = totalViolations();

        // 나간 사람이 준비를 바꾸려는 것은 충돌이지 불변식 위반이 아니다.
        // 여기까지 세면 docs/09의 Sev-1 규칙이 아무 의미가 없어진다.
        assertThrows(ConflictException.class, () -> partyService.changeReady(partyId, a, true));

        assertEquals(before, totalViolations());
    }

    @Test
    void 파티_종료가_사유별로_잡힌다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = partyService.createFromProposal(
                confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);
        double before = counter("queuemate.party.closed", "reason", "MEMBER_LEFT");

        departures.leave(partyId, a);

        assertEquals(before + 1, counter("queuemate.party.closed", "reason", "MEMBER_LEFT"));
    }

    @Test
    void 이탈_유예를_어느_단계로_줬는지_남는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = partyService.createFromProposal(
                confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);
        partyService.changeReady(partyId, a, true);
        partyService.changeReady(partyId, b, true);
        double before = counter("queuemate.departure.grace", "status", "READY");

        reconciler.reconcile();

        // 어느 단계가 실제로 쓰이는지가 유예를 나눈 판단이 맞는지 보는 유일한 창이다.
        assertTrue(counter("queuemate.departure.grace", "status", "READY") > before);
    }

    @Test
    void 대조가_찾은_수가_지표에_남는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        partyService.createFromProposal(confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);
        double before = counter("queuemate.presence.reconcile.found");

        reconciler.reconcile();

        // 이 값이 0이 아니라는 것 자체가 정상 경로의 실패를 뜻한다.
        assertEquals(before + 2, counter("queuemate.presence.reconcile.found"));
    }

    @Test
    void 내보낸_수가_지표에_남는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        partyService.createFromProposal(confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);
        pending.schedule(a, Duration.ZERO);
        double before = counter("queuemate.departure.evicted");

        sweeper.sweep();

        assertEquals(before + 1, counter("queuemate.departure.evicted"));
    }

    @Test
    void 한도_거절이_scope별로_잡힌다() {
        double before = counter("queuemate.ratelimit.rejected", "scope", "signup:ip:burst");

        for (int i = 0; i < 6; i++) {
            String tag = UUID.randomUUID().toString().substring(0, 6);
            http.postForEntity("/api/v1/auth/signup",
                    new SignupRequest(tag + "@queuemate.dev", "password12", tag), String.class);
        }

        // 정상 사용자가 한도에 걸리는지 보는 유일한 창이다.
        assertEquals(before + 1,
                counter("queuemate.ratelimit.rejected", "scope", "signup:ip:burst"));
    }

    @Test
    void 열린_session_수가_게이지로_노출된다() {
        assertNotNull(meters.find("queuemate.ws.sessions").gauge(),
                "늘고 주는 값이라 카운터로는 의미가 없다");
    }

    @Test
    void 태그에_개별_식별자를_넣지_않는다() {
        UUID a = user("alpha");
        UUID b = user("bravo");
        UUID partyId = partyService.createFromProposal(
                confirmedProposal(a, b), "LOL", "SOLO_DUO", 2, null);
        departures.leave(partyId, a);
        reconciler.reconcile();

        // userId나 partyId를 태그로 넣으면 시계열이 무한히 늘어난다.
        // 지표 저장소를 죽이는 가장 흔한 방법이고, 늘어난 뒤에는 되돌리기 어렵다.
        Set<String> allowed = Set.of("invariant", "reason", "status", "scope", "policy", "origin");
        for (Meter meter : meters.getMeters()) {
            if (!meter.getId().getName().startsWith("queuemate.")) {
                continue;
            }
            for (Tag tag : meter.getId().getTags()) {
                assertTrue(allowed.contains(tag.getKey()),
                        meter.getId().getName() + "에 예상 밖 태그가 있다: " + tag.getKey());
                assertTrue(tag.getValue().length() < 40,
                        meter.getId().getName() + "의 태그 값이 식별자처럼 보인다: " + tag.getValue());
            }
        }
    }

    // ---- helpers ----

    private double totalViolations() {
        return meters.find("queuemate.invariant.violation").counters().stream()
                .mapToDouble(Counter::count).sum();
    }

    private double counter(String name, String... tags) {
        Counter found = tags.length == 0
                ? meters.find(name).counter()
                : meters.find(name).tags(tags).counter();
        return found == null ? 0 : found.count();
    }

    private UUID user(String nickname) {
        String unique = UUID.randomUUID().toString().substring(0, 6);
        return users.save(User.create(nickname + unique + "@queuemate.dev", "hash",
                nickname + unique)).getId();
    }

    private UUID confirmedProposal(UUID... members) {
        UUID proposalId = UUID.randomUUID();
        jdbc.sql("INSERT INTO match_proposals (id, source_type, status, expires_at, confirmed_at) "
                        + "VALUES (:id, 'REALTIME', 'CONFIRMED', now() + interval '1 minute', :now)")
                .param("id", proposalId).param("now", OffsetDateTime.now()).update();
        for (UUID member : List.of(members)) {
            jdbc.sql("INSERT INTO proposal_members (proposal_id, user_id, source_request_id, acceptance) "
                            + "VALUES (:p, :u, :r, 'ACCEPTED')")
                    .param("p", proposalId).param("u", member).param("r", UUID.randomUUID()).update();
        }
        return proposalId;
    }
}
