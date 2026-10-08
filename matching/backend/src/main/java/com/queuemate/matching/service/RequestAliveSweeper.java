package com.queuemate.matching.service;

import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 접속 확인이 끊긴 요청을 찾아 {@link RequestAliveExpiryService} 에 넘긴다 (docs/11 D-43).
 *
 * <p><b>무엇을 꺼내나.</b> {@code qm:request:alive} ZSET(member = userId, score = 시한)에서 score 가 지금보다
 * 작은 member 다. 접수가 첫 시한을 넣고 heartbeat 가 뒤로 밀므로, 여기 걸리는 것은 유예만큼 신호가 없던 사람이다.
 * 끝난 요청(취소 · 만료 · 확정)의 member 도 여기 걸린다 — Lua 가 {@code ZREM} 하지 않고 여기서 게으르게 지우기
 * 때문이다({@code RequestAliveExpiryService} 머리말). 그래서 꺼낸 것 대부분은 실제로 할 일이 없다.
 *
 * <p><b>고르는 일만 한다.</b> 실제 정리는 {@code expire()} 가 취소 경로({@code leave-party.lua})를 태워서 한다.
 * {@link ProposalSweeper} 와 같은 이유로 나눠 두었다 — 테스트가 주기를 기다리지 않고 {@code sweep()} 을 직접 부른다.
 *
 * <p><b>락 없이 여러 인스턴스가 돌아도 된다.</b> {@code expire()} 가 <b>먼저</b> {@code ZREM} 하고 나서 활성 요청을
 * 보므로, 두 인스턴스가 같은 회차에 같은 사람을 꺼내도 뒤늦은 쪽의 {@code cancel()} 은 {@code NOT_FOUND} 로 끝난다
 * ({@code leave-party.lua} 가 compare-and-delete 다). 두 번 빠지는 일도, 그 사이 새로 접수한 요청이 지워지는 일도 없다.
 *
 * <p><b>{@code fixedRate} 가 아니라 {@code fixedDelay} 다.</b> 스케줄러 스레드는 기본 1개라, 한 회차가 주기보다 오래
 * 걸리면 {@code fixedRate} 는 실행이 밀려 쌓인다. 앞 회차가 끝난 뒤 세는 {@code fixedDelay} 가 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestAliveSweeper {

    /**
     * 한 회차에 처리할 상한.
     *
     * <p>Redis 는 싱글 스레드다. 한 번에 수천 개를 돌면 그동안 사용자 요청이 밀린다.
     * 남은 것은 다음 회차가 가져간다 — 목록이 시한순이라 오래된 것부터 나온다.
     */
    private static final long BATCH_SIZE = 100;

    /** Redis 가 죽어 있으면 매 회차 실패한다. 스택트레이스를 주기마다 쌓지 않으려는 간격 */

    private final StringRedisTemplate redis;
    private final RequestAliveExpiryService requestAliveExpiryService;


    @Scheduled(fixedDelayString = "${queuemate.alive.sweep-interval-ms}")
    public void sweep() {
        long now = System.currentTimeMillis();

        Set<String> stale;
        try {
            stale = redis.opsForZSet()
                    .rangeByScore(SharedKeys.HEARTBEAT_KEY, 0, now, 0, BATCH_SIZE);
        } catch (DataAccessException e) {
            log.warn("접속 확인 목록을 읽지 못했다. 다음 주기에 다시 시도한다", e);
            return;
        }
        if (stale == null || stale.isEmpty()) {
            return;
        }

        // 하나가 터져도 나머지를 계속 처리한다. 예외를 밖으로 내보내면 그 회차가 통째로 끊기고,
        // 같은 항목이 다음 회차에도 또 걸려 영영 앞을 막는다
        for (String userId : stale) {
            try {
                requestAliveExpiryService.expire(userId);
            } catch (Exception e) {
                log.warn("접속 확인 만료 처리 실패 userId={}", userId, e);
            }
        }
    }

}
