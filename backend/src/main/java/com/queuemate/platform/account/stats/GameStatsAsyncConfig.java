package com.queuemate.platform.account.stats;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 전적을 긁는 <b>전용 풀</b>. 요청 스레드에서 Riot 을 10여 번(경기 10판이면 14번) 기다리면 <b>자를 방법이 없어서</b> 여기에 던지고 상한(30초)만큼 기다린다.
 * 던지는 쪽은 둘이다 — 전적 갱신(2026-09-24)과 <b>LoL 게임 계정 연결</b>(2026-09-27 소유자 결정 — 동기로 긁는다). 둘 다 {@link GameStatsRefresher} 다.
 * (2026-09-27 까지는 게임 계정 저장 뒤의 비동기 갱신이 {@code @Async} 로 여기서 돌았다 — 그 길을 없애며 {@code @EnableAsync} 도 뺐다.)
 *
 * <p><b>작게 두고 넘치면 버린다</b>({@link ThreadPoolExecutor.DiscardPolicy}). 큐가 무한이면 늦게 오는 요청이 계속 쌓이고,
 * 호출자를 돌려 쓰면({@code CallerRunsPolicy}) 요청 스레드가 Riot 을 기다리게 된다 — 둘 다 피한다.
 * 버려지면 그 {@code Future} 가 영원히 완료되지 않는다 — 그래서 던지는 쪽이 큐를 먼저 본다.
 *
 * <p><b>이 큐에 판정이 기대지 않는다</b> — stateless 규칙(CLAUDE.md §5)을 어기지 않는 이유다. 프로세스가 죽으면 대기 중인 갱신은
 * 사라지고, 그때 잃는 것은 "전적이 잠시 낡아 있다"뿐이다. 그래서 종료 때 기다리지도 않는다.
 */
@Configuration
public class GameStatsAsyncConfig {

    /** 빈 이름. 스프링의 공용 실행기를 쓰지 않는다 — 전적이 다른 일을 밀어내지 않게 */
    public static final String EXECUTOR = "gameStatsExecutor";

    /**
     * 돌려주는 형이 {@link Executor} 가 아니라 {@link ThreadPoolTaskExecutor} 인 이유 — <b>전적 갱신 요청이 큐가 꽉 찼는지 보고
     * 던지기 전에 끊는다</b>({@link GameStatsRefresher}). 버려지는 것({@link ThreadPoolExecutor.DiscardPolicy})은 조용해서
     * 던진 쪽이 알 길이 그것뿐이다.
     */
    @Bean(EXECUTOR)
    public ThreadPoolTaskExecutor gameStatsExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("game-stats-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        // 종료 신호를 받으면 돌고 있는 것만 끝내고 큐에 남은 것은 버린다 — 요청을 끝내는 것(graceful shutdown)이 전적보다 먼저다
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
