package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.domain.GameTiers;
import com.queuemate.platform.account.repository.GameAccountRepository;
import com.queuemate.platform.account.repository.GameAccountStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
     * 그 사람의 게임 계정 <b>전부</b>와 지금 적혀 있는 전적 — <b>쿼리 한 번이다</b>(LEFT JOIN · 게임 이름순). 로그인 · 재발급 때 다시 받을 계정을
     * 고르는 데 쓴다({@link GameStatsLoginRefresher} — P-42). 없으면 빈 목록이다.
     */
    @Transactional(readOnly = true)
    public List<GameAccountWithStats> findAll(Long userId)
    {
        return gameAccountRepository.findWithStatsByUserId(userId);
    }

    /**
     * 긁어 온 것을 적는다 — 전적 스냅숏 upsert 와 게임 계정 줄의 {@code external_id} · 사다리별 티어를 <b>한 트랜잭션</b>으로.
     * 스냅숏이 바뀌었는데 식별자만 남는(또는 그 반대) 일이 없게 한다. 로그인 · 재발급 때 뒤에서 다시 받기({@link GameStatsSyncWorker#syncIfStale} — P-42)의 길이다 —
     * {@code game_nickname} 은 건드리지 않는다(사용자가 적은 값이다).
     */
    @Transactional
    public void save(Long gameAccountId, Game game, StatsSnapshot snapshot, Instant now)
    {
        write(gameAccountId, game, snapshot, now);
    }

    /**
     * <b>LoL · PUBG 게임 계정 연결</b>(2026-09-27 · 2026-09-29 소유자 결정) — 게임 계정 줄 upsert(이름 · 서버 · 게임사 API 의 사다리별 티어)와 긁어 온 것을
     * <b>한 트랜잭션</b>으로 적는다. 게임사 API 를 다 긁은 <b>뒤에</b> 부른다 — 긁는 동안 커넥션을 붙잡지 않는다({@link GameStatsRefresher#link}).
     * {@code server} 는 PUBG 만 값이 있다(LoL 은 {@code null} — DB 의 CHECK 도 PUBG 만 받는다).
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 그 사용자가 DB 에 없다(FK) — 부르는 쪽이 401 로 옮긴다
     */
    @Transactional
    public void link(Long userId, Game game, String gameNickname, String server, StatsSnapshot snapshot, Instant now)
    {
        String tiers = GameTiers.write(game, snapshot.tiers());
        gameAccountRepository.upsert(userId, game.name(), gameNickname, tiers, server, now);
        Long gameAccountId = gameAccountRepository.findIdByUserIdAndGame(userId, game)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 게임 계정이 없다 userId=" + userId + " game=" + game));
        write(gameAccountId, game, snapshot, now);
    }

    private void write(Long gameAccountId, Game game, StatsSnapshot snapshot, Instant now)
    {
        String tiers = GameTiers.write(game, snapshot.tiers());
        statsRepository.upsert(gameAccountId, snapshot.games(), snapshot.avgKills(), snapshot.avgDeaths(),
                snapshot.avgAssists(), snapshot.wins(), snapshot.losses(), snapshot.winStreak(),
                snapshot.detail(), SOURCE_API, now);
        gameAccountRepository.applyFetchedProfile(gameAccountId, snapshot.externalId(), tiers, now);
        log.info("전적 스냅숏 저장 gameAccountId={} games={} wins={} losses={} winStreak={} tiers={}",
                gameAccountId, snapshot.games(), snapshot.wins(), snapshot.losses(), snapshot.winStreak(), tiers);
    }
}
