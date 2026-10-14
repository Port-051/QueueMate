package com.queuemate.chat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 소켓 전송 전용 스레드 풀과 (뒤에 올 ping 의) 스케줄링을 켠다 — notification 의 {@code config/AsyncConfig} 와 같은 자리다(2026-10-11).
 *
 * <p>Redis 리스너 스레드에서 바로 소켓에 쓰지 않는다. 느린 브라우저 하나가 리스너를 막으면 <b>모든 방</b>의 메시지가 멈추므로,
 * 받은 메시지는 풀로 넘겨 보낸다({@code RoomSockets#send}).
 *
 * <p>notification 과 다른 점 하나 — 알림은 순서가 바뀌어도 되지만 <b>채팅은 같은 방 안에서 순서가 지켜져야 한다</b>(불변식 C-2 "모두 같은 순서").
 * 보통의 풀(스레드 N 개가 큐를 나눠 먹는)은 메시지 1 · 2 를 다른 스레드가 집어 2 가 먼저 나갈 수 있다. 그래서 풀 대신
 * <b>줄(stripe) 여러 개</b>를 둔다 — 줄마다 스레드 하나이고 방은 늘 같은 줄로 간다({@link RoomSendExecutors}). 한 줄 안은 FIFO 라 방 안의 순서가 지켜지고,
 * 느린 브라우저가 막는 범위는 그 줄에 걸린 방들뿐이다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ChatSendProperties.class, ChatProperties.class})
public class AsyncConfig {

    /**
     * 방 채팅 전송 줄 묶음. 줄 수 · 큐 길이는 환경변수로(기본 4 줄 · 줄마다 1000 개).
     * 큐는 반드시 유한하다 — 무제한이면 느린 브라우저가 많을 때 밀린 메시지가 메모리를 다 먹는다. 넘치면 그 메시지는 버린다(방 채팅은 저장하지 않는 휘발성이다 — D-58).
     */
    @Bean
    public RoomSendExecutors roomSendExecutors(ChatSendProperties properties) {
        return new RoomSendExecutors(properties.stripes(), properties.queueCapacity());
    }
}
