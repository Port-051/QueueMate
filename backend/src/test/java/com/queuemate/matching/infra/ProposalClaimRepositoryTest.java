package com.queuemate.matching.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.matching.domain.ClaimCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INV-2 검증. 한 사용자는 동시에 하나의 활성 proposal에만 속한다.
 * Redis 정합성이 이 클래스에 걸려 있으므로 Testcontainers로 진짜 Redis에 붙는다.
 */
class ProposalClaimRepositoryTest extends RedisTestSupport {

    private static final Duration TTL = Duration.ofSeconds(20);

    private final String queueKey = MatchingRedisKeys.queue(GameKey.LOL, "SOLO_DUO_RANKED");

    private ProposalClaimRepository repository;

    @BeforeEach
    void createRepository() {
        repository = new ProposalClaimRepository(redis);
    }

    @Test
    @DisplayName("후보 전원이 대기 중이면 한 번에 잠기고 대기열에서 빠진다")
    void claimsEveryCandidateAtOnce() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);
        UUID proposalId = UUID.randomUUID();

        boolean claimed = repository.claimAll(queueKey, proposalId, TTL, List.of(a, b));

        assertThat(claimed).isTrue();
        assertThat(activeProposalOf(a)).isEqualTo(proposalId.toString());
        assertThat(activeProposalOf(b)).isEqualTo(proposalId.toString());
        assertThat(redis.opsForZSet().size(queueKey)).isZero();
        assertThat(redis.opsForSet().size(MatchingRedisKeys.proposalMembers(proposalId))).isEqualTo(2);
        assertThat(redis.getExpire(MatchingRedisKeys.activeProposal(a.userId())))
                .isPositive().isLessThanOrEqualTo(TTL.toSeconds());
        assertThat(redis.getExpire(MatchingRedisKeys.proposalMembers(proposalId)))
                .isPositive().isLessThanOrEqualTo(TTL.toSeconds());
    }

    @Test
    @DisplayName("한 명이 이미 다른 proposal에 묶여 있으면 아무도 잠기지 않는다")
    void leavesNoTraceWhenOneCandidateIsAlreadyClaimed() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);
        redis.opsForValue().set(MatchingRedisKeys.activeProposal(b.userId()), "other-proposal");
        UUID proposalId = UUID.randomUUID();

        boolean claimed = repository.claimAll(queueKey, proposalId, TTL, List.of(a, b));

        assertThat(claimed).isFalse();
        assertThat(activeProposalOf(a)).isNull();
        assertThat(activeProposalOf(b)).isEqualTo("other-proposal");
        assertThat(redis.opsForZSet().size(queueKey)).isEqualTo(2);
        assertThat(redis.hasKey(MatchingRedisKeys.proposalMembers(proposalId))).isFalse();
    }

    @Test
    @DisplayName("후보를 고른 뒤 사용자가 요청을 새로 넣었으면 claim이 실패한다")
    void failsWhenActiveRequestChanged() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);
        // 취소 후 재요청. 우리가 본 requestId는 더 이상 유효하지 않다.
        redis.opsForValue().set(MatchingRedisKeys.activeRequest(a.userId()), UUID.randomUUID().toString());

        boolean claimed = repository.claimAll(queueKey, UUID.randomUUID(), TTL, List.of(a, b));

        assertThat(claimed).isFalse();
        assertThat(activeProposalOf(a)).isNull();
        assertThat(activeProposalOf(b)).isNull();
    }

    @Test
    @DisplayName("사용자가 매칭을 취소해 활성 요청이 사라졌으면 claim이 실패한다")
    void failsWhenActiveRequestGone() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);
        redis.delete(MatchingRedisKeys.activeRequest(a.userId()));

        boolean claimed = repository.claimAll(queueKey, UUID.randomUUID(), TTL, List.of(a, b));

        assertThat(claimed).isFalse();
        assertThat(activeProposalOf(b)).isNull();
    }

    @Test
    @DisplayName("INV-2: 한 사용자를 여러 matcher가 동시에 잡아도 정확히 하나만 성공한다")
    void onlyOneConcurrentClaimSucceeds() throws Exception {
        int rivals = 50;
        ClaimCandidate target = enqueue(1000);
        List<ClaimCandidate> partners = new ArrayList<>();
        for (int i = 0; i < rivals; i++) {
            partners.add(enqueue(1001 + i));
        }

        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(rivals);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(rivals);

        // 전원이 start를 함께 기다려야 하므로 요청 수만큼 동시에 살아 있는 스레드가 필요하다.
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ClaimCandidate partner : partners) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (repository.claimAll(queueKey, UUID.randomUUID(), TTL, List.of(target, partner))) {
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
        assertThat(activeProposalOf(target)).isNotNull();
        // 승자 한 명 외에는 아무도 잠기지 않았다.
        long lockedPartners = partners.stream().filter(p -> activeProposalOf(p) != null).count();
        assertThat(lockedPartners).isEqualTo(1);
        // 대기열에서는 승자 두 명만 빠졌다.
        assertThat(redis.opsForZSet().size(queueKey)).isEqualTo(rivals + 1 - 2L);
    }

    @Test
    @DisplayName("끝난 제안을 정리해도 그 사이 다른 제안이 잡은 잠금은 건드리지 않는다")
    void releaseOnlyRemovesOwnClaims() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();

        // 새 제안이 두 사람을 잡고 있는 상태.
        assertThat(repository.claimAll(queueKey, newer, TTL, List.of(a, b))).isTrue();

        // TTL이 먼저 끝난 옛 제안의 뒤늦은 정리가 도착한다.
        long released = repository.releaseClaims(older, List.of(a.userId(), b.userId()));

        assertThat(released).isZero();
        assertThat(activeProposalOf(a)).isEqualTo(newer.toString());
        assertThat(activeProposalOf(b)).isEqualTo(newer.toString());
    }

    @Test
    @DisplayName("자기 제안의 잠금은 정리한다")
    void releaseRemovesOwnClaims() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);
        UUID proposalId = UUID.randomUUID();
        repository.claimAll(queueKey, proposalId, TTL, List.of(a, b));

        assertThat(repository.releaseClaims(proposalId, List.of(a.userId(), b.userId()))).isEqualTo(2);
        assertThat(activeProposalOf(a)).isNull();
        assertThat(redis.hasKey(MatchingRedisKeys.proposalMembers(proposalId))).isFalse();
    }

    @Test
    @DisplayName("같은 사용자를 두 번 담으면 거부한다")
    void rejectsDuplicateCandidate() {
        ClaimCandidate a = enqueue(1000);

        assertThatThrownBy(() -> repository.claimAll(queueKey, UUID.randomUUID(), TTL, List.of(a, a)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("두 번");
        assertThat(activeProposalOf(a)).isNull();
    }

    @Test
    @DisplayName("혼자서는 파티가 되지 않는다")
    void rejectsSingleCandidate() {
        ClaimCandidate a = enqueue(1000);

        assertThatThrownBy(() -> repository.claimAll(queueKey, UUID.randomUUID(), TTL, List.of(a)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("TTL 없는 영구 잠금을 만들지 않는다")
    void rejectsNonPositiveTtl() {
        ClaimCandidate a = enqueue(1000);
        ClaimCandidate b = enqueue(1001);

        assertThatThrownBy(() -> repository.claimAll(queueKey, UUID.randomUUID(), Duration.ZERO, List.of(a, b)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 대기 중인 사용자 한 명을 만든다. 활성 요청 guard와 대기열 항목을 함께 세운다. */
    private ClaimCandidate enqueue(double queuedAtScore) {
        ClaimCandidate candidate = new ClaimCandidate(UUID.randomUUID(), UUID.randomUUID());
        redis.opsForValue().set(
                MatchingRedisKeys.activeRequest(candidate.userId()), candidate.requestId().toString());
        redis.opsForZSet().add(queueKey, candidate.requestId().toString(), queuedAtScore);
        return candidate;
    }

    private String activeProposalOf(ClaimCandidate candidate) {
        return redis.opsForValue().get(MatchingRedisKeys.activeProposal(candidate.userId()));
    }
}
