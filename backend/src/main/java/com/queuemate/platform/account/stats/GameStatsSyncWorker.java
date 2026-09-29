package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccount;
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
 * 전적을 실제로 긁는 곳. 들어오는 문이 <b>둘</b>이다 — 이미 있는 게임 계정을 긁어 적는 {@link #syncNow}(사용자가 누르는 전적 갱신)와,
 * <b>아직 저장하지 않은</b> 게임 닉네임으로 긁기만 하는 {@link #fetch}(LoL 게임 계정 연결 — 2026-09-27 소유자 결정. 적는 것은 부르는 쪽이다).
 * 둘 다 {@link GameStatsRefresher} 가 전용 풀에 던져 기다린다.
 *
 * <p>(게임 계정을 저장한 뒤 <b>비동기로</b> 긁던 {@code sync}({@code @Async})와 그것을 걸던 {@code GameStatsSync} 는 2026-09-27 에 없앴다 —
 * LoL 은 연결이 동기로 긁고, VALORANT · PUBG 는 구현이 없어 원래 아무것도 하지 않았다.)
 *
 * <p>{@link #syncNow} 의 순서는 넷이다. <b>게임사 API 를 부르기 전에</b> 걸러 낼 것을 다 거른다 — 그 게임의 구현이 없다 · 계정이 없다 · 누가 이미 긁고 있다
 * (키가 없는 경우는 부르는 쪽이 먼저 거른다 — {@link GameStatsRefresher}).
 * <ol>
 *   <li>그 게임의 구현({@link GameStatsProvider})을 찾는다 — 없으면(VALORANT · PUBG) 끝낸다</li>
 *   <li>게임 계정을 읽는다(쿼리 한 번) — 계정이 없으면 끝낸다</li>
 *   <li>Redis 락을 잡는다 — 못 잡으면 <b>줄 서지 않고</b> 끝낸다({@link GameStatsSyncLock})</li>
 *   <li>긁어서 upsert 한다. <b>실패하면 기존 전적 줄을 그대로 둔다</b> — 옛 값이라도 있는 편이 낫다</li>
 * </ol>
 */
@Slf4j
@Component
public class GameStatsSyncWorker {

    /**
     * 한 번 긁고 난 결과. <b>{@link #syncNow} 를 부른 쪽이 응답을 가르는 데 쓴다</b> — "긁지 못했다"의 이유가 사용자에게
     * 404 · 409 · 429 · 503 으로 갈린다({@link GameStatsRefresher}).
     */
    public enum SyncOutcome {
        /** 긁어서 적었다 */
        SAVED,
        /** 그 게임의 전적을 긁는 구현이 없다(VALORANT · PUBG) */
        NO_PROVIDER,
        /** 그 사람에게 그 게임 계정이 없다 */
        NO_ACCOUNT,
        /** 누가 이미 같은 계정을 긁고 있다 */
        LOCKED,
        /** 물어볼 수 없는 닉네임이다({@code 이름#태그} 가 아니다 등) — 게임사 API 를 부르지 않았고 전적 줄도 건드리지 않았다 */
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

    /**
     * 게임 닉네임 하나로 <b>긁기만 한다</b> — DB 를 읽지도 쓰지도 않고 자물쇠도 보지 않는다(그것은 부르는 쪽 몫이다 — {@link GameStatsRefresher#link}).
     * LoL 게임 계정 연결은 <b>긁어서 성공해야 저장한다</b>(2026-09-27 소유자 결정)라서 저장 전의 이름으로 긁어야 한다.
     * {@link #syncNow} 와 같이 <b>요청 스레드에서 그냥 부르지 마라</b> — 전용 풀에 던져 상한을 건다.
     *
     * @return 긁은 것. 물어볼 수 없는 닉네임이면 {@code null}
     * @throws IllegalStateException    그 게임의 구현이 없다 — 부르는 쪽이 {@link #supports} 로 먼저 거른다
     * @throws RiotIdNotFoundException  그 Riot ID 가 없다
     * @throws RiotApiException         게임사 API 가 거절했거나 응답이 없다
     */
    public StatsSnapshot fetch(Game game, String gameNickname)
    {
        GameStatsProvider provider = providers.get(game);
        if(provider == null)
        {
            throw new IllegalStateException(game + " 의 전적을 긁는 구현이 없다");
        }
        return provider.fetch(gameNickname);
    }

    /**
     * 같은 일을 하되 <b>무슨 일이 있었는지 돌려주고 예외를 삼키지 않는다</b> — 사용자가 누른 전적 갱신 요청이
     * 성공/실패를 응답으로 내려 줘야 해서다({@link GameStatsRefresher} — 2026-09-24 소유자 결정).
     *
     * <p><b>{@code @Async} 가 없다 — 어느 스레드에서 돌지는 부르는 쪽이 정한다.</b> {@code @Async} 를 달면 결과를 기다릴 수 없고
     * (돌려주는 값이 {@code Future} 가 된다) 그 요청이 상한(30초)을 스스로 걸 수도 없다. 그래서 갱신 요청은 이것을 <b>전용 풀에 직접 던져</b> 기다린다.
     * <b>요청 스레드에서 그냥 부르지 마라</b> — Riot 을 20여 번 기다리는 동안 그 스레드가 묶이고, 중간에 자를 수도 없다.
     *
     * @throws RiotApiException 게임사 API 가 거절했거나 응답이 없다
     */
    public SyncOutcome syncNow(Long userId, Game game)
    {
        GameStatsProvider provider = providers.get(game);
        if(provider == null)
        {
            // 긁는 대상이 아닌 게임이다 — VALORANT 는 Riot 의 별도 승인, PUBG 는 다른 API 가 필요하다 (CLAUDE.md §7)
            return SyncOutcome.NO_PROVIDER;
        }
        Optional<GameAccountWithStats> found = store.find(userId, game);
        if(found.isEmpty())
        {
            return SyncOutcome.NO_ACCOUNT;
        }
        GameAccount account = found.get().account();

        GameStatsSyncLock.Token token = lock.acquire(account.getId());
        try
        {
            if(!token.proceed())
            {
                return SyncOutcome.LOCKED;
            }
            StatsSnapshot snapshot = provider.fetch(account.getGameNickname());
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
}
