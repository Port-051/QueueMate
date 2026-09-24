package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 전적을 실제로 긁어 적는 곳. 들어오는 문이 <b>둘</b>이다 — 성공/실패를 아무도 기다리지 않는 {@link #sync}(게임 계정 저장 뒤의 비동기 갱신)와,
 * 같은 일을 하고 <b>결과를 돌려주는</b> {@link #syncNow}(사용자가 누르는 전적 갱신 — {@link GameStatsRefresher}).
 * <b>긁는 알맹이는 하나다</b> — 두 길이 같은 순서 · 같은 자물쇠를 쓰게 하려고 갈라 두었다.
 *
 * <p>순서는 넷이다. <b>게임사 API 를 부르기 전에</b> 걸러 낼 것을 다 거른다 — 그 게임의 구현이 없다 · 계정이 없다 · 누가 이미 긁고 있다
 * (키가 없는 경우는 부르는 쪽이 먼저 거른다 — {@link GameStatsSync} · {@link GameStatsRefresher}).
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
     * 404 · 409 · 429 · 503 으로 갈린다({@link GameStatsRefresher}). {@link #sync} 는 로그에만 쓴다.
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
     * 그 사람의 그 게임 전적을 긁는다 — <b>아무도 결과를 기다리지 않는 길</b>(게임 계정 저장 뒤의 비동기 갱신. 부르는 곳은 {@link GameStatsSync} 하나다).
     * <b>신선도를 보지 않는다</b> — 방금 바꾼 계정에 옛 전적이 남으면 안 된다(2026-09-24 소유자 결정 — {@link GameStatsSync}).
     *
     * <p><b>어떤 예외도 밖으로 내보내지 않는다</b> — 전적은 곁가지다. 게임 계정 저장은 이미 성공했고, 실패하면 로그만 남긴다
     * (알림 발행과 같은 원칙 — CLAUDE.md §3.2). <b>삼키는 것은 이 메서드뿐이다</b> — 결과를 아는 쪽이 필요한 길은 {@link #syncNow} 를 쓴다.
     */
    @Async(GameStatsAsyncConfig.EXECUTOR)
    public void sync(Long userId, Game game)
    {
        try
        {
            SyncOutcome outcome = syncNow(userId, game);
            if(outcome != SyncOutcome.SAVED)
            {
                log.debug("전적을 긁지 않았다 userId={} game={} outcome={}", userId, game, outcome);
            }
        }
        catch(RiotApiException e)
        {
            // 429 는 재시도하지 않는다 — 개발용 키는 2분당 100회라 되풀이해도 소용이 없다. 다음 갱신 때 다시 긁는다
            log.warn("전적을 긁지 못했다{} userId={} game={}: {}", e.rateLimited() ? "(rate limit)" : "", userId, game, e.getMessage());
        }
        catch(Exception e)
        {
            log.warn("전적을 긁다 예상하지 못한 실패 userId={} game={}: {}", userId, game, e.toString());
        }
    }

    /**
     * 같은 일을 하되 <b>무슨 일이 있었는지 돌려주고 예외를 삼키지 않는다</b> — 사용자가 누른 전적 갱신 요청이
     * 성공/실패를 응답으로 내려 줘야 해서다({@link GameStatsRefresher} — 2026-09-24 소유자 결정).
     *
     * <p><b>{@code @Async} 가 없다 — 어느 스레드에서 돌지는 부르는 쪽이 정한다.</b> 여기에 {@code @Async} 를 달면 결과를 기다릴 수 없고
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
            store.save(account.getId(), snapshot, Instant.now().truncatedTo(ChronoUnit.MILLIS));
            return SyncOutcome.SAVED;
        }
        finally
        {
            lock.release(token);
        }
    }
}
