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
     * Redis를 못 쓸 때 어떻게 할 것인가. 호출하는 쪽이 반드시 정한다.
     *
     * 같은 Redis라도 기능마다 답이 다르다. 기본값을 두면 그 판단을 안 하고 지나가게 된다.
     */
    enum OnUnavailable {
        /** 통과시킨다. 막으면 정상 사용자의 진행이 끊기는 경우. */
        ALLOW,
        /** 거절한다. 통과시키면 방어가 통째로 사라지는 경우. */
        REJECT
    }

    /**
     * @param scope    한도를 나누는 단위. signal, login 처럼 기능별로 다르다.
     * @param identity 누구에게 거는가. userId나 email이나 IP다.
     * @return 허용이면 true. 한도를 넘었으면 false.
     */
    boolean tryAcquire(String scope, String identity, int limit, Duration window,
                       OnUnavailable onUnavailable);

    /**
     * 카운터를 지운다. 로그인 성공처럼 그동안의 실패를 없던 일로 볼 때 쓴다.
     * 지우지 못해도 창이 지나면 사라지므로 실패를 예외로 올리지 않는다.
     */
    void reset(String scope, String identity, Duration window);
}
