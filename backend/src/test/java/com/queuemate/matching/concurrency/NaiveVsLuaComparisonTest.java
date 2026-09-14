package com.queuemate.matching.concurrency;

import com.queuemate.matching.service.MatchRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "왜 Lua여야 하는가"를 숫자로 남긴다.
 *
 * 같은 부하를 두 방식에 그대로 걸어 결과를 비교한다.
 *   순진한 방식 — 자바에서 EXISTS 확인 후 HSET. 명령 두 개 사이에 틈이 있다.
 *   Lua        — 확인과 저장이 한 원자 실행 안이라 틈이 없다.
 */
class NaiveVsLuaComparisonTest extends ConcurrencyTestSupport {

    private static final int THREADS = 100;
    private static final int ROUNDS = 5;

    @Autowired
    private MatchRequestService matchRequestService;

    /**
     * 실제 서비스가 쓰기 전에 이렇게 짰다면 어땠을지를 그대로 재현한다.
     * EXISTS와 HSET 사이에 다른 스레드가 끼어들 수 있다.
     */
    private boolean naiveJoin(String userId) {
        String key = "qm:user:active-request:" + userId;
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            return false;
        }
        redis.opsForHash().put(key, "requestId", UUID.randomUUID().toString());
        return true;
    }

    @Test
    @DisplayName("순진한 EXISTS-후-HSET 방식은 동시 요청에서 INV-1이 깨진다")
    void naiveApproachBreaksUnderConcurrency() throws InterruptedException {
        int worstRound = 0;
        int totalDuplicates = 0;

        for (int round = 0; round < ROUNDS; round++) {
            resetRedis();
            AtomicInteger accepted = new AtomicInteger();

            runConcurrently(THREADS, i -> {
                if (naiveJoin("u1")) {
                    accepted.incrementAndGet();
                }
            });

            int duplicates = accepted.get() - 1;   // 1개만 통과해야 정상
            worstRound = Math.max(worstRound, accepted.get());
            totalDuplicates += duplicates;
            System.out.printf("  [순진한 방식] round %d: 통과 %d건 (중복 %d건)%n",
                    round + 1, accepted.get(), duplicates);
        }

        System.out.printf("순진한 방식 %d스레드 x %d라운드 → 최대 통과 %d건, 누적 중복 %d건%n",
                THREADS, ROUNDS, worstRound, totalDuplicates);

        // 이 방식은 실제로 깨진다. 깨지지 않으면 비교의 전제가 무너진 것이다.
        assertThat(totalDuplicates)
                .as("순진한 방식은 동시 요청에서 활성 요청을 여러 개 만든다")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Lua 방식은 같은 부하에서 중복이 0건이다")
    void luaApproachHoldsUnderSameLoad() throws InterruptedException {
        int totalDuplicates = 0;

        for (int round = 0; round < ROUNDS; round++) {
            resetRedis();
            AtomicInteger accepted = new AtomicInteger();

            runConcurrently(THREADS, i -> {
                if (matchRequestService.join(command("u1", "RANKED_SOLO", "TOP")).isPresent()) {
                    accepted.incrementAndGet();
                }
            });

            int duplicates = accepted.get() - 1;
            totalDuplicates += duplicates;
            System.out.printf("  [Lua 방식] round %d: 통과 %d건 (중복 %d건)%n",
                    round + 1, accepted.get(), duplicates);
        }

        System.out.printf("Lua 방식 %d스레드 x %d라운드 → 누적 중복 %d건%n",
                THREADS, ROUNDS, totalDuplicates);

        assertThat(totalDuplicates).isZero();
    }
}
