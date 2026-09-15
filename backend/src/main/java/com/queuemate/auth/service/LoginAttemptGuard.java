package com.queuemate.auth.service;

import com.queuemate.common.error.TooManyRequestsException;
import com.queuemate.common.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 비밀번호 대입을 막는다.
 *
 * 기준을 둘로 나눈다. 하나만으로는 각각 뚫린다.
 *
 *   이메일별만  공격자가 계정을 바꿔가며 시도하면 계정마다 한도를 새로 받는다
 *   IP별만      한 계정을 여러 IP에서 노리면 계정별 한도가 없어 계속 시도된다
 *
 * 둘 다 걸면 서로 다른 공격을 각각 막는다.
 */
@Component
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);

    private static final String EMAIL_SCOPE = "login:email";
    private static final String IP_SCOPE = "login:ip";
    private static final Duration WINDOW = Duration.ofMinutes(10);

    /**
     * 사람이 비밀번호를 틀리는 횟수는 보통 한두 번이다. 다섯 번이면 오타를 충분히 흡수한다.
     * 성공하면 카운터를 지우므로 정상 사용자가 이 한도에 닿는 일은 거의 없다.
     */
    private static final int EMAIL_LIMIT = 5;

    /**
     * 한 IP 뒤에 여러 사람이 있을 수 있다. 회사나 학교, 이동통신 NAT가 그렇다.
     * 계정별 한도보다 넉넉하게 두되, 10분에 30회는 사람이 만들 수 있는 수를 넘는다.
     */
    private static final int IP_LIMIT = 30;

    private final RateLimiter rateLimiter;

    public LoginAttemptGuard(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * 시도 전에 부른다. 실패만 세지 않고 모든 시도를 센다.
     * 실패만 세려면 비밀번호를 먼저 확인해야 하는데, 그러면 막으려던 연산을 그대로 하게 된다.
     */
    public void checkAllowed(String email, String clientIp) {
        boolean emailAllowed = rateLimiter.tryAcquire(
                EMAIL_SCOPE, normalize(email), EMAIL_LIMIT, WINDOW,
                // Redis가 죽었다고 무제한 대입을 허용할 수는 없다.
                // refresh token store도 같은 이유로 fail-closed다.
                RateLimiter.OnUnavailable.REJECT);
        boolean ipAllowed = rateLimiter.tryAcquire(
                IP_SCOPE, clientIp, IP_LIMIT, WINDOW, RateLimiter.OnUnavailable.REJECT);

        if (!emailAllowed || !ipAllowed) {
            // 어느 기준에 걸렸는지 응답에 담지 않는다. 이메일 한도에 걸렸다는 사실만으로도
            // 그 계정이 공격받고 있다는 정보가 된다.
            log.warn("로그인 시도 제한 email={} ip={}", emailAllowed ? "ok" : "limited",
                    ipAllowed ? "ok" : "limited");
            throw new TooManyRequestsException("LOGIN_ATTEMPTS_EXCEEDED",
                    "로그인 시도가 너무 잦다. 잠시 후 다시 시도한다");
        }
    }

    /** 성공하면 그동안의 시도를 없던 일로 본다. 오타 몇 번 뒤에 성공한 사용자를 벌하지 않는다. */
    public void recordSuccess(String email, String clientIp) {
        rateLimiter.reset(EMAIL_SCOPE, normalize(email), WINDOW);
        rateLimiter.reset(IP_SCOPE, clientIp, WINDOW);
    }

    /** 대소문자만 바꿔 한도를 새로 받는 것을 막는다. */
    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
