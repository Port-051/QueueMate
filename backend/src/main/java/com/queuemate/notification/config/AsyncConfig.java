package com.queuemate.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * SSE 전송 전용 스레드 풀과 하트비트 스케줄링을 켠다.
 *
 * <p>Redis 리스너 스레드에서 바로 보내지 않는다 (CLAUDE.md §5). 느린 클라이언트 하나가
 * 리스너를 막으면 모든 사용자의 알림이 멈추므로, 받은 메시지는 이 풀로 넘겨 보낸다.
 *
 * <p>이 풀은 <b>전송</b>만 담당한다. 동시 연결 수의 상한은 이 풀이 아니라 Tomcat
 * {@code server.tomcat.max-connections} 다. 연결 수를 늘리려고 이 풀을 키우지 마라.
 */
@Configuration
@EnableScheduling
public class AsyncConfig {

    /** 전송 풀 빈 이름. {@code @Qualifier} 로 주입받을 때 이 이름을 쓴다. */
    public static final String SSE_PUSH_EXECUTOR = "ssePushExecutor";

    /**
     * 받은 알림을 SSE 연결로 써 주는 풀.
     */
    @Bean(name = SSE_PUSH_EXECUTOR)
    public Executor ssePushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("sse-push-");
        // TODO: 풀 크기(core/max), 큐 용량, 거부 정책을 정한다. 큐가 넘치면 알림은 버려도 되지만 리스너를 막으면 안 된다
        executor.initialize();
        return executor;
    }
}
