package com.queuemate.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 구독 컨테이너를 만든다.
 *
 * <p>이 서비스가 Redis 에서 하는 일은 {@code qm:pubsub:push:{userId}} 구독뿐이다.
 * 매칭 키({@code qm:party:*}, {@code qm:user:*}, {@code qm:proposal:*},
 * {@code qm:gameconfig:*})를 읽거나 쓰는 빈을 여기 두지 마라 (CLAUDE.md §2).
 *
 * <p>구독 추가/해제는 컨테이너에 직접 하지 않고 {@code UserChannelSubscriber} 를 거친다.
 * 리스너와 토픽의 짝을 한 곳에서만 관리하기 위해서다.
 */
@Configuration
public class RedisConfig {

    /**
     * 사용자별 채널을 동적으로 붙였다 뗐다 할 컨테이너.
     *
     * <p>컨테이너는 Redis 와 전용 연결 하나를 열고 그 위에서 {@code SUBSCRIBE} /
     * {@code UNSUBSCRIBE} 를 실행한다. 리스너 실행 스레드는 컨테이너 것이므로, 그 안에서
     * SSE 전송을 하지 않는다 — {@code PushMessageListener} 가 별도 풀로 넘긴다.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // TODO: 구독 처리용 TaskExecutor, 연결 끊김 시 재구독 간격(recoveryInterval), 에러 핸들러를 정한다
        return container;
    }
}
