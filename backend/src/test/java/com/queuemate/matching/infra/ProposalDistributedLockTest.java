package com.queuemate.matching.infra;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.matching.domain.ClaimCandidate;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchBucket;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/** 실제 Redis와 독립 연결 팩토리 두 개로 락·트랜잭션 경계의 경합을 검증한다. */
class ProposalDistributedLockTest extends RedisTestSupport {
    private static final Duration TTL = Duration.ofSeconds(20);
    private static final MatchBucket BUCKET = new MatchBucket(GameKey.LOL, "SOLO_DUO_RANKED",
            LolPosition.MID, VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);
    private LettuceConnectionFactory secondFactory;
    private StringRedisTemplate other;
    private GatedTemplate gated;
    private SimpleMeterRegistry metrics;
    private ProposalClaimRepository repository;

    @BeforeEach
    void independentClients() {
        var first = (LettuceConnectionFactory) redis.getConnectionFactory();
        secondFactory = new LettuceConnectionFactory(first.getHostName(), first.getPort());
        secondFactory.afterPropertiesSet();
        other = new StringRedisTemplate(secondFactory);
        gated = new GatedTemplate(first);
        metrics = new SimpleMeterRegistry();
        repository = new ProposalClaimRepository(gated, metrics, 10_000);
    }

    @AfterEach
    void closeClients() { secondFactory.destroy(); metrics.close(); }

