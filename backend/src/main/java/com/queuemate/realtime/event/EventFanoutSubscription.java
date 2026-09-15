package com.queuemate.realtime.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 노드 간 이벤트 채널 구독을 유지한다.
 *
 * 구독 컨테이너를 그냥 자동 기동으로 두면 Redis가 죽어 있을 때 컨텍스트가 아예 안 뜬다.
 * 노트 014에서 Redis 장애 시 노드 간 전달만 멈추고 같은 노드에는 계속 보내기로 했는데,
 * 기동이 막히면 그 결정이 무의미해진다. 아무것도 못 한다.
 *
 * 그래서 자동 기동을 끄고 여기서 붙인다. 실패하면 다음 주기에 다시 시도한다.
 * 한 번 실패하고 끝내면 Redis가 살아나도 이 노드만 영원히 고립된다.
 * 그 고립은 조용하다. 다른 노드의 이벤트가 안 올 뿐 아무 오류도 안 난다.
 */
@Component
public class EventFanoutSubscription {

    private static final Logger log = LoggerFactory.getLogger(EventFanoutSubscription.class);

    private final RedisMessageListenerContainer container;

    public EventFanoutSubscription(RedisMessageListenerContainer container) {
        this.container = container;
    }

    public boolean isSubscribed() {
        return container.isRunning();
    }

    @Scheduled(initialDelay = 0, fixedDelay = 10_000)
    public void ensureSubscribed() {
        if (container.isRunning()) {
            return;
        }
        try {
            container.start();
            log.info("노드 간 이벤트 채널 구독 시작 channel={}", EventFanout.CHANNEL);
        } catch (RuntimeException e) {
            // 실패한 채로 running이 남으면 다음 주기에 재시도하지 않는다.
            container.stop();
            log.warn("노드 간 이벤트 채널 구독 실패. 다음 주기에 다시 시도한다: {}", e.getMessage());
        }
    }
}
