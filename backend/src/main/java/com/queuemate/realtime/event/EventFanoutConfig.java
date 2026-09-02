package com.queuemate.realtime.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class EventFanoutConfig {

    /**
     * 구독은 별도 연결을 붙잡는다. 명령용 연결과 같은 것을 쓰면 구독 중에 다른 명령을
     * 보낼 수 없다. 컨테이너가 그 연결과 재구독을 맡는다.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, EventFanout fanout) {
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
        container.addMessageListener(fanout, new ChannelTopic(EventFanout.CHANNEL));
        return container;
    }
}
