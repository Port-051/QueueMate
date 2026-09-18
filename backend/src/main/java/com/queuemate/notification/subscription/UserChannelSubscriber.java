package com.queuemate.notification.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 사용자별 Redis 채널 구독을 걸고 푼다.
 *
 * <p>{@code SUBSCRIBE qm:pubsub:push:{userId}} 만 쓴다. <b>{@code qm:pubsub:push:*}
 * 패턴 구독 금지</b> — 사용자가 늘면 모든 인스턴스가 모든 사용자의 메시지를 받아 버리고
 * 로컬에서 거르는 낭비가 인스턴스 수만큼 곱해진다 (CLAUDE.md §5).
 *
 * <p>호출 시점은 {@code SseConnections} 가 정한다 — 첫 연결에 {@link #subscribe},
 * 마지막 연결에 {@link #unsubscribe}.
 *
 * <p><b>순환 참조를 여기서 끊는다.</b> {@code SseConnections → 이 클래스 →
 * PushMessageListener → SseConnections} 가 순환이라 {@code PushMessageListener} 를
 * 직접 주입받지 않고 {@link ObjectProvider} 로 받는다. 실제 인스턴스는 첫
 * {@link #subscribe} 호출 때 꺼내므로 컨텍스트 로딩 시점에는 순환이 없다.
 * {@code @Lazy} 프록시 대신 {@code ObjectProvider} 를 고른 이유는 컨테이너에 등록한
 * 리스너 객체가 프록시가 아닌 <b>같은 인스턴스</b>여야 {@code removeMessageListener} 가
 * 짝을 찾기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserChannelSubscriber {

    private final RedisMessageListenerContainer container;
    private final ObjectProvider<PushMessageListener> listenerProvider;

    /**
     * 그 사용자의 채널을 구독한다. 첫 연결이 생겼을 때 한 번만 불린다.
     */
    public void subscribe(String userId) {
        // TODO: container.addMessageListener(listenerProvider.getObject(), new ChannelTopic(PushChannels.pushChannel(userId)))
    }

    /**
     * 그 사용자의 채널 구독을 푼다. 마지막 연결이 끊겼을 때 한 번만 불린다.
     */
    public void unsubscribe(String userId) {
        // TODO: container.removeMessageListener(listenerProvider.getObject(), new ChannelTopic(PushChannels.pushChannel(userId)))
    }
}
