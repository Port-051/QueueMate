package com.queuemate.matching.concurrency;

import com.queuemate.matching.service.MatchRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.queuemate.matching.dto.AcceptedRequest;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-1: 한 사용자는 활성 매칭 요청을 1개만 가진다.
 *
 * "이미 있나?"를 확인하고 등록하는 두 동작 사이에 다른 요청이 끼면 둘 다 통과한다.
 * Lua 스크립트로 그 두 동작을 한 원자 실행에 묶었고, 여기서 실제 경합으로 확인한다.
 */
class ActiveRequestConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    private MatchRequestService matchRequestService;

    @Test
    @DisplayName("INV-1: 같은 사용자가 동시에 100번 요청해도 정확히 1개만 성공한다")
    void onlyOneRequestSucceedsPerUser() throws InterruptedException {
        AtomicInteger accepted = new AtomicInteger();

        runConcurrently(100, i -> {
            Optional<AcceptedRequest> requestId = matchRequestService.join(command("u1", "RANKED_SOLO", "TOP"));
            if (requestId.isPresent()) {
                accepted.incrementAndGet();
            }
        });

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(redis.hasKey("qm:user:active-request:u1")).isTrue();
    }

    @Test
    @DisplayName("INV-1: 서로 다른 사용자 100명은 모두 성공한다")
    void differentUsersAllSucceed() throws InterruptedException {
        AtomicInteger accepted = new AtomicInteger();

        runConcurrently(100, i -> {
            Optional<AcceptedRequest> requestId = matchRequestService.join(command("u" + i, "RANKED_SOLO", "TOP"));
            if (requestId.isPresent()) {
                accepted.incrementAndGet();
            }
        });

        assertThat(accepted.get()).isEqualTo(100);
    }
}