    @Test
    void realtimeAndReservationCompeteForSameUserAcrossIndependentClients() throws Exception {
        var target = enqueue();
        var rival = new ProposalClaimRepository(other);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        List<UUID> proposals = new ArrayList<>();
        List<ClaimCandidate> partners = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 40; i++) {
                UUID proposal = UUID.randomUUID();
                proposals.add(proposal);
                ClaimCandidate partner = enqueue();
                partners.add(partner);
                boolean reservation = i % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    return reservation ? rival.claimAllForReservation(proposal, TTL, List.of(target.userId(), partner.userId()))
                            : repository.claimAll(proposal, TTL, List.of(target, partner));
                }));
            }
            start.countDown();
            int winners = 0;
            for (int i = 0; i < futures.size(); i++) {
                if (futures.get(i).get(20, TimeUnit.SECONDS)) {
                    winners++;
                    assertThat(other.opsForValue().get(MatchingRedisKeys.activeProposal(target.userId())))
                            .isEqualTo(proposals.get(i).toString());
                }
            }
            assertThat(winners).isEqualTo(1);
        }
        assertThat(partners.stream().filter(p -> other.hasKey(MatchingRedisKeys.activeProposal(p.userId()))).count()).isEqualTo(1);
        assertThat(other.keys("qm:lock:claim:*")).isEmpty();
    }

    @Test
    void partialLockAcquisitionDoesNotLeaveOwnMutexOrChangeClaims() {
        var members = List.of(enqueue(), enqueue());
        var keys = members.stream().map(c -> MatchingRedisKeys.claimLock(c.userId())).sorted().toList();
        other.opsForValue().set(keys.getLast(), "other", TTL);
        assertThat(repository.claimAll(UUID.randomUUID(), TTL, members)).isFalse();
        assertThat(other.opsForValue().get(keys.getLast())).isEqualTo("other");
        assertThat(other.hasKey(keys.getFirst())).isFalse();
        assertUnclaimed(members);
        assertThat(metrics.get("qm.matching.claim.lock.conflict").counter().count()).isEqualTo(1);
    }

    @Test
    void expiredHolderCannotCommitOrDeleteNewOwnersLock() {
        var members = List.of(enqueue(), enqueue());
        String key = MatchingRedisKeys.claimLock(members.getFirst().userId());
        gated.beforeExec = () -> {
            // WATCH 이후 임대가 끝나 새 소유자가 들어온 순간을 재현한다.
            other.expire(key, Duration.ZERO);
            assertThat(other.opsForValue().setIfAbsent(key, "new-owner", TTL)).isTrue();
        };
        assertThat(repository.claimAll(UUID.randomUUID(), TTL, members)).isFalse();
        assertUnclaimed(members);
        assertThat(other.opsForValue().get(key)).isEqualTo("new-owner");
        assertThat(metrics.get("qm.matching.claim.transaction.conflict").counter().count()).isEqualTo(1);
    }

    @Test
    void requestChangedAfterJavaValidationPreventsWholeClaim() {
        var members = List.of(enqueue(), enqueue());
        String guard = MatchingRedisKeys.activeRequest(members.getLast().userId());
        gated.beforeExec = () -> other.opsForValue().set(guard, UUID.randomUUID().toString());
        assertThat(repository.claimAll(UUID.randomUUID(), TTL, members)).isFalse();
        assertUnclaimed(members);
        assertThat(other.keys("qm:lock:claim:*")).isEmpty();
    }

    @Test
    void exceptionBeforeExecDiscardsWritesAndSessionCanBeReused() {
        var members = List.of(enqueue(), enqueue());
        gated.beforeExec = () -> { throw new RedisConnectionFailureException("injected before EXEC"); };
        assertThatThrownBy(() -> repository.claimAll(UUID.randomUUID(), TTL, members))
                .isInstanceOf(RedisConnectionFailureException.class);
        assertUnclaimed(members);
        assertThat(other.keys("qm:lock:claim:*")).isEmpty();
        gated.beforeExec = () -> {};
        assertThat(repository.claimAll(UUID.randomUUID(), TTL, members)).isTrue();
        assertThat(metrics.get("qm.matching.claim.duration").tag("outcome", "error").timer().count()).isEqualTo(1);
    }

    @Test
    void lostExecReplyFailsClosedEvenWhenRedisAppliedClaim() {
        var members = List.of(enqueue(), enqueue());
        UUID proposal = UUID.randomUUID();
        gated.afterExec = () -> { throw new RedisConnectionFailureException("injected lost EXEC reply"); };
        assertThatThrownBy(() -> repository.claimAll(proposal, TTL, members))
                .isInstanceOf(RedisConnectionFailureException.class);
        assertThat(other.opsForValue().get(MatchingRedisKeys.activeProposal(members.getFirst().userId())))
                .isEqualTo(proposal.toString());
        assertThat(other.getExpire(MatchingRedisKeys.activeProposal(members.getFirst().userId()))).isPositive();
        // 보이지 않는 성공으로 취급하지 않으며, 다른 제안도 아직 이 사용자를 얻지 못한다.
        assertThat(new ProposalClaimRepository(other).claimAllForReservation(UUID.randomUUID(), TTL,
                members.stream().map(ClaimCandidate::userId).toList())).isFalse();
    }

    @Test
    void unlockFailureDoesNotTurnCommittedClaimIntoFailure() {
        var members = List.of(enqueue(), enqueue());
        gated.failUnlock = true;
        UUID proposal = UUID.randomUUID();
        assertThat(repository.claimAll(proposal, TTL, members)).isTrue();
        assertThat(other.opsForValue().get(MatchingRedisKeys.activeProposal(members.getFirst().userId())))
                .isEqualTo(proposal.toString());
        assertThat(other.getExpire(MatchingRedisKeys.claimLock(members.getFirst().userId()))).isPositive();
        assertThat(metrics.get("qm.matching.claim.lock.release.failure").counter().count()).isEqualTo(1);
    }

    @Test
    void wrongQueueTypeFailsBeforeAnyParticipantIsClaimed() {
        var members = List.of(enqueue(), enqueue());
        other.opsForValue().set(MatchingRedisKeys.queue(BUCKET), "invalid type");
        assertThatThrownBy(() -> repository.claimAll(UUID.randomUUID(), TTL, members))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        for (var member : members) assertThat(other.hasKey(MatchingRedisKeys.activeProposal(member.userId()))).isFalse();
        assertThat(other.keys("qm:lock:claim:*")).isEmpty();
    }

    @Test
    void reservationUsesSameLeaseValidationAndRejectsDuplicateParticipants() {
        var users = List.of(UUID.randomUUID(), UUID.randomUUID());
        gated.beforeExec = () -> other.expire(MatchingRedisKeys.claimLock(users.getFirst()), Duration.ZERO);
        assertThat(repository.claimAllForReservation(UUID.randomUUID(), TTL, users)).isFalse();
        for (UUID user : users) assertThat(other.hasKey(MatchingRedisKeys.activeProposal(user))).isFalse();
        assertThatThrownBy(() -> repository.claimAllForReservation(UUID.randomUUID(), TTL, List.of(users.getFirst(), users.getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.claimAllForReservation(UUID.randomUUID(), Duration.ofNanos(1), users))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProposalClaimRepository(gated, metrics, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    private ClaimCandidate enqueue() {
        var candidate = new ClaimCandidate(UUID.randomUUID(), UUID.randomUUID(), BUCKET);
        other.opsForValue().set(MatchingRedisKeys.activeRequest(candidate.userId()), candidate.requestId().toString());
        other.opsForZSet().add(MatchingRedisKeys.queue(BUCKET), candidate.requestId().toString(), 1000);
        return candidate;
    }

    private void assertUnclaimed(List<ClaimCandidate> members) {
        for (var member : members) {
            assertThat(other.hasKey(MatchingRedisKeys.activeProposal(member.userId()))).isFalse();
            assertThat(other.opsForZSet().score(MatchingRedisKeys.queue(BUCKET), member.requestId().toString())).isEqualTo(1000.0);
        }
    }

    /** 실제 SessionCallback을 유지하고 EXEC 전후에 별도 클라이언트의 경합을 주입한다. */
    private static final class GatedTemplate extends StringRedisTemplate {
        Runnable beforeExec = () -> {};
        Runnable afterExec = () -> {};
        boolean failUnlock;
        GatedTemplate(LettuceConnectionFactory factory) { super(factory); }

        @Override public <T> T execute(SessionCallback<T> callback) {
            return super.execute(new SessionCallback<T>() {
                @Override @SuppressWarnings("unchecked")
                public <K, V> T execute(RedisOperations<K, V> operations) {
                    var proxy = (RedisOperations<K, V>) Proxy.newProxyInstance(RedisOperations.class.getClassLoader(),
                            new Class<?>[]{RedisOperations.class}, (ignored, method, args) -> {
                                if (method.getName().equals("exec")) beforeExec.run();
                                Object value;
                                try { value = method.invoke(operations, args); }
                                catch (InvocationTargetException e) { throw e.getCause(); }
                                if (method.getName().equals("exec")) afterExec.run();
                                return value;
                            });
                    return callback.execute(proxy);
                }
            });
        }

        @Override public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            if (failUnlock && keys.stream().allMatch(k -> k.startsWith("qm:lock:claim:"))) {
                throw new RedisConnectionFailureException("injected unlock failure");
            }
            return super.execute(script, keys, args);
        }
    }
}
