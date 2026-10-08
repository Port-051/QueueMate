package com.queuemate.notification.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserChannelSubscriber} 가 <b>사용자별 채널 구독</b>만 거는지 본다. Redis 는 띄우지 않는다.
 */
class UserChannelSubscriberTest {

    private RedisMessageListenerContainer container;
    private PushMessageListener listener;
    private UserChannelSubscriber subscriber;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        container = mock(RedisMessageListenerContainer.class);
        listener = mock(PushMessageListener.class);
        ObjectProvider<PushMessageListener> listenerProvider = mock(ObjectProvider.class);
        when(listenerProvider.getObject()).thenReturn(listener);
        subscriber = new UserChannelSubscriber(container, listenerProvider);
    }

    @Test
    @DisplayName("subscribe(\"u1\") 은 ChannelTopic qm:pubsub:push:u1 로 리스너를 건다 - PatternTopic 이 아니다")
    void 사용자_채널을_패턴이_아닌_채널_구독으로_건다() {
        subscriber.subscribe("u1");

        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);
        verify(container).addMessageListener(listenerCaptor.capture(), topicCaptor.capture());

        assertThat(listenerCaptor.getValue()).isSameAs(listener);
        // 패턴 구독(qm:pubsub:push:*) 금지 (CLAUDE.md §5)
        assertThat(topicCaptor.getValue())
                .isInstanceOf(ChannelTopic.class)
                .isNotInstanceOf(PatternTopic.class);
        assertThat(topicCaptor.getValue().getTopic()).isEqualTo("qm:pubsub:push:u1");
        verify(container, never()).removeMessageListener(any(MessageListener.class), any(Topic.class));
    }

    @Test
    @DisplayName("unsubscribe(\"u1\") 은 같은 리스너 인스턴스, 같은 채널 이름으로 removeMessageListener 를 부른다")
    void 걸_때와_같은_리스너와_채널로_푼다() {
        subscriber.subscribe("u1");
        subscriber.unsubscribe("u1");

        ArgumentCaptor<MessageListener> addedListener = ArgumentCaptor.forClass(MessageListener.class);
        ArgumentCaptor<Topic> addedTopic = ArgumentCaptor.forClass(Topic.class);
        verify(container).addMessageListener(addedListener.capture(), addedTopic.capture());
        ArgumentCaptor<MessageListener> removedListener = ArgumentCaptor.forClass(MessageListener.class);
        ArgumentCaptor<Topic> removedTopic = ArgumentCaptor.forClass(Topic.class);
        verify(container).removeMessageListener(removedListener.capture(), removedTopic.capture());

        // 컨테이너는 리스너 인스턴스와 토픽으로 짝을 찾는다. 다르면 구독이 안 풀리고 쌓인다
        assertThat(removedListener.getValue()).isSameAs(listener).isSameAs(addedListener.getValue());
        assertThat(removedTopic.getValue()).isInstanceOf(ChannelTopic.class);
        assertThat(removedTopic.getValue().getTopic()).isEqualTo("qm:pubsub:push:u1");
        assertThat(removedTopic.getValue()).isEqualTo(addedTopic.getValue());
    }

    @Test
    @DisplayName("사용자마다 자기 채널만 건다")
    void 사용자마다_자기_채널만_건다() {
        subscriber.subscribe("u1");
        subscriber.subscribe("u2");

        ArgumentCaptor<Topic> topicCaptor = ArgumentCaptor.forClass(Topic.class);
        verify(container, times(2)).addMessageListener(any(MessageListener.class), topicCaptor.capture());

        assertThat(topicCaptor.getAllValues())
                .extracting(Topic::getTopic)
                .containsExactly("qm:pubsub:push:u1", "qm:pubsub:push:u2");
    }
}
