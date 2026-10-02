package com.queuemate.platform.account.stats;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 전적을 긁는 <b>전용 풀 둘</b>. 스프링의 공용 실행기를 쓰지 않는다 — 전적이 다른 일을 밀어내지 않게.
 * <ul>
 *   <li>{@link #EXECUTOR} — <b>LoL · PUBG 게임 계정 연결</b>(2026-09-27 · 2026-09-29 소유자 결정 — 저장하기 전에 동기로 긁는다).
 *       요청 스레드에서 Riot 을 10여 번(경기 10판이면 14번) 기다리면 <b>자를 방법이 없어서</b> 여기에 던지고 상한(30초)만큼 기다린다({@link GameStatsRefresher})</li>
 *   <li>{@link #LOGIN_EXECUTOR} — <b>로그인 · 재발급 때 뒤에서 다시 받기</b>(2026-09-30 소유자 결정 · P-42 — {@link GameStatsLoginRefresher}).
 *       아무도 기다리지 않는 일이다</li>
 * </ul>
 * <b>둘을 나눈 이유</b> — 로그인이 몰려 뒤의 다시 받기가 큐를 채우면 사용자가 기다리는 연결이 "큐가 꽉 찼다"로 503 이 된다. 풀이 따로면 서로 밀어내지 않는다.
 * (2026-09-24 ~ 09-30 에는 사용자가 누르는 "전적 갱신"도 {@link #EXECUTOR} 에 던졌다 — P-42 로 그 요청이 없어졌다.
 * 2026-09-27 까지는 게임 계정 저장 뒤의 비동기 갱신이 {@code @Async} 로 여기서 돌았다 — 그 길을 없애며 {@code @EnableAsync} 도 뺐다.)
 *
 * <p><b>작게 둔다.</b> 큐가 무한이면 늦게 오는 일이 계속 쌓이고, 호출자를 돌려 쓰면({@code CallerRunsPolicy}) 요청 스레드가 Riot 을 기다리게 된다 — 둘 다 피한다.
 *
 * <p><b>이 큐에 판정이 기대지 않는다</b> — stateless 규칙(CLAUDE.md §5)을 어기지 않는 이유다. 프로세스가 죽으면 대기 중인 일은
 * 사라지고, 그때 잃는 것은 "전적이 잠시 낡아 있다"뿐이다(다음 로그인 · 재발급 때 다시 본다). 그래서 종료 때 기다리지도 않는다.
 */
@Configuration
public class GameStatsAsyncConfig {

    /** 게임 계정 연결의 풀 — 빈 이름 */
    public static final String EXECUTOR = "gameStatsExecutor";

    /** 로그인 · 재발급 때 뒤에서 다시 받는 풀 — 빈 이름(2026-09-30 · P-42) */
    public static final String LOGIN_EXECUTOR = "gameStatsLoginExecutor";

    /**
     * 돌려주는 형이 {@link Executor} 가 아니라 {@link ThreadPoolTaskExecutor} 인 이유 — <b>연결 요청이 큐가 꽉 찼는지 보고
     * 던지기 전에 끊는다</b>({@link GameStatsRefresher}). 버려지는 것({@link ThreadPoolExecutor.DiscardPolicy})은 조용해서
     * 던진 쪽이 알 길이 그것뿐이다. 버려지면 그 {@code Future} 가 영원히 완료되지 않는다 — 그래서 던지는 쪽이 큐를 먼저 본다.
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

    /**
     * <b>스레드 하나 · 큐 100</b> — 한 번에 한 사람씩 차례로 긁는다. 게임사 API 의 한도(Riot 개발용 키는 지역마다 2분에 100회 · LoL 한 번이 14번,
     * PUBG 는 키 하나에 분당 10회)를 로그인이 한꺼번에 태우지 않게 하려는 것이다 — 한도를 지키는 것은 "1시간이 지난 계정만"(P-42)이고, 이것은 몰릴 때의 완충이다.
     * <b>넘치면 거절하고(기본 {@code AbortPolicy}) 던지는 쪽이 WARN 한 줄을 남긴 뒤 버린다</b>({@link GameStatsLoginRefresher}) —
     * 기다리는 사람이 없어 {@code Future} 가 필요 없고, 거절을 조용히 삼키면 왜 안 갱신됐는지 알 길이 없다.
     */
    @Bean(LOGIN_EXECUTOR)
    public ThreadPoolTaskExecutor gameStatsLoginExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("game-stats-login-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
