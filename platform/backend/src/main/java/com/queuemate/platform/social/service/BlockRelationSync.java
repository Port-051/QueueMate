package com.queuemate.platform.social.service;

import com.queuemate.platform.social.BlockProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.time.Duration;

/**
 * 차단 관계 사본을 언제 다시 만드는가(2026-10-02 · docs/11 D-57 · {@code contracts/platform-api.md} P-52) — <b>① 뜰 때마다 한 번 ② 주기적으로</b>
 * ({@code platform.block.redis-sync-interval} ← {@code BLOCK_REDIS_SYNC_INTERVAL}, 기본 {@code PT5M}). 하는 일은 {@link BlockRelationRedis#rebuild}.
 *
 * <p><b>왜 다시 만드는가</b> — 사본은 차단 · 해제가 그때그때 고치지만, Redis 의 장애 조치(복제가 비동기라 마지막 몇 ms 의 쓰기를 잃을 수 있다) · 비어서 뜬 Redis ·
 * 커밋하지 못한 차단이 남긴 더 막기 · 탈퇴의 정리 실패는 그 길로 고쳐지지 않는다. 그 어긋남이 <b>길어야 주기만큼</b> 남는다(기동 직후에는 곧바로 맞춘다).
 *
 * <p><b>실패해도 기동을 막지 않는다</b> — Redis(또는 DB)에 닿지 못하면 WARN 한 줄을 남기고 넘어간다. 다음 주기에 다시 한다. 태스크가 여럿이어도 저마다 돈다 —
 * 같은 표에서 같은 결과라 멱등이고, 사본을 바꾸는 트랜잭션은 한 줄로 서므로({@code BlockRepository#lockRelationCopy}) 겹치지 않는다.
 *
 * <p>{@code @Scheduled} 대신 {@link SchedulingConfigurer} 로 거는 것은 <b>{@code PT0S} 면 끄기</b> 위해서다 — 고정 지연 0 은 스케줄러가 받지 않는다.
 * 첫 주기는 기동 뒤 한 주기가 지나서다(기동 때의 한 번과 겹치지 않게).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockRelationSync implements ApplicationRunner, SchedulingConfigurer {

    private final BlockRelationRedis blockRelationRedis;
    private final BlockProperties blockProperties;

    /** 기동 때 한 번 — 컨텍스트가 다 뜬 뒤 · 요청을 받을 준비(readiness)가 되기 전이다 */
    @Override
    public void run(ApplicationArguments args)
    {
        rebuildQuietly(true);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar)
    {
        Duration interval = blockProperties.redisSyncInterval();
        if(interval == null || interval.isZero() || interval.isNegative())
        {
            log.info("차단 관계 사본의 주기적 재구성을 끈다 — platform.block.redis-sync-interval={} (기동 때 한 번은 한다)", interval);
            return;
        }
        registrar.addFixedDelayTask(new FixedDelayTask(() -> rebuildQuietly(false), interval, interval));
    }

    /**
     * 다시 만들고 결과를 남긴다. <b>예외를 밖으로 내지 않는다</b>(DB · Redis 에 닿지 못한 것만 — 코드의 잘못은 그대로 올라간다).
     * 기동 때는 INFO, 주기는 지운 키가 있을 때만 INFO(사본이 어긋나 있었다는 신호다) · 아니면 DEBUG.
     */
    void rebuildQuietly(boolean atStartup)
    {
        try
        {
            BlockRelationRedis.Rebuilt rebuilt = blockRelationRedis.rebuild();
            if(atStartup || rebuilt.removedKeys() > 0)
            {
                log.info("차단 관계 사본을 다시 만들었다({}) — 사용자 {} · 표에 없어 지운 키 {}",
                        atStartup ? "기동" : "주기", rebuilt.users(), rebuilt.removedKeys());
            }
            else
            {
                log.debug("차단 관계 사본을 다시 만들었다(주기) — 사용자 {}", rebuilt.users());
            }
        }
        catch(DataAccessException | TransactionException e)
        {
            log.warn("차단 관계 사본을 다시 만들지 못했다({}) — 다음 주기에 다시 한다: {}", atStartup ? "기동" : "주기", e.toString());
        }
    }
}
