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
        // 웹 서버보다 먼저 시작시킨다(phase 가 작을수록 먼저 시작하고 나중에 멈춘다). 기본값(Integer.MAX_VALUE)이면
        // Tomcat 이 요청을 받기 시작한 뒤에 컨테이너가 Redis 에 붙는다 - 그 1~2초 사이에 들어온 SSE 연결의 채널은
        // 구독이 걸리지 않거나(알림이 안 간다) 구독 연결이 둘 생긴다(알림이 두 번 간다). 2026-09-20 에 재현했다.
        // 시작 때 게시판 채널이 등록돼 있으므로(BoardChannelSubscriber) 첫 요청이 오기 전에 구독 연결이 하나로 선다
        container.setPhase(0);
        // 나머지는 기본값을 쓴다. 2026-09-19 에 Redis 를 껐다 켜며 확인한 동작이다.
        //  - 재연결: Redis 가 재시작돼도 열려 있던 SSE 연결은 다시 알림을 받는다. 컨테이너가 다시 붙으면서
        //    등록돼 있던 채널을 전부 다시 구독한다. 그 사이 발행된 알림은 사라진다 - Pub/Sub 의 성질이고
        //    클라이언트가 상태 조회로 복구한다
        //  - Redis 가 죽어 있는 동안 마지막 연결이 닫혀도 구독 해제는 예외를 던지지 않는다. 맵에서 정상으로
        //    빠지고, 복구 뒤 그 사용자가 재접속하면 구독이 새로 걸린다
        //  - 에러 핸들러: PushMessageListener 가 예외를 안에서 다 잡으므로 따로 두지 않는다
        //  - 리스너 스레드: onMessage 는 전송 풀에 넘기고 바로 끝나므로 기본 실행기로 충분하다
        return container;
    }
}
