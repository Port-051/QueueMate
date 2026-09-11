package com.queuemate.matching;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.app.MatchQueueRecoveryService;
import com.queuemate.matching.app.MatchRequestService;
import com.queuemate.matching.app.RealtimeMatcher;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.matching.domain.BucketScanPlan;
import com.queuemate.matching.domain.MatchBucket;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 재시작 복구 드릴 (docs/07 §10, docs/08 CI gate).
 *
 * <p>Redis는 영속 진실이 아니다. 통째로 날아가도 DB의 활성 요청으로 대기열을 다시 세울 수 있어야 한다.
 */
@Testcontainers(disabledWithoutDocker = true)
// 애플리케이션에 WebSocket 엔드포인트가 있어 실제 서블릿 컨테이너가 필요하다.
// MOCK 환경에는 jakarta.websocket의 ServerContainer가 없어 컨텍스트가 뜨지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchQueueRecoveryIntegrationTest {

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
        registry.add("queuemate.matching.sweep-ms", () -> 3_600_000);
        // 매칭은 요청이 들어온 순간 저절로 돈다. 여기서는 매처를 직접 불러 한 판씩 본다.
        registry.add("queuemate.matching.auto-trigger", () -> false);
        registry.add("queuemate.proposal.sweep-ms", () -> 3_600_000);
        registry.add("queuemate.reservation.sweep-ms", () -> 3_600_000);
    }

    @Autowired MatchRequestService matchRequests;
    @Autowired MatchQueueRecoveryService recovery;
    @Autowired MatchQueueRepository queue;
    @Autowired RealtimeMatcher matcher;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcClient jdbc;

    private final AtomicInteger sequence = new AtomicInteger();

    @BeforeEach
    void reset() {
        jdbc.sql("TRUNCATE proposal_members, match_proposals, match_requests, users CASCADE").update();
        flushRedis();
    }

    @Test
    @DisplayName("Redis가 통째로 비워져도 DB의 활성 요청으로 대기열을 다시 세운다")
    void rebuildsQueueAfterRedisWipe() {
        UUID jungler = newUser();
        UUID mid = newUser();
        matchRequests.start(jungler, lol(LolPosition.JUNGLE));
        matchRequests.start(mid, lol(LolPosition.MID));
        assertThat(queue.waitingCount(buckets())).isEqualTo(2);

        flushRedis(); // Redis 재시작을 흉내 낸다
        assertThat(queue.waitingCount(buckets())).isZero();
        assertThat(matcher.tryMatch(GameKey.LOL, MODE)).isEmpty();

        MatchQueueRecoveryService.RecoveryReport report = recovery.rebuild();

        assertThat(report.restored()).isEqualTo(2);
        assertThat(report.conflicted()).isZero();
        assertThat(queue.waitingCount(buckets())).isEqualTo(2);
        assertThat(queue.activeRequestOf(jungler)).isPresent();
        // 복구 뒤에는 곧바로 매칭이 다시 돈다.
        assertThat(matcher.tryMatch(GameKey.LOL, MODE)).isPresent();
    }

    @Test
    @DisplayName("정상 상태에서 복구를 돌려도 아무것도 망가지지 않는다")
    void rebuildIsSafeWhenNothingIsLost() {
        UUID user = newUser();
        matchRequests.start(user, lol(LolPosition.JUNGLE));

        MatchQueueRecoveryService.RecoveryReport report = recovery.rebuild();

        assertThat(report.total()).isEqualTo(1);
        assertThat(report.restored()).isZero();
        assertThat(report.alreadyPresent()).isEqualTo(1);
        assertThat(report.conflicted()).isZero();
        assertThat(queue.waitingCount(buckets())).isEqualTo(1);
    }

    @Test
    @DisplayName("복구해도 오래 기다린 사람이 앞자리를 지킨다")
    void keepsAgingOrderAfterRecovery() {
        UUID first = newUser();
        UUID second = newUser();
        UUID firstRequest = matchRequests.start(first, lol(LolPosition.JUNGLE)).getId();
        UUID secondRequest = matchRequests.start(second, lol(LolPosition.MID)).getId();

        flushRedis();
        recovery.rebuild();

        assertThat(waitingOldestFirst()).containsExactly(firstRequest, secondRequest);
    }

    @Test
    @DisplayName("DB에는 끝났는데 Redis에만 남은 guard를 걷어 낸다")
    void reconcileRemovesStaleGuards() {
        UUID user = newUser();
        UUID requestId = matchRequests.start(user, lol(LolPosition.JUNGLE)).getId();
        // Redis 작업이 유실된 상황을 흉내 낸다. DB만 끝나고 guard가 남았다.
        jdbc.sql("UPDATE match_requests SET status = 'CANCELLED' WHERE id = ?")
                .param(requestId).update();
        assertThat(queue.activeRequestOf(user)).contains(requestId);

        MatchQueueRecoveryService.ReconcileReport report = recovery.reconcile();

        assertThat(report.staleGuards()).isEqualTo(1);
        assertThat(queue.activeRequestOf(user)).isEmpty();
        // guard가 풀렸으므로 다시 매칭을 시작할 수 있다.
        assertThat(matchRequests.start(user, lol(LolPosition.MID))).isNotNull();
    }

    @Test
    @DisplayName("살아 있는 guard는 정합성 정리가 건드리지 않는다")
    void reconcileKeepsHealthyGuards() {
        UUID user = newUser();
        UUID requestId = matchRequests.start(user, lol(LolPosition.JUNGLE)).getId();

        MatchQueueRecoveryService.ReconcileReport report = recovery.reconcile();

        assertThat(report.staleGuards()).isZero();
        assertThat(queue.activeRequestOf(user)).contains(requestId);
        assertThat(queue.waitingCount(buckets())).isEqualTo(1);
    }

    private void flushRedis() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    private UUID newUser() {
        int n = sequence.incrementAndGet();
        return users.save(User.create("rec" + n + "@queuemate.test", "hash", "rec" + n)).getId();
    }

    private static MatchCondition lol(LolPosition position) {
        return new MatchCondition(GameKey.LOL, MODE, position,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    }

    private static List<MatchBucket> buckets() {
        return MatchBucket.allFor(new GameModeConfig(GameKey.LOL, MODE, 2, true, true));
    }

    /**
     * bucket을 가로질러 대기 순으로 모은다.
     *
     * <p>대기열이 조건별로 나뉜 뒤로 "전체에서 몇 번째"는 Redis 한 자료구조가 알려 주지 않는다.
     * 복구가 최초 queuedAt(=score)을 지켰는지 보는 것이 이 도우미의 목적이므로 score로 정렬한다.
     */
    private List<UUID> waitingOldestFirst() {
        GameModeConfig config = new GameModeConfig(GameKey.LOL, MODE, 2, true, true);
        record Scored(UUID requestId, double queuedAt) {
        }
        List<Scored> scored = new ArrayList<>();
        for (MatchQueueRepository.BucketSlice slice : queue.waitingOldestFirst(
                BucketScanPlan.of(queue.bucketDepths(buckets()), config, 50))) {
            String key = MatchingRedisKeys.queue(slice.bucket());
            for (UUID requestId : slice.requestIds()) {
                Double score = redis.opsForZSet().score(key, requestId.toString());
                scored.add(new Scored(requestId, score == null ? Double.MAX_VALUE : score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::queuedAt));
        return scored.stream().map(Scored::requestId).toList();
    }
}
