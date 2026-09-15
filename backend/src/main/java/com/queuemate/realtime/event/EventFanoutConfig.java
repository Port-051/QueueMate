package com.queuemate.realtime.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class EventFanoutConfig {

    @Bean
    public ThreadPoolTaskExecutor fanoutDeliveryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("fanout-delivery-");
        return executor;
    }

    @Bean
    public SimpleAsyncTaskExecutor fanoutSubscriptionExecutor() {
        return new SimpleAsyncTaskExecutor("fanout-subscription-");
    }

    /**
     * 구독은 별도 연결을 붙잡는다. 명령용 연결과 같은 것을 쓰면 구독 중에 다른 명령을
     * 보낼 수 없다. 컨테이너가 그 연결과 재구독을 맡는다.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, EventFanout fanout,
            @Qualifier("fanoutDeliveryExecutor") ThreadPoolTaskExecutor deliveryExecutor,
            @Qualifier("fanoutSubscriptionExecutor") SimpleAsyncTaskExecutor subscriptionExecutor) {
        // 자동 기동을 끈다. Redis가 죽어 있을 때 구독 실패가 컨텍스트 기동을 막으면
        // 노드 간 전달만 잃기로 한 결정이 무의미해진다. 앱이 아예 안 뜬다.
        // 이 버전의 컨테이너에는 setAutoStartup이 없어서 재정의가 유일한 방법이다.
        // 실제 구독은 EventFanoutSubscription이 붙이고, 실패하면 다시 시도한다.
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public boolean isAutoStartup() {
                return false;
            }
        };
        container.setConnectionFactory(connectionFactory);
        // 병렬 콜백은 Redis 수신 순서를 바꿀 수 있다. OFFER/ICE 전달을 직렬화한다.
        // 구독 연결은 컨테이너의 별도 실행기를 사용한다.
        container.setTaskExecutor(deliveryExecutor);
        container.setSubscriptionExecutor(subscriptionExecutor);
        container.addMessageListener(fanout, new ChannelTopic(EventFanout.CHANNEL));
        return container;
    }
}
