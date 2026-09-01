package com.queuemate.common.ratelimit;

import java.time.Duration;

/**
 * 남용을 눌러 주는 장치이지 정합성 장치가 아니다.
 *
 * 정확성을 보장하는 곳은 각 기능의 제약과 검증이다. rate limit은 한 사용자가 자원을
 * 독식하거나 다른 사용자를 괴롭히는 걸 막는 데까지만 책임진다.
 */
public interface RateLimiter {

    /**
     * @param scope    한도를 나누는 단위. signal, login 처럼 기능별로 다르다.
     * @param identity 누구에게 거는가. 보통 userId다.
     * @return 허용이면 true. 한도를 넘었으면 false.
     */
    boolean tryAcquire(String scope, String identity, int limit, Duration window);
}
