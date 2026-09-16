package com.queuemate.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 시한이 지난 제안을 찾아 {@link ProposalExpiryService} 에 넘긴다 (INV-5 expired).
 *
 * <p><b>고르는 일만 한다.</b> 실제 정리는 만료 스크립트와 취소 경로가 한다. 나눠 둔 것은
 * 테스트 때문이기도 하다 — 만료 로직을 확인하려고 주기를 기다릴 이유가 없다.
 *
 * <p><b>{@code fixedRate} 가 아니라 {@code fixedDelay} 다.</b> 스케줄러 스레드는 기본 1개라,
 * 한 회차가 주기보다 오래 걸리면 {@code fixedRate} 는 실행이 밀려 쌓인다. 앞 회차가 끝난 뒤
 * 세는 {@code fixedDelay} 가 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProposalSweeper {

    /**
     * 한 회차에 처리할 상한.
     *
     * <p>Redis 는 싱글 스레드다. 한 번에 수천 개를 돌면 그동안 사용자 요청이 밀린다.
     * 남은 것은 다음 회차가 가져간다 — 목록이 만료 시각순이라 오래된 것부터 나온다.
     */
    private static final long BATCH_SIZE = 100;

    /** Redis 가 죽어 있으면 매 회차 실패한다. 스택트레이스를 초당 한 번씩 쌓지 않으려는 간격 */
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 30_000;

    private final StringRedisTemplate redis;
    private final ProposalExpiryService proposalExpiryService;

    private long lastFailureLoggedAt;

    @Scheduled(fixedDelayString = "${queuemate.sweep.interval-ms}")
    public void sweep() {
        long now = System.currentTimeMillis();

        Set<String> expired;
        try {
            expired = redis.opsForZSet()
                    .rangeByScore(ProposalExpiryService.PENDING_KEY, 0, now, 0, BATCH_SIZE);
        } catch (DataAccessException e) {
            logFailureQuietly(now, e);
            return;
        }
        if (expired == null || expired.isEmpty()) {
            return;
        }

        // 하나가 터져도 나머지를 계속 처리한다. 예외를 밖으로 내보내면 그 회차가 통째로 끊기고,
        // 같은 항목이 다음 회차에도 또 걸려 영영 앞을 막는다
        for (String partyId : expired) {
            try {
                proposalExpiryService.expire(partyId);
            } catch (Exception e) {
                log.warn("제안 만료 처리 실패 partyId={}", partyId, e);
            }
        }
    }

    /**
     * Redis 장애는 조용히 넘긴다.
     *
     * <p>새 매칭을 막는 것은 이미 {@code GlobalExceptionHandler} 의 fail-closed 가 한다 (INV-10).
     * 여기서 할 수 있는 일은 없고, 1초마다 같은 예외를 쌓으면 진짜 로그가 묻힌다.
     */
    private void logFailureQuietly(long now, DataAccessException e) {
        if (now - lastFailureLoggedAt < FAILURE_LOG_INTERVAL_MILLIS) {
            return;
        }
        lastFailureLoggedAt = now;
        log.warn("만료 대기 목록을 읽지 못했다. 다음 주기에 다시 시도한다", e);
    }
}
