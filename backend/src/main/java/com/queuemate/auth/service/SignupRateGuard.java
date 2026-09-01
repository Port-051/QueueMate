package com.queuemate.auth.service;

import com.queuemate.common.error.TooManyRequestsException;
import com.queuemate.common.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 계정이 한 곳에서 대량으로 만들어지는 것을 막는다.
 *
 * 무엇을 막지 못하는지 먼저 적어 둔다. 차단이나 신고를 당한 사람이 계정 하나를 새로 만들어
 * 돌아오는 것은 이것으로 막히지 않는다. 한 번의 가입이기 때문이다. 그건 신원 확인의
 * 영역이고 지금 범위 밖이다. 여기서 막는 것은 자동화된 대량 생성뿐이다.
 *
 * 그 대량 생성이 두 가지를 망친다.
 *
 *   CPU        가입은 비밀번호를 해싱한다. 일부러 비싼 연산이라 몰리면 코어를 먹는다.
 *   대기열     INV-1 때문에 계정 하나가 대기열 자리 하나다. 가짜 계정은 곧 가짜 참가자다.
 *
 * 로그인 제한과 구조가 비슷하지만 두 곳이 다르다.
 *
 * 첫째, 성공해도 카운터를 지우지 않는다. 로그인에서는 성공이 정상 사용자라는 증거지만,
 * 가입에서는 성공 자체가 아껴야 할 자원이다. 성공할 때마다 한도를 돌려주면 한도가 없다.
 *
 * 둘째, 기준이 IP 하나뿐이다. 가입에는 인증된 주체가 없고, 이메일은 매번 다른 값이라
 * 세는 의미가 없다. 중복 이메일은 unique 제약이 이미 막는다.
 */
@Component
public class SignupRateGuard {

    private static final Logger log = LoggerFactory.getLogger(SignupRateGuard.class);

    private static final String BURST_SCOPE = "signup:ip:burst";
    private static final String DAILY_SCOPE = "signup:ip:daily";

    private static final Duration BURST_WINDOW = Duration.ofMinutes(10);
    private static final Duration DAILY_WINDOW = Duration.ofDays(1);

    /**
     * 한 IP에서 10분에 5건. 사람은 가입을 한 번 한다. 같은 자리에서 10분에 다섯 번은
     * 이미 사람의 사용 방식이 아니다. 비밀번호 해싱이 한꺼번에 몰리는 것을 여기서 막는다.
     */
    private static final int BURST_LIMIT = 5;

    /**
     * 한 IP에서 하루 50건. 짧은 창만 두면 속도를 맞춰 하루 720건까지 지나간다.
     * 이 값이 실제 상한을 정한다.
     *
     * 넉넉하게 잡은 이유는 한 IP 뒤에 여러 사람이 있기 때문이다. PC방과 이동통신
     * NAT가 그렇다. 가입은 서비스의 정문이라 잘못 막으면 그 사용자는 돌아오지 않는다.
     * 공격자는 어차피 IP를 바꾼다. 조이면 공유 IP 사용자만 벌하고 공격은 못 막는다.
     */
    private static final int DAILY_LIMIT = 50;

    private final RateLimiter rateLimiter;

    public SignupRateGuard(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * 비밀번호를 해싱하기 전에 부른다. 뒤에서 세면 막으려던 연산을 그대로 하게 된다.
     *
     * 중복 이메일로 실패한 요청도 센다. 셀 시점에는 성공할지 알 수 없고,
     * 실패만 빼 주면 존재하는 이메일을 넣어 한도를 피해 가는 길이 생긴다.
     */
    public void checkAllowed(String clientIp) {
        boolean burstAllowed = rateLimiter.tryAcquire(
                BURST_SCOPE, clientIp, BURST_LIMIT, BURST_WINDOW,
                // Redis가 죽었다고 무제한 가입을 열어 둘 수는 없다. 그때 만들어진 계정은
                // 장애가 끝나도 남는다. 일시적인 고장이 영구적인 결과를 만든다.
                // 반대로 막았을 때의 손해는 잠시 뒤 다시 시도하면 되는 것뿐이다.
                RateLimiter.OnUnavailable.REJECT);
        boolean dailyAllowed = rateLimiter.tryAcquire(
                DAILY_SCOPE, clientIp, DAILY_LIMIT, DAILY_WINDOW, RateLimiter.OnUnavailable.REJECT);

        if (!burstAllowed || !dailyAllowed) {
            log.warn("가입 속도 제한 ip={} burst={} daily={}", clientIp,
                    burstAllowed ? "ok" : "limited", dailyAllowed ? "ok" : "limited");
            throw new TooManyRequestsException("SIGNUP_RATE_EXCEEDED",
                    "가입 시도가 너무 잦다. 잠시 후 다시 시도한다");
        }
    }
}
