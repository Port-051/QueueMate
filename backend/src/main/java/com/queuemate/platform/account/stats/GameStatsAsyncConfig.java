package com.queuemate.platform.account.stats;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 전적을 긁는 <b>전용 풀</b>. 게임사 API 를 부르는 데 수 초가 걸려서 응답을 그만큼 붙잡을 수 없다 — 요청은 바로 끝내고 여기서 긁는다.
 *
 * <p><b>작게 두고 넘치면 버린다</b>({@link ThreadPoolExecutor.DiscardPolicy}) — 전적은 곁가지다. 버려진 갱신은 다음 기회에 다시 긁힌다
 * (게임 계정을 다시 연결할 때 · 글을 쓸 때 {@code synced_at} 이 낡아 있을 때). 큐가 무한이면 늦게 오는 요청이 계속 쌓이고,
 * 호출자를 돌려 쓰면({@code CallerRunsPolicy}) 요청 스레드가 Riot 을 기다리게 된다 — 둘 다 피한다.
 *
 * <p><b>이 큐에 판정이 기대지 않는다</b> — stateless 규칙(CLAUDE.md §5)을 어기지 않는 이유다. 프로세스가 죽으면 대기 중인 갱신은
 * 사라지고, 그때 잃는 것은 "전적이 잠시 낡아 있다"뿐이다. 그래서 종료 때 기다리지도 않는다.
 */
@Configuration
@EnableAsync
public class GameStatsAsyncConfig {

    /** {@code @Async} 가 가리키는 이름. 스프링의 공용 실행기를 쓰지 않는다 — 전적이 다른 일을 밀어내지 않게 */
    public static final String EXECUTOR = "gameStatsExecutor";

    @Bean(EXECUTOR)
    public Executor gameStatsExecutor()
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
