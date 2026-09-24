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
 * 전적을 실제로 긁어 적는 곳 — <b>요청 스레드가 아니라 전용 풀에서 돈다</b>({@link GameStatsAsyncConfig}).
 * 부르는 곳은 {@link GameStatsSync} 하나다(그쪽이 "커밋된 뒤에" 를 맡는다).
 *
 * <p><b>어떤 예외도 밖으로 내보내지 않는다</b> — 전적은 곁가지다. 게임 계정 저장은 이미 성공했고, 실패하면 로그만 남긴다
 * (알림 발행과 같은 원칙 — CLAUDE.md §3.2). 그래서 이 메서드 전체가 {@code try} 안에 있다.
 *
 * <p>순서는 넷이다. <b>게임사 API 를 부르기 전에</b> 걸러 낼 것을 다 거른다 — 키가 없다 · 그 게임의 구현이 없다 · 계정이 없다 ·
 * 누가 이미 긁고 있다.
 * <ol>
 *   <li>그 게임의 구현({@link GameStatsProvider})을 찾는다 — 없으면(VALORANT · PUBG) 조용히 끝낸다</li>
 *   <li>게임 계정을 읽는다(쿼리 한 번) — 계정이 없으면 끝낸다</li>
 *   <li>Redis 락을 잡는다 — 못 잡으면 <b>줄 서지 않고</b> 끝낸다({@link GameStatsSyncLock})</li>
 *   <li>긁어서 upsert 한다. <b>실패하면 기존 전적 줄을 그대로 둔다</b> — 옛 값이라도 있는 편이 낫다</li>
 * </ol>
 */
@Slf4j
@Component
public class GameStatsSyncWorker {

    private final GameStatsStore store;
    private final GameStatsSyncLock lock;
    private final Map<Game, GameStatsProvider> providers = new EnumMap<>(Game.class);

    public GameStatsSyncWorker(GameStatsStore store, GameStatsSyncLock lock, List<GameStatsProvider> providers)
    {
        this.store = store;
        this.lock = lock;
        providers.forEach(provider -> this.providers.put(provider.game(), provider));
    }

    /**
     * 그 사람의 그 게임 전적을 긁는다. <b>신선도를 보지 않는다</b> — 부르는 곳이 게임 계정을 저장한 직후 하나뿐이라
     * 방금 바꾼 계정에 옛 전적이 남으면 안 된다(2026-09-24 소유자 결정 — {@link GameStatsSync}).
     */
    @Async(GameStatsAsyncConfig.EXECUTOR)
    public void sync(Long userId, Game game)
    {
        GameStatsSyncLock.Token token = null;
        try
        {
            GameStatsProvider provider = providers.get(game);
            if(provider == null)
            {
                // 긁는 대상이 아닌 게임이다 — VALORANT 는 Riot 의 별도 승인, PUBG 는 다른 API 가 필요하다 (CLAUDE.md §7)
                log.debug("전적을 긁는 구현이 없는 게임이다 game={}", game);
                return;
            }
            Optional<GameAccountWithStats> found = store.find(userId, game);
            if(found.isEmpty())
            {
                log.debug("게임 계정이 없어 전적을 긁지 않는다 userId={} game={}", userId, game);
                return;
            }
            GameAccount account = found.get().account();

            token = lock.acquire(account.getId());
            if(!token.proceed())
            {
                log.debug("이미 누가 긁고 있어 건너뛴다 gameAccountId={}", account.getId());
                return;
            }

            StatsSnapshot snapshot = provider.fetch(account.getGameNickname());
            if(snapshot == null)
            {
                // 긁을 수 없는 닉네임이다(이유는 구현이 로그에 남겼다). 기존 전적 줄은 건드리지 않는다
                return;
            }
            store.save(account.getId(), snapshot, Instant.now().truncatedTo(ChronoUnit.MILLIS));
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
        finally
        {
            lock.release(token);
        }
    }
}
