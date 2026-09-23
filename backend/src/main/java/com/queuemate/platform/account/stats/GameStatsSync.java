package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * <b>전적 동기화를 거는 창구</b> — {@code account} 밖(글 쓰기)에서도 이것만 부른다({@code account} 의 {@code UserReader} ·
 * {@code GameProfileReader} 와 같은 자리다 — CLAUDE.md §4). 안에서 무슨 일이 일어나는지는 {@link GameStatsSyncWorker} 다.
 *
 * <p><b>긁는 시점은 둘이다</b>(2026-09-23 소유자 결정 · {@code contracts/platform-api.md} "게임 프로필").
 * <ul>
 *   <li>{@link #afterGameAccountLinked} — 게임 계정을 연결할 때({@code PUT …/game-accounts/{game}}). <b>신선도를 보지 않는다</b></li>
 *   <li>{@link #afterPostCreated} — 모집 글을 쓸 때({@code POST /posts}). 방장의 그 게임 계정을 보고 <b>{@code synced_at} 이 신선하면 건너뛴다</b></li>
 * </ul>
 *
 * <p><b>커밋된 뒤에 시작한다</b>({@code common.push.PushPublisher} 와 같은 방식) — 되돌려진 변경(409 로 끝난 글 쓰기 등)의 전적을 긁지 않고,
 * 긁는 쪽이 방금 쓴 게임 계정을 반드시 볼 수 있게 한다. 트랜잭션 밖에서 부르면 바로 건다.
 *
 * <p><b>키가 없으면 아무것도 하지 않는다</b>({@code RIOT_API_KEY}) — 기동은 정상이고 {@code stats} 가 {@code null} 로 남는다.
 * 그 판단을 여기서 하므로 키가 없을 때는 전용 풀에 일이 쌓이지도 않는다.
 */
@Slf4j
@Component
@EnableConfigurationProperties(RiotProperties.class)
public class GameStatsSync {

    private final RiotProperties properties;
    private final GameStatsSyncWorker worker;

    public GameStatsSync(RiotProperties properties, GameStatsSyncWorker worker)
    {
        this.properties = properties;
        this.worker = worker;
        if(properties.configured())
        {
            log.info("게임 전적 동기화가 켜져 있다 — {}", properties);
        }
        else
        {
            // 키를 찍지 않는다. 환경변수의 이름만 남긴다
            log.info("RIOT_API_KEY 가 없어 게임 전적을 긁지 않는다 — 게임 프로필의 stats 는 null 로 남는다");
        }
    }

    /** 게임 계정을 연결 · 수정했다. <b>신선도를 보지 않고 긁는다</b> — 닉네임이 바뀌었을 수 있어 옛 전적을 그대로 두면 안 된다 */
    public void afterGameAccountLinked(Long userId, Game game)
    {
        start(userId, game, false);
    }

    /** 모집 글을 썼다. 그 글의 게임에 연결한 방장의 게임 계정을 본다 — 신선하면 건너뛴다(글마다 Riot 을 부르지 않는다) */
    public void afterPostCreated(Long hostId, Game game)
    {
        start(hostId, game, true);
    }

    private void start(Long userId, Game game, boolean respectFreshness)
    {
        if(!properties.configured())
        {
            return;
        }
        if(!TransactionSynchronizationManager.isSynchronizationActive())
        {
            worker.sync(userId, game, respectFreshness);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit()
            {
                worker.sync(userId, game, respectFreshness);
            }
        });
    }
}
