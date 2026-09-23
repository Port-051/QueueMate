package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.repository.GameAccountRepository;
import com.queuemate.platform.account.repository.GameAccountStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 전적 동기화의 <b>DB 쪽</b>. 메서드 하나가 트랜잭션 하나다 — 게시판의 {@code party.service.PostStore} 와 같은 이유로 나눴다 —
 * 게임사 API 를 기다리는 동안 DB 커넥션을 붙잡지 않는다. {@link GameStatsSyncWorker} 는 트랜잭션 없이 순서만 잡는다.
 *
 * <p><b>{@code source} 는 늘 {@code API} 다</b> — 이 클래스로 들어오는 것은 게임사 API 에서 긁어 온 것뿐이다
 * ({@code SELF} 는 사용자가 적은 값을 담게 될 자리이고 지금은 쓰이지 않는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameStatsStore {

    /** {@code game_account_stats.source} 의 값 — DB 의 CHECK 가 {@code SELF} · {@code API} 만 받는다 */
    static final String SOURCE_API = "API";

    private final GameAccountRepository gameAccountRepository;
    private final GameAccountStatsRepository statsRepository;

    /** 그 사람의 그 게임 계정과 지금 적혀 있는 전적 — <b>쿼리 한 번이다</b>(LEFT JOIN). 없으면 비어 있다 */
    @Transactional(readOnly = true)
    public Optional<GameAccountWithStats> find(Long userId, Game game)
    {
        return gameAccountRepository.findWithStatsByUserIdAndGame(userId, game);
    }

    /**
     * 긁어 온 것을 적는다 — 전적 스냅숏 upsert 와 {@code external_id} 를 <b>한 트랜잭션</b>으로.
     * 스냅숏이 바뀌었는데 식별자만 남는(또는 그 반대) 일이 없게 한다.
     */
    @Transactional
    public void save(Long gameAccountId, StatsSnapshot snapshot, Instant now)
    {
        statsRepository.upsert(gameAccountId, snapshot.games(), snapshot.avgKills(), snapshot.avgDeaths(),
                snapshot.avgAssists(), snapshot.wins(), snapshot.losses(), snapshot.winStreak(),
                snapshot.detail(), SOURCE_API, now);
        if(snapshot.externalId() != null)
        {
            gameAccountRepository.updateExternalId(gameAccountId, snapshot.externalId(), now);
        }
        log.info("전적 스냅숏 저장 gameAccountId={} games={} wins={} losses={} winStreak={}",
                gameAccountId, snapshot.games(), snapshot.wins(), snapshot.losses(), snapshot.winStreak());
    }
}
