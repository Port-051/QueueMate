package com.queuemate.common.ratelimit;

import com.queuemate.common.error.ServiceUnavailableException;

/**
 * fail-closed로 정한 자리에서 Redis를 못 읽었을 때.
 *
 * 로그인처럼 통과시키면 방어가 통째로 사라지는 경로에서만 난다.
 * 이 예외가 뜨면 그 기능은 Redis가 돌아올 때까지 멈춘다.
 */
public class RateLimitUnavailableException extends ServiceUnavailableException {

    public RateLimitUnavailableException(String scope, Throwable cause) {
        super("RATE_LIMIT_UNAVAILABLE", "일시적으로 처리할 수 없다 scope=" + scope, cause);
    }
}
