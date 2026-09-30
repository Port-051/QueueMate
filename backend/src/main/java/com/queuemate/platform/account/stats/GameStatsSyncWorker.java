package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.domain.GameAccountStats;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 전적을 실제로 긁는 곳. 들어오는 문이 <b>둘</b>이다 — <b>아직 저장하지 않은</b> 게임 닉네임으로 긁기만 하는 {@link #fetch}(LoL · PUBG 게임 계정 연결 —
 * 2026-09-27 · 2026-09-29 소유자 결정. 적는 것은 부르는 쪽 {@link GameStatsRefresher#link} 다)와, 이미 있는 게임 계정이 낡았으면 긁어 적는
 * {@link #syncIfStale}(로그인 · 재발급 때 뒤에서 — 2026-09-30 소유자 결정 · P-42. 부르는 쪽은 {@link GameStatsLoginRefresher} 다).
 *
 * <p>(사용자가 누르던 "전적 갱신"의 {@code syncNow} 는 2026-09-30 에 요청과 함께 없앴다 — P-42. 게임 계정을 저장한 뒤 <b>비동기로</b> 긁던
 * {@code sync}({@code @Async})는 2026-09-27 에 없앴다.)
 *
 * <p>{@link #syncIfStale} 의 순서. <b>게임사 API 를 부르기 전에</b> 걸러 낼 것을 다 거른다.
 * <ol>
 *   <li>그 게임의 구현({@link GameStatsProvider})을 찾는다 — 없으면(VALORANT) 끝낸다</li>
 *   <li>Redis 락을 잡는다 — 못 잡으면 <b>줄 서지 않고</b> 끝낸다({@link GameStatsSyncLock}. 연결이 긁고 있거나 다른 탭의 재발급이 먼저 잡았다)</li>
 *   <li><b>락을 잡은 뒤에</b> 게임 계정과 전적을 다시 읽는다(쿼리 한 번) — 계정이 없어졌거나 <b>그 사이에 누가 받아 아직 신선하면</b> 끝낸다.
 *       락이 풀리자마자 같은 계정을 또 긁지 않게 하려는 것이다(탭 둘이 거의 같이 재발급한 경우)</li>
 *   <li>긁어서 upsert 한다. <b>실패하면 기존 전적 줄을 그대로 둔다</b> — 옛 값이라도 있는 편이 낫다(예외는 부르는 쪽이 로그로 끝낸다)</li>
 * </ol>
 */
@Slf4j
@Component
public class GameStatsSyncWorker {

    /** {@link #syncIfStale} 가 한 일 — 부르는 쪽이 로그에 남긴다({@link GameStatsLoginRefresher}) */
    public enum SyncOutcome {
        /** 긁어서 적었다 */
        SAVED,
        /** 아직 신선하다 — 락을 잡고 다시 보니 그 사이에 누가 받았다 */
        FRESH,
        /** 그 게임의 전적을 긁는 구현이 없다(VALORANT) */
        NO_PROVIDER,
        /** 그 게임 계정이 없어졌다(그 사이에 연결을 끊었다) */
        NO_ACCOUNT,
        /** 누가 이미 같은 계정을 긁고 있다 — 건너뛴다 */
        LOCKED,
        /** 물어볼 수 없는 닉네임이다({@code 이름#태그} 가 아니다 · 서버가 없는 옛 PUBG 계정 등) — 게임사 API 를 부르지 않았고 전적 줄도 건드리지 않았다 */
        NOT_FETCHABLE
    }

    private final GameStatsStore store;
    private final GameStatsSyncLock lock;
    private final Map<Game, GameStatsProvider> providers = new EnumMap<>(Game.class);

    public GameStatsSyncWorker(GameStatsStore store, GameStatsSyncLock lock, List<GameStatsProvider> providers)
    {
        this.store = store;
        this.lock = lock;
        providers.forEach(provider -> this.providers.put(provider.game(), provider));
    }

    /** 그 게임의 전적을 긁을 수 있는가 — 부르는 쪽이 <b>게임사 API 를 부르기 전에</b> 거절을 가르는 데 쓴다({@link GameStatsRefresher}) */
    public boolean supports(Game game)
    {
        return providers.containsKey(game);
    }

    /** 그 게임사 API 의 키가 있는가({@link GameStatsProvider#configured()}) — 구현이 없는 게임은 {@code false} 다 */
    public boolean configured(Game game)
    {
        GameStatsProvider provider = providers.get(game);
        return provider != null && provider.configured();
    }

    /** 키가 있는 게임이 하나라도 있는가 — 없으면 로그인 때 다시 받을 일 자체가 없다({@link GameStatsLoginRefresher} 가 풀에 던지기 전에 본다) */
    public boolean anyConfigured()
    {
        return providers.values().stream().anyMatch(GameStatsProvider::configured);
    }

    /**
     * 게임 닉네임 하나로 <b>긁기만 한다</b> — DB 를 읽지도 쓰지도 않고 자물쇠도 보지 않는다(그것은 부르는 쪽 몫이다 — {@link GameStatsRefresher#link}).
     * LoL · PUBG 게임 계정 연결은 <b>긁어서 성공해야 저장한다</b>(2026-09-27 · 2026-09-29 소유자 결정)라서 저장 전의 이름(과 PUBG 의 서버)으로 긁어야 한다.
     * <b>요청 스레드에서 그냥 부르지 마라</b> — 전용 풀에 던져 상한을 건다.
     *
     * @return 긁은 것. 물어볼 수 없는 닉네임이면 {@code null}
     * @throws IllegalStateException    그 게임의 구현이 없다 — 부르는 쪽이 {@link #supports} 로 먼저 거른다
     * @throws RiotIdNotFoundException     그 Riot ID 가 없다
     * @throws PubgPlayerNotFoundException 그 shard 에 그 PUBG 닉네임이 없다
     * @throws RiotApiException            Riot API 가 거절했거나 응답이 없다
     * @throws PubgApiException            PUBG API 가 거절했거나 응답이 없다
     */
    public StatsSnapshot fetch(Game game, String gameNickname, String server)
    {
        GameStatsProvider provider = providers.get(game);
        if(provider == null)
        {
            throw new IllegalStateException(game + " 의 전적을 긁는 구현이 없다");
        }
        return provider.fetch(gameNickname, server);
    }

    /**
     * 그 게임 계정이 <b>{@code staleBefore} 보다 전에 받은 것이면</b>(전적 줄이 없어도) 지금 긁어 적는다 — 로그인 · 재발급 때 뒤에서 다시 받기의 알맹이다
     * (2026-09-30 소유자 결정 · P-42). 순서는 이 클래스 머리의 주석이다.
     *
     * <p><b>{@code @Async} 가 없다 — 어느 스레드에서 돌지는 부르는 쪽이 정한다.</b> 로그인 · 재발급의 요청 스레드에서 그냥 부르지 마라 —
     * Riot 을 10여 번(경기 10판이면 14번) 기다린다. {@link GameStatsLoginRefresher} 가 전용 풀({@link GameStatsAsyncConfig#LOGIN_EXECUTOR})에 던진다.
     * 트랜잭션도 없다 — 읽기 · 쓰기는 {@link GameStatsStore} 가 각자 짧은 트랜잭션으로 한다(긁는 동안 DB 커넥션을 붙잡지 않는다).
     *
     * @param candidate   부르는 쪽이 방금 읽은 게임 계정 — 락의 열쇠(계정 번호)로만 쓰고, 닉네임 · 서버 · 신선도는 락을 잡은 뒤 다시 읽는다
     * @param staleBefore 이보다 전에 받은 전적이면 낡았다(지금 − {@link RiotProperties#staleAfter()})
     * @throws RiotApiException 게임사 API 가 거절했거나 응답이 없다(PUBG 는 {@link PubgApiException} · 이름이 없어졌으면 {@link RiotIdNotFoundException} ·
     *                          {@link PubgPlayerNotFoundException}) — 전적 줄은 건드리지 않았다
     */
    public SyncOutcome syncIfStale(GameAccount candidate, Instant staleBefore)
    {
        Game game = candidate.getGame();
        GameStatsProvider provider = providers.get(game);
        if(provider == null)
        {
            // 긁는 대상이 아닌 게임이다 — VALORANT 는 Riot 의 별도 승인이 필요하다 (CLAUDE.md §7)
            return SyncOutcome.NO_PROVIDER;
        }
        GameStatsSyncLock.Token token = lock.acquire(candidate.getId());
        try
        {
            if(!token.proceed())
            {
                return SyncOutcome.LOCKED;
            }
            Optional<GameAccountWithStats> found = store.find(candidate.getUserId(), game);
            if(found.isEmpty() || !found.get().account().getId().equals(candidate.getId()))
            {
                // 그 사이에 연결을 끊었다(끊고 다시 이었으면 번호가 다르다 — 그 계정은 연결이 방금 긁었다)
                return SyncOutcome.NO_ACCOUNT;
            }
            if(!isStale(found.get(), staleBefore))
            {
                return SyncOutcome.FRESH;
            }
            GameAccount account = found.get().account();
            StatsSnapshot snapshot = provider.fetch(account.getGameNickname(), account.getServer());
            if(snapshot == null)
            {
                // 물어볼 수 없는 닉네임이다(이유는 구현이 로그에 남겼다). 기존 전적 줄은 건드리지 않는다
                return SyncOutcome.NOT_FETCHABLE;
            }
            store.save(account.getId(), game, snapshot, Instant.now().truncatedTo(ChronoUnit.MILLIS));
            return SyncOutcome.SAVED;
        }
        finally
        {
            lock.release(token);
        }
    }

    /**
     * 낡았는가 — 전적 줄이 없거나 마지막으로 받은 때({@code game_account_stats.synced_at})가 {@code staleBefore} 보다 전이다.
     * 기준이 <b>성공한</b> 받기의 시각이라 실패는 기다림을 늘리지 않는다 — 실패한 계정은 다음 로그인 · 재발급 때 다시 본다.
     */
    static boolean isStale(GameAccountWithStats row, Instant staleBefore)
    {
        GameAccountStats stats = row.stats();
        return stats == null || stats.getSyncedAt() == null || stats.getSyncedAt().isBefore(staleBefore);
    }
}
