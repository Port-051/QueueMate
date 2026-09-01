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
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(fanout, new ChannelTopic(EventFanout.CHANNEL));
        return container;
    }
}
