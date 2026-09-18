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
        // 전송은 소켓에 몇 줄 쓰는 짧은 일이라 스레드가 많이 필요 없다.
        // max 는 큐가 가득 찬 뒤에야 쓰인다 (ThreadPoolExecutor 의 규칙). 평소에는 core 로 돈다
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        // 반드시 유한해야 한다. 무제한이면 느린 클라이언트가 많을 때 밀린 알림이 메모리를 다 먹는다
        executor.setQueueCapacity(1000);
        // 거부 정책은 기본값(AbortPolicy, 예외를 던진다)을 그대로 둔다. 넘친 알림은
        // PushMessageListener 가 예외를 잡아 버린다 - 알림은 휘발성이고 클라이언트가 조회로 복구한다.
        // CallerRunsPolicy 로 바꾸지 마라. 넘치는 순간 Redis 리스너 스레드가 직접 전송하게 되어
        // 느린 클라이언트 하나가 모든 사용자의 알림을 막는다 (CLAUDE.md §5)
        executor.initialize();
        return executor;
    }
}
