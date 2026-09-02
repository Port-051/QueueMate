package com.queuemate.matching;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.app.RealtimeMatcher;
import com.queuemate.matching.domain.BlockedPairProposalGuard;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.ProposalStatus;
import com.queuemate.matching.infra.MatchProposalRepository;
import com.queuemate.common.error.ConflictException;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.ProposalClaimRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 제안 응답이 동시에 도착하는 경우 (docs/08 §2 INV-5 계열).
 *
 * <p>여기서 보려는 것은 "확정이 안 되는" 반대 방향의 실패다. 두 사람이 같은 순간에
 * 수락하면 서로의 변경을 보지 못해 아무도 확정하지 않을 수 있다.
 */
@Testcontainers(disabledWithoutDocker = true)
// 애플리케이션에 WebSocket 엔드포인트가 있어 실제 서블릿 컨테이너가 필요하다.
// MOCK 환경에는 jakarta.websocket의 ServerContainer가 없어 컨텍스트가 뜨지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    @Autowired ProposalClaimRepository claims;
    @Autowired BlockedPairProposalGuard blockGuard;
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

    @Test
    @DisplayName("만료된 제안을 수락해도 Redis 잠금이 제멋대로 풀리지 않는다")
    void acceptingExpiredProposalDoesNotStripClaims() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, MODE).orElseThrow();

        // 기한이 지난 상태를 만든다.
        jdbc.sql("UPDATE match_proposals SET expires_at = now() - interval '1 minute' WHERE id = ?")
                .param(proposalId).update();

        assertThatThrownBy(() -> proposals.accept(first, proposalId))
                .isInstanceOf(ConflictException.class);

        // 예외가 트랜잭션을 되돌린다. DB가 그대로면 Redis 잠금도 그대로여야 한다.
        // 여기서 잠금만 풀리면 두 사람이 다른 제안에 또 잡혀 INV-2가 깨진다.
        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(claims.activeProposalOf(first)).contains(proposalId);
        assertThat(claims.activeProposalOf(second)).contains(proposalId);

        // 정리는 별도 트랜잭션인 sweep이 맡는다.
        assertThat(proposals.expireOverdue()).isEqualTo(1);
        assertThat(claims.activeProposalOf(first)).isEmpty();
        assertThat(queue.waitingCount(MatchingRedisKeys.queue(GameKey.LOL, MODE))).isEqualTo(2);
    }

    @Test
    @DisplayName("대기열에 남은 끝난 요청이 scan 창을 잠식하지 않는다")
    void staleQueueEntriesAreEvicted() {
        UUID cancelled = newUser();
        UUID requestId = matchRequests.start(cancelled, lol(LolPosition.TOP)).getId();
        // Redis 항목만 남기고 DB에서는 끝난 요청으로 만든다.
        jdbc.sql("UPDATE match_requests SET status = 'CANCELLED' WHERE id = ?")
                .param(requestId).update();
        matchRequests.start(newUser(), lol(LolPosition.JUNGLE));
        matchRequests.start(newUser(), lol(LolPosition.MID));

        assertThat(matcher.tryMatch(GameKey.LOL, MODE)).isPresent();
        // 끝난 항목은 대기열에서도 사라진다.
        assertThat(queue.waitingCount(MatchingRedisKeys.queue(GameKey.LOL, MODE))).isZero();
    }

    @Test
    @DisplayName("INV-6: 제안이 떠 있는 사이 차단이 생기면 그 제안을 닫는다")
    void blockingDuringPendingProposalClosesIt() {
        UUID first = newUser();
        UUID second = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, MODE).orElseThrow();

        // 후보 탐색과 잠금 사이가 아니라, 이미 제안이 만들어진 뒤에 차단이 생긴 경우다.
        // 매칭 쪽 재검증만으로는 이 창을 막을 수 없어 차단을 만드는 쪽에서 닫는다.
        int closed = blockGuard.cancelSharedPendingProposals(first, second);

        assertThat(closed).isEqualTo(1);
        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.CANCELLED);
        // 두 사람 모두 대기로 돌아가되, 이제 서로는 후보가 아니다.
        assertThat(queue.waitingCount(MatchingRedisKeys.queue(GameKey.LOL, MODE))).isEqualTo(2);
        assertThat(claims.activeProposalOf(first)).isEmpty();
    }

    @Test
    @DisplayName("함께 있지 않은 두 사람을 차단해도 남의 제안은 건드리지 않는다")
    void blockingUnrelatedUsersLeavesProposalsAlone() {
        UUID first = newUser();
        UUID second = newUser();
        UUID outsider = newUser();
        matchRequests.start(first, lol(LolPosition.JUNGLE));
        matchRequests.start(second, lol(LolPosition.MID));
        UUID proposalId = matcher.tryMatch(GameKey.LOL, MODE).orElseThrow();

        assertThat(blockGuard.cancelSharedPendingProposals(first, outsider)).isZero();
        assertThat(proposalRepository.findById(proposalId))
                .get().extracting(p -> p.getStatus()).isEqualTo(ProposalStatus.PENDING);
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
