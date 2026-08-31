package com.queuemate.matching;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.app.RealtimeMatcher;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchingRedisKeys;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제안 응답이 동시에 도착하는 경우 (docs/08 §2 INV-5 계열).
 *
 * <p>여기서 보려는 것은 "확정이 안 되는" 반대 방향의 실패다. 두 사람이 같은 순간에
 * 수락하면 서로의 변경을 보지 못해 아무도 확정하지 않을 수 있다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ProposalRaceIntegrationTest {

    private static final String MODE = "SOLO_DUO_RANKED";

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
        registry.add("queuemate.matching.tick-ms", () -> 3_600_000);
        registry.add("queuemate.proposal.sweep-ms", () -> 3_600_000);
        registry.add("queuemate.reservation.sweep-ms", () -> 3_600_000);
    }

    @Autowired MatchRequestService matchRequests;
    @Autowired RealtimeMatcher matcher;
    @Autowired ProposalService proposals;
    @Autowired MatchProposalRepository proposalRepository;
    @Autowired MatchQueueRepository queue;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE proposal_members, match_proposals, match_requests, users CASCADE").update();
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    @DisplayName("INV-4: 두 사람이 같은 순간에 수락해도 파티는 확정된다")
    void concurrentAcceptStillConfirms() throws Exception {
        for (int round = 0; round < 5; round++) {
            reset();
            UUID first = newUser();
            UUID second = newUser();
            matchRequests.start(first, lol(LolPosition.JUNGLE));
            matchRequests.start(second, lol(LolPosition.MID));
            UUID proposalId = matcher.tryMatch(GameKey.LOL, MODE).orElseThrow();

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (UUID user : List.of(first, second)) {
                    pool.execute(() -> {
                        try {
                            start.await();
                            proposals.accept(user, proposalId);
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            }

            assertThat(proposalRepository.findById(proposalId))
                    .withFailMessage("round %d: 두 사람이 모두 수락했는데 확정되지 않았다 (실패=%s)",
                            round, failure.get())
                    .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.CONFIRMED);
        }
    }

    @Test
    @DisplayName("수락과 거절이 동시에 오면 최종 상태는 하나뿐이다")
    void concurrentAcceptAndDeclineSettleOnOneState() throws Exception {
        UUID accepter = newUser();
        UUID decliner = newUser();
        matchRequests.start(accepter, lol(LolPosition.JUNGLE));
        matchRequests.start(decliner, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, MODE).orElseThrow();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            pool.execute(() -> {
                try {
                    start.await();
                    proposals.accept(accepter, proposalId);
                } catch (Exception ignored) {
                    // 상대가 먼저 거절했으면 409다. 정상 경로.
                } finally {
                    done.countDown();
                }
            });
            pool.execute(() -> {
                try {
                    start.await();
                    proposals.decline(decliner, proposalId);
                } catch (Exception ignored) {
                    // 이미 확정됐으면 409다. 정상 경로.
                } finally {
                    done.countDown();
                }
            });
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        ProposalStatus status = proposalRepository.findById(proposalId).orElseThrow().getStatus();
        assertThat(status)
                .withFailMessage("PENDING으로 남으면 아무도 처리하지 않은 것이다: %s", status)
                .isIn(ProposalStatus.CONFIRMED, ProposalStatus.DECLINED);
    }

    @Test
    @DisplayName("큐 맨 앞의 매칭 불가능한 사람이 뒤의 매칭을 막지 않는다")
    void headOfLineDoesNotBlockOthers() {
        // 가장 오래 기다린 사람은 아무와도 맞지 않는다.
        matchRequests.start(newUser(), new MatchCondition(GameKey.LOL, MODE,
                LolPosition.JUNGLE, VoicePreference.NO_VOICE, PlayPurpose.RANK_UP));
        // 뒤의 두 사람은 서로 잘 맞는다.
        matchRequests.start(newUser(), new MatchCondition(GameKey.LOL, MODE,
                LolPosition.TOP, VoicePreference.REQUIRED, PlayPurpose.RANK_UP));
        matchRequests.start(newUser(), new MatchCondition(GameKey.LOL, MODE,
                LolPosition.MID, VoicePreference.REQUIRED, PlayPurpose.RANK_UP));

        assertThat(matcher.tryMatch(GameKey.LOL, MODE))
                .withFailMessage("맨 앞 사람이 매칭되지 않는다고 뒤의 조합까지 막혔다")
                .isPresent();
        assertThat(queue.waitingCount(MatchingRedisKeys.queue(GameKey.LOL, MODE))).isEqualTo(1);
    }

    private UUID newUser() {
        int n = sequence.incrementAndGet();
        return users.save(User.create("race" + n + "-" + UUID.randomUUID() + "@queuemate.test",
                "hash", "race" + n + sequence.get())).getId();
    }

    private static MatchCondition lol(LolPosition position) {
        return new MatchCondition(GameKey.LOL, MODE, position,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    }
}
