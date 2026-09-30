package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.stats.GameStatsSyncWorker.SyncOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

/**
 * <b>로그인 · 재발급 때 낡은 전적을 뒤에서 다시 받는다</b>(2026-09-30 소유자 결정 · {@code contracts/platform-api.md} "전적을 긁는 것" 의 "로그인 때 다시 받기" · P-42).
 * 사용자가 누르던 "전적 갱신"({@code POST …/game-accounts/{game}/refresh} — P-17)을 없애고 이것으로 바꿨다.
 *
 * <p><b>부르는 곳은 넷</b> — 진짜 로그인(소셜 콜백으로 이미 가입한 사람이 로그인 · 소셜 가입(새 사용자라 실제로는 받을 계정이 없다) · 개발용 로그인
 * {@code TEMP-DEV-LOGIN})과 <b>자동 재로그인 {@code POST /api/v1/auth/refresh}</b>(성공한 rotation 만). 셋 다 {@code account.controller} 가
 * 로그인 쿠키를 만든 뒤에 {@link #refreshStale} 을 부른다. 잇기(로그인된 채 온 소셜 콜백)는 로그인이 아니라 부르지 않는다.
 * <b>왜 재발급도인가</b> — access 15분 · refresh 7일이고 refresh 는 쓸 때마다 새 7일이라 자주 여는 사람은 "진짜 로그인"을 거의 하지 않는다.
 *
 * <p><b>무엇을 받나</b> — 그 사람의 게임 계정 가운데 <b>긁는 구현과 키가 있는 게임</b>(LoL · PUBG — VALORANT 는 구현이 없다)이고,
 * <b>마지막으로 받은 뒤 {@link RiotProperties#staleAfter()}(기본 1시간)가 지난 것</b>(전적 줄이 없는 계정 포함)만이다. 1시간은 게임사 키의 한도를 지키는
 * 바닥이다 — LoL 한 번이 Riot 호출 14번(개발용 키는 2분에 100번) · PUBG 는 키 하나에 분당 10번이고, 재발급은 15분마다 온다.
 *
 * <p><b>응답을 기다리지 않고 응답을 실패시키지도 않는다</b> — {@link #refreshStale} 은 전용 풀({@link GameStatsAsyncConfig#LOGIN_EXECUTOR})에 던지고 곧바로 돌아온다.
 * DB 를 읽는 것(어느 계정이 낡았나)도 풀의 스레드가 한다 — 로그인에 지연을 더하지 않는다. <b>어떤 예외도 밖으로 내보내지 않는다.</b>
 * 트랜잭션 안에서 불렸으면 커밋된 뒤에 던진다(지금 부르는 곳은 전부 트랜잭션 밖의 컨트롤러다 — 받침으로 둔다).
 *
 * <p><b>실패하면</b>(게임사 API 거절 · 429 · 응답 없음 · 이름이 없어졌다) WARN 한 줄을 남기고 <b>기존 전적 줄을 그대로 둔다</b>. <b>재시도하지 않는다</b> —
 * 다음 로그인 · 재발급 때 다시 본다(기준이 성공한 받기의 시각이라 실패한 계정은 그때 또 낡은 것으로 보인다). 같은 계정을 두 탭이 거의 같이 재발급해도
 * 자물쇠({@link GameStatsSyncLock} — {@code qm:riot:sync:{gameAccountId}})가 한 번만 긁게 한다(못 잡으면 건너뛴다 · Redis 가 죽으면 락 없이 — 그 클래스의 규칙).
 *
 * <p><b>stateless</b> — 판정은 DB({@code synced_at})와 Redis(자물쇠)에 있다. 풀의 큐가 사라져도(재시작) 잃는 것은 "이번 로그인에 다시 받지 못했다"뿐이다.
 * (세부 — 설정 이름 · 전용 풀 · 한 사람씩 · 넘치면 버리기 · 락 뒤 다시 읽기 — 는 Claude 가 정했다. 계약 P-42 행)
 */
@Slf4j
@Component
public class GameStatsLoginRefresher {

    private final GameStatsSyncWorker worker;
    private final GameStatsStore store;
    private final RiotProperties properties;
    private final ThreadPoolTaskExecutor executor;

    public GameStatsLoginRefresher(GameStatsSyncWorker worker, GameStatsStore store, RiotProperties properties,
                                   @Qualifier(GameStatsAsyncConfig.LOGIN_EXECUTOR) ThreadPoolTaskExecutor executor)
    {
        this.worker = worker;
        this.store = store;
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * 그 사람의 낡은 전적을 <b>뒤에서</b> 다시 받게 한다. 곧바로 돌아오고 <b>예외를 내지 않는다</b> — 로그인 · 재발급 응답은 이것과 무관하다.
     * 키가 있는 게임이 하나도 없으면(개발 환경 · 테스트의 기본 컨텍스트) 풀에 던지지도 않는다.
     */
    public void refreshStale(long userId)
    {
        try
        {
            if(!worker.anyConfigured())
            {
                return;
            }
            if(TransactionSynchronizationManager.isSynchronizationActive())
            {
                // 커밋되기 전에 다른 스레드가 읽으면 방금 적은 것을 못 본다 — 커밋 뒤에 던진다
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit()
                    {
                        submit(userId);
                    }
                });
                return;
            }
            submit(userId);
        }
        catch(RuntimeException e)
        {
            log.warn("로그인 뒤 전적 다시 받기를 걸지 못했다 userId={}: {}", userId, e.toString());
        }
    }

    private void submit(long userId)
    {
        try
        {
            executor.execute(() -> run(userId));
        }
        catch(TaskRejectedException e)
        {
            // 큐(100)가 꽉 찼다 — 몰린 것이다. 버린다(다음 로그인 · 재발급 때 다시 본다)
            log.warn("로그인 뒤 전적 다시 받기를 버렸다 — 전용 풀의 큐가 꽉 찼다 userId={}", userId);
        }
    }

    /** 풀의 스레드에서 돈다. 계정마다 따로 — 한 계정이 실패해도 다음 계정은 받는다 */
    void run(long userId)
    {
        List<GameAccountWithStats> accounts;
        Instant staleBefore = Instant.now().minus(properties.staleAfter());
        try
        {
            accounts = store.findAll(userId);
        }
        catch(RuntimeException e)
        {
            log.warn("로그인 뒤 전적 다시 받기 — 게임 계정을 읽지 못했다 userId={}: {}", userId, e.toString());
            return;
        }
        for(GameAccountWithStats row : accounts)
        {
            if(!worker.configured(row.account().getGame()) || !GameStatsSyncWorker.isStale(row, staleBefore))
            {
                continue;
            }
            Long gameAccountId = row.account().getId();
            try
            {
                SyncOutcome outcome = worker.syncIfStale(row.account(), staleBefore);
                log.info("로그인 뒤 전적 다시 받기 userId={} gameAccountId={} game={} outcome={}",
                        userId, gameAccountId, row.account().getGame(), outcome);
            }
            catch(RuntimeException e)
            {
                // 게임사 API 거절 · 429 · 응답 없음 · 이름이 없어졌다 · DB 오류 — 옛 전적 그대로 두고 재시도하지 않는다
                log.warn("로그인 뒤 전적 다시 받기에 실패했다 — 옛 전적을 그대로 둔다 userId={} gameAccountId={} game={}: {}",
                        userId, gameAccountId, row.account().getGame(), e.toString());
            }
        }
    }
}
