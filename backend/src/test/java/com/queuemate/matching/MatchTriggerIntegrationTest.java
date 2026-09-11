package com.queuemate.matching;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequestStatus;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 매칭이 주기 tick이 아니라 대기열이 변한 순간 돈다는 것 (docs/03 §11).
 *
 * <p>여기서는 매처를 직접 부르지 않는다. 부르면 검증할 것이 남지 않는다.
 * 안전망 sweep은 꺼 둔다. 켜 두면 trigger가 고장나도 sweep이 대신 매칭해 테스트가 통과한다.
 */
@Testcontainers(disabledWithoutDocker = true)
// 애플리케이션에 WebSocket 엔드포인트가 있어 실제 서블릿 컨테이너가 필요하다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchTriggerIntegrationTest {

    private static final String LOL_MODE = "SOLO_DUO_RANKED";

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
        // 안전망이 대신 일해 주면 trigger가 도는지 알 수 없다. 사실상 끈다.
        registry.add("queuemate.matching.sweep-ms", () -> 3_600_000);
        registry.add("queuemate.proposal.sweep-ms", () -> 3_600_000);
    }

    @Autowired MatchRequestService matchRequests;
    @Autowired ProposalService proposals;
    @Autowired MatchRequestRepository requestRepository;
    @Autowired MatchProposalRepository proposalRepository;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE proposal_members, match_proposals, match_requests, blocks, users CASCADE")
                .update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("정원을 채우는 사람이 들어오면 아무도 부르지 않아도 제안이 생긴다")
    void queueingTriggersMatching() {
        matchRequests.start(newUser(), lol(LolPosition.JUNGLE));
        matchRequests.start(newUser(), lol(LolPosition.MID));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(proposalRepository.findAll()).hasSize(1));
    }

    @Test
    @DisplayName("혼자 기다리는 동안에는 제안을 만들지 않는다")
    void doesNotMatchASingleWaiter() {
        matchRequests.start(newUser(), lol(LolPosition.JUNGLE));

        // 잠깐 기다려도 생기지 않아야 한다. 정원을 못 채운 파티가 확정되면 INV-3이 깨진다.
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(proposalRepository.findAll()).isEmpty());
    }

    @Test
    @DisplayName("상대와 조건이 맞지 않으면 들어와도 제안이 생기지 않는다")
    void doesNotMatchIncompatibleWaiters() {
        // 음성 요구가 정면 충돌한다. 어떤 등급으로도 같은 파티가 될 수 없다 (docs/03 §4).
        matchRequests.start(newUser(), lol(LolPosition.JUNGLE, VoicePreference.REQUIRED));
        matchRequests.start(newUser(), lol(LolPosition.MID, VoicePreference.NO_VOICE));

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(proposalRepository.findAll()).isEmpty());
    }

    @Test
    @DisplayName("거절로 큐에 돌아온 사람도 아무도 부르지 않은 채 다시 매칭된다")
    void requeuedWaitersAreMatchedAgain() {
        // 제안이 깨져 큐로 돌아가는 것도 대기열이 변한 것이다. 여기서 trigger를 걸지 않으면
        // 돌아온 사람들은 다음 대기자가 올 때까지 아무도 보지 않는다 (docs/03 §8).
        UUID jungler = newUser();
        UUID mid = newUser();
        matchRequests.start(jungler, lol(LolPosition.JUNGLE));
        matchRequests.start(mid, lol(LolPosition.MID));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(pendingProposals()).hasSize(1));

        UUID first = pendingProposals().get(0);
        proposals.decline(mid, first);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(pendingProposals()).hasSize(1).doesNotContain(first);
            assertThat(requestRepository
                    .findAllByStatusOrderByQueuedAtAsc(MatchRequestStatus.QUEUED)).isEmpty();
        });
    }

    private List<UUID> pendingProposals() {
        return proposalRepository.findAll().stream()
                .filter(proposal -> proposal.getStatus() == ProposalStatus.PENDING)
                .map(com.queuemate.matching.domain.MatchProposal::getId)
                .toList();
    }

    private UUID newUser() {
        int n = sequence.incrementAndGet();
        return users.save(User.create("trigger" + n + "@queuemate.test", "hash", "trigger" + n))
                .getId();
    }

    private static MatchCondition lol(LolPosition position) {
        return lol(position, VoicePreference.OPTIONAL);
    }

    private static MatchCondition lol(LolPosition position, VoicePreference voice) {
        return new MatchCondition(GameKey.LOL, LOL_MODE, position, voice, PlayPurpose.RANK_UP);
    }
}
