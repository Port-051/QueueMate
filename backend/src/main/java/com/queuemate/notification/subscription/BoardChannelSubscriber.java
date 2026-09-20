package com.queuemate.notification.subscription;

import com.queuemate.notification.domain.GameKey;
import com.queuemate.notification.redisKeys.BoardChannels;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 게시판 채널({@code qm:pubsub:board:{game}}) 구독을 <b>앱이 뜰 때 한 번만</b> 건다. 풀지 않는다.
 *
 * <p>사용자 채널과 다르게 다룬다 — 게시판 채널은 게임마다 하나이고 접속한 모두가 같이 쓴다.
 * 사용자의 접속 · 종료에 맞춰 걸고 풀면, 한 사람이 나갈 때 남은 사람들의 구독까지 풀린다.
 * 그래서 {@link UserChannelSubscriber} 에 두지 않는다.
 *
 * <p>빈이 전부 만들어진 뒤 · 컨테이너가 시작하기 전에 불린다. 여기서는 채널을 등록만 하고,
 * 실제 {@code SUBSCRIBE} 는 컨테이너가 시작하면서 한다.
 */
@Component
@RequiredArgsConstructor
public class BoardChannelSubscriber implements SmartInitializingSingleton {

    private final RedisMessageListenerContainer container;
    private final PushBoardListener listener;

    @Override
    public void afterSingletonsInstantiated() {
        for (GameKey gameKey : GameKey.values()) {
            container.addMessageListener(listener, new ChannelTopic(BoardChannels.boardChannel(gameKey)));
        }
    }
}
