package com.queuemate.matching.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.gameconfig.domain.GameModeConfig;
import com.queuemate.matching.domain.BucketDepth;
import com.queuemate.matching.domain.BucketScanPlan;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-1 검증(한 사용자는 활성 실시간 매칭 요청을 1개만 가진다)과
 * 조건별 bucket 대기열의 읽기 동작 (docs/07 §3).
 */
class MatchQueueRepositoryTest extends RedisTestSupport {

    private static final GameModeConfig CONFIG =
            new GameModeConfig(GameKey.LOL, "SOLO_DUO_RANKED", 2, true, true);

    /** 같은 조건끼리 묶이는지 보려면 서로 다른 bucket이 최소 둘 필요하다. */
    private static final MatchBucket TOP = bucket(LolPosition.TOP, VoicePreference.OPTIONAL);
    private static final MatchBucket MID = bucket(LolPosition.MID, VoicePreference.OPTIONAL);

    private final String topKey = MatchingRedisKeys.queue(TOP);
    private final Map<UUID, UUID> userByRequest = new HashMap<>();

    private MatchQueueRepository repository;

    @BeforeEach
    void createRepository() {
        repository = new MatchQueueRepository(redis);
        userByRequest.clear();
    }

    @Test
    @DisplayName("첫 요청은 guard를 잡고 자기 조건의 bucket에 들어간다")
    void acquiresGuardAndEnqueues() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        boolean acquired = repository.acquire(userId, requestId, topKey, Instant.now());

