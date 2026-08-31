package com.queuemate.matching.infra;

import com.queuemate.common.domain.GameKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INV-1 검증. 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다.
 */
class MatchQueueRepositoryTest extends RedisTestSupport {

    private final String queueKey = MatchingRedisKeys.queue(GameKey.LOL, "SOLO_DUO_RANKED");
    private final Map<UUID, UUID> userByRequest = new HashMap<>();

    private MatchQueueRepository repository;

    @BeforeEach
    void createRepository() {
        repository = new MatchQueueRepository(redis);
        userByRequest.clear();
    }

    @Test
    @DisplayName("첫 요청은 guard를 잡고 대기열에 들어간다")
    void acquiresGuardAndEnqueues() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        boolean acquired = repository.acquire(userId, requestId, queueKey, Instant.now());

        assertThat(acquired).isTrue();
        assertThat(repository.activeRequestOf(userId)).contains(requestId);
        assertThat(repository.waitingCount(queueKey)).isEqualTo(1);
        assertThat(repository.waitingOldestFirst(queueKey, 10)).containsExactly(requestId);
    }

    @Test
    @DisplayName("이미 매칭 중인 사용자의 두 번째 요청은 거부되고 대기열도 늘지 않는다")
    void rejectsSecondRequestOfSameUser() {
        UUID userId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.acquire(userId, first, queueKey, Instant.now());

        boolean acquired = repository.acquire(userId, second, queueKey, Instant.now());

        assertThat(acquired).isFalse();
        assertThat(repository.activeRequestOf(userId)).contains(first);
        assertThat(repository.waitingCount(queueKey)).isEqualTo(1);
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
                        if (repository.acquire(userId, UUID.randomUUID(), queueKey, Instant.now())) {
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
        assertThat(repository.waitingCount(queueKey)).isEqualTo(1);
    }

    @Test
    @DisplayName("취소하면 guard와 대기열 항목이 함께 사라진다")
    void releasesGuardAndDequeues() {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        repository.acquire(userId, requestId, queueKey, Instant.now());

        boolean released = repository.release(userId, requestId, queueKey);

        assertThat(released).isTrue();
        assertThat(repository.activeRequestOf(userId)).isEmpty();
        assertThat(repository.waitingCount(queueKey)).isZero();
    }

    @Test
    @DisplayName("늦게 도착한 취소가 새로 등록한 요청을 지우지 않는다")
    void releaseDoesNotStealNewerRequest() {
        UUID userId = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        repository.acquire(userId, stale, queueKey, Instant.now());
        repository.release(userId, stale, queueKey);
        repository.acquire(userId, current, queueKey, Instant.now());

        // 사용자가 취소한 줄 알고 뒤늦게 도착한 해제 요청.
        boolean released = repository.release(userId, stale, queueKey);

        assertThat(released).isFalse();
        assertThat(repository.activeRequestOf(userId)).contains(current);
        assertThat(repository.waitingCount(queueKey)).isEqualTo(1);
    }

    @Test
    @DisplayName("대기열은 오래 기다린 순으로 나온다")
    void returnsOldestFirst() {
        Instant base = Instant.parse("2026-08-31T00:00:00Z");
        UUID oldest = enqueueAt(base);
        UUID middle = enqueueAt(base.plusSeconds(60));
        UUID newest = enqueueAt(base.plusSeconds(120));

        assertThat(repository.waitingOldestFirst(queueKey, 10))
                .containsExactly(oldest, middle, newest);
        assertThat(repository.waitingOldestFirst(queueKey, 2))
                .containsExactly(oldest, middle);
    }

    @Test
    @DisplayName("proposal을 거절하고 큐로 돌아와도 최초 대기 시각이 보존된다")
    void keepsOriginalQueuedAtOnRequeue() {
        Instant base = Instant.parse("2026-08-31T00:00:00Z");
        UUID waitingLong = enqueueAt(base);
        UUID returning = enqueueAt(base.plusSeconds(30));
        UUID joinedLater = enqueueAt(base.plusSeconds(60));

        // 거절 후 복귀. 최초 시각을 그대로 넘긴다 (docs/03 §8).
        UUID returningUser = userByRequest.get(returning);
        repository.release(returningUser, returning, queueKey);
        repository.acquire(returningUser, returning, queueKey, base.plusSeconds(30));

        assertThat(repository.waitingOldestFirst(queueKey, 10))
                .containsExactly(waitingLong, returning, joinedLater);
    }

    @Test
    @DisplayName("limit이 0 이하면 거부한다")
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> repository.waitingOldestFirst(queueKey, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 대기열은 빈 목록을 준다")
    void emptyQueueReturnsEmptyList() {
        assertThat(repository.waitingOldestFirst(queueKey, 10)).isEmpty();
        assertThat(repository.waitingCount(queueKey)).isZero();
    }

    private UUID enqueueAt(Instant queuedAt) {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        repository.acquire(userId, requestId, queueKey, queuedAt);
        userByRequest.put(requestId, userId);
        return requestId;
    }
}
