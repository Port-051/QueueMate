package com.queuemate.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 구독 컨테이너를 만든다 — notification 의 {@code config/RedisConfig} 와 같은 모양이다(2026-10-10 옮겨 왔다).
 *
 * <p>이 서비스가 Redis 에서 <b>구독</b>하는 것은 방 채팅 채널 {@code qm:pubsub:chat:{roomId}}(그 방의 소켓이 이 인스턴스에 있을 때만)과,
 * 뒤에 생길 DM 깨우기 채널 · {@code qm:pubsub:chat:ctl} 이다(CLAUDE.md §2). 발행({@code PUBLISH})과 DM 스트림 쓰기는 이 컨테이너가 아니라
 * 자동 설정된 {@code StringRedisTemplate} 으로 한다 — {@code SUBSCRIBE} 를 건 연결은 다른 명령을 보낼 수 없어 구독 전용 연결이 따로 필요하다.
 * 매칭 · 계정의 키({@code qm:party:*} · {@code qm:user:*} · {@code qm:proposal:*} · {@code qm:gameconfig:*} · {@code qm:auth:*})를 읽거나 쓰는 빈을 여기 두지 마라.
 *
 * <p>구독 추가/해제는 컨테이너에 직접 하지 않고 {@code room.RoomChannelSubscriber} 를 거친다(DM 은 그쪽의 Subscriber). 리스너와 토픽의 짝을 한 곳에서만
 * 관리하기 위해서다 — 불변식 C-4(패턴 구독 금지 · 로컬 소켓이 있을 때만 이름으로 구독)를 지키는 자리가 그 한 곳이다.
 */
@Configuration
public class RedisConfig {

    /**
     * 방별 채널을 동적으로 붙였다 뗐다 할 컨테이너.
     *
     * <p>컨테이너는 Redis 와 전용 연결 하나를 열고 그 위에서 {@code SUBSCRIBE} / {@code UNSUBSCRIBE} 를 실행한다. 리스너 실행 스레드는 컨테이너 것이므로
     * 그 안에서 느린 일을 하면 모든 방의 메시지가 멈춘다 — 소켓에 쓰는 리스너({@code RoomChatListener})는 별도 풀로 넘기는 쪽으로 간다(notification 의
     * {@code PushMessageListener} 와 같은 이유. 첫 버전이 리스너 스레드에서 바로 보내더라도 그 다음에 옮긴다).
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Tomcat 보다 먼저 시작 — 첫 요청 전에 컨테이너가 running 이어야 동시 등록이 안전하다(notification 2026-09-20 재현).
        // chat 은 기동 때 미리 구독하는 채널이 아직 없어 첫 구독이 lazy 다 — 동시 첫 소켓 둘로 구독이 하나인지는 RoomChannelSubscriber 테스트에서 확인한다(2026-10-10 미확인)
        container.setPhase(0);
        return container;
    }
}
