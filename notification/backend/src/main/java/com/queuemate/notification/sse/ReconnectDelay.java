package com.queuemate.notification.sse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 브라우저에 내려 줄 SSE 재접속 대기 시간({@code retry:})을 연결마다 다르게 뽑는다.
 *
 * <p>서버를 재배포하면 모든 연결이 같은 순간에 끊기고, 모든 브라우저가 같은 간격으로 재접속한다.
 * 각자 {@code matching} 에 상태 조회까지 한 번씩 보내므로 몰림이 두 서비스에 같이 온다.
 * {@code EventSource} 는 재시도 간격을 프런트에서 바꿀 수 없고 서버가 내려 준 값을 쓰므로,
 * 여기서 값을 흩어 놓는다.
 *
 * <p>브라우저는 마지막으로 받은 값을 기억한다. <b>연결할 때 한 번만 보내면 된다</b> — 알림마다
 * 붙이면 줄만 늘고 효과가 없다.
 */
@Component
public class ReconnectDelay {

    private final long minMs;
    private final long maxMs;

    public ReconnectDelay(@Value("${queuemate.sse.reconnect-delay-min-ms}") long minMs,
                          @Value("${queuemate.sse.reconnect-delay-max-ms}") long maxMs) {
        if (minMs < 0 || minMs > maxMs) {
            // 잘못된 설정으로 조용히 도는 것보다 기동에서 막는 편이 낫다
            throw new IllegalArgumentException(
                    "queuemate.sse.reconnect-delay: min=" + minMs + " max=" + maxMs + " (0 <= min <= max 여야 한다)");
        }
        this.minMs = minMs;
        this.maxMs = maxMs;
    }

    /**
     * {@code [min, max]} 사이의 값을 하나 뽑는다. 요청 스레드 여럿이 동시에 부르므로
     * {@link ThreadLocalRandom} 을 쓴다.
     */
    public long nextMs() {
        return ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
    }
}
