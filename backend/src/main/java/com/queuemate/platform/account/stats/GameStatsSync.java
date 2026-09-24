package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * <b>전적 동기화를 거는 창구</b> — 안에서 무슨 일이 일어나는지는 {@link GameStatsSyncWorker} 다.
 *
 * <p><b>긁는 시점은 하나다 — 게임 계정을 연결 · 수정할 때</b>({@code PUT …/game-accounts/{game}}. 2026-09-24 소유자 결정 ·
 * {@code contracts/platform-api.md} "전적을 긁는 것"). 2026-09-23 에는 <b>모집 글을 쓸 때</b>도 긁었고 그쪽만 {@code synced_at} 이
 * 신선하면 건너뛰었다 — 긁는 것이 비동기라 방금 쓴 글의 응답에 반영되지 않는데 Riot 호출 20여 번을 쓴다는 이유로 되물렸다.
 * <b>그래서 신선도를 보는 장치도 함께 없앴다</b>(넘기는 곳이 사라져 죽은 코드가 됐다).
 *
 * <p><b>커밋된 뒤에 시작한다</b>({@code common.push.PushPublisher} 와 같은 방식) — 되돌려진 변경의 전적을 긁지 않고,
 * 긁는 쪽이 방금 저장한 게임 계정을 반드시 볼 수 있게 한다. 트랜잭션 밖에서 부르면 바로 건다.
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

    /** 게임 계정을 연결 · 수정했다. <b>무조건 긁는다</b> — 닉네임이 바뀌었을 수 있어 옛 전적을 그대로 두면 안 된다 */
    public void afterGameAccountLinked(Long userId, Game game)
    {
        start(userId, game);
    }

    private void start(Long userId, Game game)
    {
        if(!properties.configured())
        {
            return;
        }
        if(!TransactionSynchronizationManager.isSynchronizationActive())
        {
            worker.sync(userId, game);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit()
            {
                worker.sync(userId, game);
            }
        });
    }
}
