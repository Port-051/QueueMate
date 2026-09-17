package com.queuemate.matching.redisLock;

import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 후보 풀 하나를 잡는 분산 락.
 *
 * <p><b>락 단위가 파티가 아니라 후보 풀 전체인 이유.</b> 배정의 첫 단계가
 * "needs 색인에서 파티 하나 꺼내기"다. 그 시점에는 어느 파티에 들어갈지 아직 모르므로
 * 파티 단위로는 잠글 대상 자체가 없다.
 *
 * <p><b>keyValue(포지션)를 락 키에 넣으면 안 된다.</b> 잠글 수 있는 가장 작은 단위는
 * {@code game:mode:voice:purpose} 까지다. 파티 하나는 자기가 아직 못 채운 keyValue
 * <b>여럿</b>의 색인에 동시에 올라가 있어서, 미드로 들어오는 사람과 정글로 들어오는 사람이
 * <b>같은 파티</b>를 건드린다. 락 키에 keyValue 를 넣으면 둘이 서로 다른 락을 잡아
 * 아무것도 막지 못하면서 "락을 잡았으니 안전하다"는 착각만 남는다.
 *
 * <p><b>락 안에서 DB 조회 / 외부 호출 / 느린 작업을 하지 마라.</b> 유지 시간을 넘기면
 * 락이 저 혼자 풀리고, 그 사이 들어온 다른 요청과 같은 파티에 동시에 손을 대게 된다.
 * 잠근 구간은 Redis 명령 몇 개로 끝나야 한다.
 *
 * <p><b>이 락은 Lua 를 대체하지 않고 두 Lua 사이의 틈을 막는다.</b> 배정은 후보 찾기
 * ({@code create-or-check-party-*.lua})와 합류({@code join-party*.lua})의 두 스크립트로
 * 나뉘어 있고, 그 사이에 자바의 차단 검증이 낀다. 각 스크립트 안의 확인+쓰기는 여전히
 * Lua 한 덩어리가 원자적으로 하고(CLAUDE.md §4 원자성 규칙), 두 호출 사이에 다른 요청이
 * 같은 파티에 끼어드는 것만 이 락이 막는다. 호출부는 {@code LolCandidateRule#canJoin()} 이다.
 *
 * <p><b>Lua 와 달리 이 락은 저장소가 강제하지 않는다.</b> Redis 에는 "이 키는 잠겨 있다"는
 * 개념이 없다. 파티 데이터를 만지는 모든 코드가 먼저 이 락을 잡는다는 약속을 지켜야만
 * 성립한다. 락을 건너뛰는 코드가 하나라도 생기면 그 순간 무의미해진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoolLock {

    /** 락을 기다리는 시간. 매칭은 사용자가 대기 화면에서 보고 있으므로 짧게 끊는다. */
    private static final long WAIT_MILLIS = 3000;

    /**
     * 락을 쥐고 있는 시간.
     *
     * 명시하지 않으면 Redisson 워치독이 30초를 잡고 백그라운드에서 계속 연장한다.
     * 그러면 작업 스레드가 죽어도 락이 30초 동안 안 풀려 그 풀 전체가 멈춘다.
     */
    private static final long LEASE_MILLIS = 3000;
    // 두 값 모두 부하를 보고 조정할 여지가 있다. 조정이 잦아지면 application.yaml 로 뺀다.

    private final RedissonClient redisson;


    public <T> T call(String poolKey, Supplier<T> work)
    {
        RLock lock = redisson.getLock(SharedKeys.poolLockKey(poolKey));
        boolean acquired;

        try {
            acquired = lock.tryLock(WAIT_MILLIS, LEASE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(poolKey, e);
        }
        if(!acquired)
        {
            throw unavailable(poolKey, null);
        }

        try {
            return work.get();
        }
        finally {

            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 돌려줄 값이 없는 작업용. 배정 경로(canJoin)가 void 라 이쪽을 쓴다. */
    public void run(String poolKey, Runnable work) {
        call(poolKey, () -> {
            work.run();
            return null;
        });
    }

    /**
     * 락 실패는 Redis 장애와 같은 자리로 내보낸다 (INV-10 fail-closed).
     *
     * QueryTimeoutException 은 DataAccessException 이므로
     * GlobalExceptionHandler#handleRedisFailure 가 이미 잡는다.
     * 확인을 못 한 요청을 통과시키지 않는다는 판단이 같으니 예외 타입을 새로 만들지 않는다.
     */
    private QueryTimeoutException unavailable(String poolKey, Throwable cause) {
        log.warn("후보 풀 락을 잡지 못해 요청을 거절한다 poolKey={}", poolKey, cause);
        return new QueryTimeoutException("후보 풀 락 획득 실패: " + poolKey, cause);
    }
}