        assertThat(acquired).isTrue();
        assertThat(repository.activeRequestOf(userId)).contains(requestId);
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isEqualTo(1);
        assertThat(repository.bucketDepths(MatchBucket.allFor(CONFIG)))
                .extracting(BucketDepth::bucket)
                .containsExactly(TOP);
    }

    @Test
    @DisplayName("이미 매칭 중인 사용자의 두 번째 요청은 거부되고 대기열도 늘지 않는다")
    void rejectsSecondRequestOfSameUser() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.acquire(userId, first, topKey, Instant.now());

        boolean acquired = repository.acquire(userId, second, topKey, Instant.now());

        assertThat(acquired).isFalse();
        assertThat(repository.activeRequestOf(userId)).contains(first);
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isEqualTo(1);
    }

    @Test
    @DisplayName("INV-1: 같은 사용자가 100번 동시에 요청해도 정확히 하나만 통과한다")
    void onlyOneConcurrentRequestSucceeds() throws Exception {
        int attempts = 100;
        UUID userId = UUID.randomUUID();
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);

        // 전원이 start를 함께 기다려야 하므로 요청 수만큼 동시에 살아 있는 스레드가 필요하다.
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < attempts; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (repository.acquire(userId, UUID.randomUUID(), topKey, Instant.now())) {
                            succeeded.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                // 검증이 실패해도 워커를 풀어 준다. 안 그러면 pool.close()가 영원히 기다린다.
                start.countDown();
            }
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isEqualTo(1);
    }

    @Test
    @DisplayName("취소하면 guard와 대기열 항목이 함께 사라진다")
    void releasesGuardAndDequeues() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        repository.acquire(userId, requestId, topKey, Instant.now());

        boolean released = repository.release(userId, requestId, topKey);

        assertThat(released).isTrue();
        assertThat(repository.activeRequestOf(userId)).isEmpty();
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isZero();
    }

    @Test
    @DisplayName("늦게 도착한 취소가 새로 등록한 요청을 지우지 않는다")
    void releaseDoesNotStealNewerRequest() {
        UUID userId = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        repository.acquire(userId, stale, topKey, Instant.now());
        repository.release(userId, stale, topKey);
        repository.acquire(userId, current, topKey, Instant.now());

        // 사용자가 취소한 줄 알고 뒤늦게 도착한 해제 요청.
        boolean released = repository.release(userId, stale, topKey);

        assertThat(released).isFalse();
        assertThat(repository.activeRequestOf(userId)).contains(current);
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isEqualTo(1);
    }

    @Test
    @DisplayName("bucket 안에서는 오래 기다린 순으로 나온다")
    void returnsOldestFirstWithinBucket() {
        Instant base = Instant.parse("2026-08-31T00:00:00Z");
        UUID oldest = enqueueAt(TOP, base);
        UUID middle = enqueueAt(TOP, base.plusSeconds(60));
        UUID newest = enqueueAt(TOP, base.plusSeconds(120));

        assertThat(requestIdsOf(TOP, scan(50))).containsExactly(oldest, middle, newest);
    }

    @Test
    @DisplayName("bucket의 최고참 시각이 aging 기준으로 나온다")
    void reportsOldestQueuedAtPerBucket() {
        Instant base = Instant.parse("2026-08-31T00:00:00Z");
        enqueueAt(MID, base.plusSeconds(120));
        enqueueAt(TOP, base);
        enqueueAt(TOP, base.plusSeconds(60));

        assertThat(repository.bucketDepths(MatchBucket.allFor(CONFIG)))
                .filteredOn(depth -> depth.bucket().equals(TOP))
                .singleElement()
                .satisfies(depth -> {
                    assertThat(depth.oldestQueuedAt()).isEqualTo(base.toEpochMilli());
                    assertThat(depth.waiting()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("한 bucket이 예산을 다 먹어도 다른 bucket의 대기자가 보인다")
    void oneCrowdedBucketDoesNotHideOthers() {
        Instant base = Instant.parse("2026-08-31T00:00:00Z");
        // 같은 포지션끼리는 role uniqueness 때문에 서로 매칭될 수 없다.
        // 예전 구조(모드당 ZSET 하나)에서는 이들이 scan 창을 채워 뒤를 가렸다.
        for (int i = 0; i < 60; i++) {
            enqueueAt(TOP, base.plusSeconds(i));
        }
        UUID buriedDeep = enqueueAt(MID, base.plusSeconds(600));

        List<MatchQueueRepository.BucketSlice> slices = scan(50);

        assertThat(requestIdsOf(MID, slices)).contains(buriedDeep);
    }

    @Test
    @DisplayName("proposal을 거절하고 큐로 돌아와도 최초 대기 시각이 보존된다")
    void keepsOriginalQueuedAtOnRequeue() {
        Instant base = Instant.parse("2026-08-31T00:00:00Z");
        UUID waitingLong = enqueueAt(TOP, base);
        UUID returning = enqueueAt(TOP, base.plusSeconds(30));
        UUID joinedLater = enqueueAt(TOP, base.plusSeconds(60));

        // 거절 후 복귀. 최초 시각을 그대로 넘긴다 (docs/03 §8).
        UUID returningUser = userByRequest.get(returning);
        repository.release(returningUser, returning, topKey);
        repository.acquire(returningUser, returning, topKey, base.plusSeconds(30));

        assertThat(requestIdsOf(TOP, scan(50)))
                .containsExactly(waitingLong, returning, joinedLater);
    }

    @Test
    @DisplayName("대기열에 섞인 손상 항목이 그 큐의 매칭 전체를 멈추게 하지 않는다")
    void skipsAndCleansCorruptedEntries() {
        UUID valid = enqueueAt(TOP, Instant.parse("2026-08-31T00:01:00Z"));
        // 운영 도구나 예전 형식이 남긴 값을 흉내 낸다.
        redis.opsForZSet().add(topKey, "not-a-uuid", 1000);

        assertThat(requestIdsOf(TOP, scan(50))).containsExactly(valid);
        // 다시 걸리지 않도록 지운다.
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isEqualTo(1);
    }

    @Test
    @DisplayName("끝난 요청은 읽어 온 bucket에서 지운다")
    void removesStaleFromItsBucket() {
        UUID stale = enqueueAt(TOP, Instant.parse("2026-08-31T00:01:00Z"));
        UUID alive = enqueueAt(MID, Instant.parse("2026-08-31T00:02:00Z"));

        repository.removeStale(TOP, List.of(stale));

        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isEqualTo(1);
        assertThat(requestIdsOf(MID, scan(50))).containsExactly(alive);
    }

    @Test
    @DisplayName("빈 대기열은 빈 목록을 준다")
    void emptyQueueReturnsEmptyList() {
        assertThat(repository.bucketDepths(MatchBucket.allFor(CONFIG))).isEmpty();
        assertThat(scan(50)).isEmpty();
        assertThat(repository.waitingCount(MatchBucket.allFor(CONFIG))).isZero();
    }

    private List<MatchQueueRepository.BucketSlice> scan(int budget) {
        List<MatchBucket> all = MatchBucket.allFor(CONFIG);
        return repository.waitingOldestFirst(
                BucketScanPlan.of(repository.bucketDepths(all), CONFIG, budget));
    }

    private static List<UUID> requestIdsOf(
            MatchBucket bucket, List<MatchQueueRepository.BucketSlice> slices) {
        return slices.stream()
                .filter(slice -> slice.bucket().equals(bucket))
                .findFirst()
                .map(MatchQueueRepository.BucketSlice::requestIds)
                .orElse(List.of());
    }

    private static MatchBucket bucket(LolPosition position, VoicePreference voice) {
        return new MatchBucket(GameKey.LOL, "SOLO_DUO_RANKED", position, voice,
                PlayPurpose.RANK_UP);
    }

    private UUID enqueueAt(MatchBucket bucket, Instant queuedAt) {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        repository.acquire(userId, requestId, MatchingRedisKeys.queue(bucket), queuedAt);
        userByRequest.put(requestId, userId);
        return requestId;
    }
}
