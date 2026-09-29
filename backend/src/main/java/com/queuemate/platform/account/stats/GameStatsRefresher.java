package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.account.stats.GameStatsSyncWorker.SyncOutcome;
import com.queuemate.platform.common.error.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <b>사용자가 눌러서 하는 전적 갱신</b>({@code POST /api/v1/users/me/game-accounts/{game}/refresh} — 2026-09-24 소유자 결정 ·
 * {@code contracts/platform-api.md} "전적을 긁는 것"). 게임 계정을 저장할 때만 갱신되던 것을 <b>원할 때</b> 하는 길이다.
 *
 * <p><b>동기다 — 다 긁을 때까지 기다렸다가 최신을 준다</b>(소유자 결정). 알맹이는 {@link GameStatsSyncWorker#syncNow} 다.
 *
 * <p><b>LoL · PUBG 게임 계정 연결도 이 클래스가 한다</b>({@link #link} — 2026-09-27 · PUBG 는 2026-09-29 소유자 결정 "LoL 처럼 동기로"). 같은 풀 · 같은 상한 · 같은 자물쇠를 쓰고
 * <b>쿨타임은 적용하지 않는다</b>(쿨타임은 전적 갱신 요청의 것이다). 거절의 갈래는 {@link #link} 의 주석에 있다.
 * <b>상한 · 쿨타임 · 자물쇠는 게임을 가리지 않는다</b> — 수치는 {@link RiotProperties} 의 {@code refresh-timeout} · {@code refresh-cooldown} 이고 키는
 * {@code qm:riot:sync:{gameAccountId}} · {@code qm:riot:refresh:{gameAccountId}} 다(이름에 {@code riot} 이 들었지만 게임 계정 번호가 게임을 가른다 — 새 접두사를 두지 않았다).
 *
 * <p><b>상한(30초)을 어떻게 거나 — 전용 풀에 던지고 {@link Future#get(long, TimeUnit)} 으로 기다린다.</b>
 * 요청 스레드에서 그냥 긁으면 <b>자를 방법이 없다</b>(Riot 호출 14번 × 읽기 타임아웃 3초 — 경기 10판). 상한을 넘기면 요청은 실패로 끝내지만
 * <b>돌던 작업은 자르지 않는다</b> — 자물쇠가 중복을 막고 있고, 잠시 뒤에 끝나면 전적은 갱신된다(다음 조회에서 보인다).
 * 대가 — 그 풀은 스레드 둘 · 큐 50 · {@code DiscardPolicy} 라 <b>넘치면 조용히 버려진다</b>({@code RejectedExecutionException} 이 아니다).
 * 버려지면 {@code Future} 가 영원히 완료되지 않아 30초를 기다린 뒤 503 이 되므로, <b>큐가 꽉 찼으면 던지기 전에 503 으로 끊는다</b>
 * (그 검사와 실제 던지기 사이의 경쟁은 남는다 — 그때 남는 것이 30초 기다림이다).
 *
 * <p><b>거절은 넷이다</b>(상태 코드는 소유자가 정했고 <b>에러 코드의 이름과 글귀는 Claude 가 정했다</b> — 계약의 "전적을 긁는 것").
 * <ul>
 *   <li>404 {@code GAME_ACCOUNT_NOT_FOUND} — 그 게임 계정을 연결하지 않았다</li>
 *   <li>409 {@code GAME_STATS_NOT_SUPPORTED} — 그 게임은 긁는 구현이 없다(VALORANT — PUBG 는 2026-09-29 부터 된다). <b>200 을 주면 거짓말이다</b> — 아무것도 갱신되지 않는다.
 *       요청이 잘못된 것이 아니라 서버가 못 하는 것이라 400 이 아니다. <b>쿨타임을 소모하지 않는다</b></li>
 *   <li>429 {@code TOO_MANY_STATS_REFRESHES} + {@code Retry-After} — 쿨타임(2분) 안에 또 불렀거나 <b>누가 이미 같은 계정을 긁고 있다</b></li>
 *   <li>503 {@code GAME_STATS_UNAVAILABLE} — 지금 전적을 가져올 수 없다. <b>전적 줄은 건드리지 않는다</b>(옛 값이 남는다).
 *       게임사 API 가 거절 · 응답이 없다 · 30초를 넘겼다 · 키({@code RIOT_API_KEY} · {@code PUBG_API_KEY})가 없다 · 닉네임이 {@code 이름#태그} 가 아니거나
 *       PUBG 계정에 서버가 없어 물어볼 수도 없다 — <b>이유를 가르지 않는다</b>(PUBG 의 429 만 {@code Retry-After} 를 싣는다 — 2026-09-29). 사용자가 할 수 있는 것은 "잠시 뒤 다시" 또는 "게임 닉네임을 고친다" 둘뿐이고, 갈라 주면 프런트가 갈래마다 다르게 그려야 한다</li>
 * </ul>
 */
@Slf4j
@Component
public class GameStatsRefresher {

    static final String GAME_ACCOUNT_NOT_FOUND = "GAME_ACCOUNT_NOT_FOUND";
    static final String NOT_SUPPORTED = "GAME_STATS_NOT_SUPPORTED";
    static final String TOO_MANY_REFRESHES = "TOO_MANY_STATS_REFRESHES";
    static final String UNAVAILABLE = "GAME_STATS_UNAVAILABLE";
    /** LoL 게임 계정 연결에서 그 Riot ID({@code 이름#태그})가 Riot 에 없다 — 2026-09-27. 이름과 글귀는 Claude 가 정했다 */
    static final String RIOT_ID_NOT_FOUND = "RIOT_ID_NOT_FOUND";
    /** PUBG 게임 계정 연결에서 그 서버(shard)에 그 닉네임이 없다 — 2026-09-29(P-36). 이름은 계약에서 정해졌고 글귀는 Claude 가 정했다 */
    static final String PUBG_PLAYER_NOT_FOUND = "PUBG_PLAYER_NOT_FOUND";

    private final RiotProperties properties;
    private final GameStatsSyncWorker worker;
    private final GameStatsStore store;
    private final GameStatsRefreshCooldown cooldown;
    private final GameStatsSyncLock lock;
    private final ThreadPoolTaskExecutor executor;

    public GameStatsRefresher(RiotProperties properties, GameStatsSyncWorker worker, GameStatsStore store,
                              GameStatsRefreshCooldown cooldown, GameStatsSyncLock lock,
                              @Qualifier(GameStatsAsyncConfig.EXECUTOR) ThreadPoolTaskExecutor executor)
    {
        this.properties = properties;
        this.worker = worker;
        this.store = store;
        this.cooldown = cooldown;
        this.lock = lock;
        this.executor = executor;
        if(properties.configured())
        {
            log.info("게임 전적 동기화가 켜져 있다 — {}", properties);
        }
        else
        {
            // 키를 찍지 않는다. 환경변수의 이름만 남긴다
            log.info("RIOT_API_KEY 가 없어 게임 전적을 긁지 않는다 — LoL 게임 계정 연결 · 전적 갱신이 503 이다");
        }
    }

    /**
     * <b>LoL · PUBG 게임 계정 연결</b>({@code PUT …/game-accounts/LOL} · {@code …/PUBG} — 2026-09-27 · 2026-09-29 소유자 결정). 저장하기 <b>전에</b> 게임사 API 를 긁고,
     * 성공하면 게임 계정 줄(이름 · 서버 · 사다리별 티어 · {@code external_id})과 전적을 <b>한 트랜잭션</b>으로 적은 뒤 다시 읽어 돌려준다.
     * <b>실패하면 아무것도 적지 않는다</b> — 연동이 안 된 것이다.
     *
     * <p>거절 — 404 {@code RIOT_ID_NOT_FOUND}(그 이름#태그가 Riot 에 없다) · 404 {@code PUBG_PLAYER_NOT_FOUND}(그 서버에 그 PUBG 닉네임이 없다) ·
     * 429 {@code TOO_MANY_STATS_REFRESHES}(누가 이미 같은 계정을 긁고 있다) ·
     * 503 {@code GAME_STATS_UNAVAILABLE}(게임사 API 거절 · 429 · 응답 없음 · 30초 초과 · 키({@code RIOT_API_KEY} · {@code PUBG_API_KEY}) 없음 · 응답에 식별자가 없다.
     * <b>PUBG 의 429 는 {@code Retry-After} 를 싣는다</b> — 한도가 풀리는 때까지).
     * <b>쿨타임은 보지도 찍지도 않는다.</b>
     *
     * <p><b>긁기는 전용 풀 · 저장은 이 스레드다</b> — 전적 갱신과 다르다. 30초를 넘겨 이 요청이 503 으로 끝났는데 뒤에서 긁기가 끝나 저장해 버리면
     * "503 을 받았는데 연결돼 있다"가 된다. 그래서 풀에서는 긁기만 하고, 상한 안에 돌아온 것만 이 스레드가 적는다(늦게 끝난 긁기는 버려진다).
     * 자물쇠도 이 스레드가 잡고 푼다 — 게임 계정이 <b>이미 있을 때만</b> 잡는다(키가 계정 번호라 처음 연결에는 잡을 것이 없다.
     * 처음 연결이 동시에 둘 와도 upsert 가 한 줄만 남긴다).
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 그 사용자가 DB 에 없다(FK) — 부르는 쪽이 401 로 옮긴다
     */
    public GameAccountWithStats link(Long userId, Game game, String gameNickname, String server)
    {
        if(!worker.supports(game))
        {
            throw new IllegalStateException(game + " 은 게임사 API 로 연결하지 않는다");
        }
        if(!worker.configured(game))
        {
            log.warn("게임 계정 연결 요청을 받았지만 그 게임사 API 키(LOL — RIOT_API_KEY · PUBG — PUBG_API_KEY)가 없다 userId={} game={}", userId, game);
            throw unavailable();
        }
        Optional<Long> existingId = store.find(userId, game).map(found -> found.account().getId());
        GameStatsSyncLock.Token token = existingId.map(lock::acquire).orElse(null);
        try
        {
            if(token != null && !token.proceed())
            {
                log.info("게임 계정 연결 거절(이미 긁는 중) userId={} gameAccountId={}", userId, existingId.get());
                throw tooManyRefreshes(GameStatsSyncLock.LOCK_TTL.toSeconds());
            }
            StatsSnapshot snapshot = await(() -> worker.fetch(game, gameNickname, server), "게임 계정 연결 userId=" + userId, true);
            if(snapshot == null)
            {
                // 물어볼 수 없었다 — 형식 · 서버는 부르는 쪽이 400 으로 먼저 거르므로 게임사 응답에 식별자(puuid 등)가 없던 경우다
                throw unavailable();
            }
            store.link(userId, game, gameNickname, server, snapshot, Instant.now().truncatedTo(ChronoUnit.MILLIS));
            log.info("게임 계정 연결 userId={} game={} tiers={}", userId, game, snapshot.tiers());
        }
        finally
        {
            lock.release(token);
        }
        return store.find(userId, game).orElseThrow(() -> new IllegalStateException(
                "방금 넣은 게임 계정이 없다 userId=" + userId + " game=" + game));
    }

    /**
     * 지금 긁어서 적고, <b>적힌 것을 다시 읽어</b> 돌려준다. 요청에서 되짚어 만들지 않는다 — {@code stats} 는 DB 에만 있는 값이다.
     *
     * <p>거르는 순서가 있다 — <b>게임사 API 를 부르기 전에</b>, 그리고 <b>쿨타임을 찍기 전에</b> 거절할 것을 다 거절한다.
     * ① 그 게임을 긁을 수 있나(409) ② 그 게임 계정이 있나(404) ③ 키가 있나(503) ④ 쿨타임(429) ⑤ 긁는다.
     * ③이 ②보다 뒤인 것은 <b>없는 계정에는 404 가 더 쓸모 있어서다</b>(키가 없다는 것은 그 사람이 할 수 있는 일이 아니다).
     */
    public GameAccountWithStats refresh(Long userId, Game game)
    {
        if(!worker.supports(game))
        {
            throw new ApiException(HttpStatus.CONFLICT, NOT_SUPPORTED, game.name() + " 의 전적은 아직 가져오지 않습니다");
        }
        GameAccountWithStats before = store.find(userId, game).orElseThrow(GameStatsRefresher::notFound);
        if(!worker.configured(game))
        {
            // 키가 없다 — 긁을 길이 없다. 쿨타임을 찍지 않는다(아무것도 소모하지 않았다)
            log.warn("전적 갱신 요청을 받았지만 그 게임사 API 키(LOL — RIOT_API_KEY · PUBG — PUBG_API_KEY)가 없다 userId={} game={}", userId, game);
            throw unavailable();
        }
        Long gameAccountId = before.account().getId();

        // 긁기를 시작하기 직전에 찍는다 — 실패해도 소모된다(소유자 결정). Redis 가 죽으면 비어 있는 값이 와서 그냥 진행한다
        OptionalLong remaining = cooldown.start(gameAccountId);
        if(remaining.isPresent())
        {
            log.info("전적 갱신 거절(쿨타임) userId={} gameAccountId={} retryAfter={}s", userId, gameAccountId, remaining.getAsLong());
            throw tooManyRefreshes(remaining.getAsLong());
        }

        SyncOutcome outcome = await(() -> worker.syncNow(userId, game), "전적 갱신 gameAccountId=" + gameAccountId, false);
        log.info("전적 갱신 userId={} gameAccountId={} outcome={}", userId, gameAccountId, outcome);
        return switch(outcome)
        {
            case SAVED -> store.find(userId, game).orElseThrow(GameStatsRefresher::notFound);
            // 긁는 사이에 게임 계정이 사라졌다(연결을 끊었다) — 없는 것과 같이 다룬다
            case NO_ACCOUNT -> throw notFound();
            // 자물쇠를 못 잡았다 = 누가 같은 계정을 긁고 있다. 곧 갱신되므로 "잠시 뒤 다시"가 맞는 답이다.
            // Retry-After 는 방금 찍은 쿨타임 그대로다 — 자물쇠가 풀리는 시각보다 늦지만, 두 429 가 같은 말을 하게 둔다
            case LOCKED -> throw tooManyRefreshes(properties.refreshCooldown().toSeconds());
            // ①에서 걸러지므로 오지 않는다. 그래도 200 으로 내려 보내지 않는다
            case NO_PROVIDER -> throw new ApiException(HttpStatus.CONFLICT, NOT_SUPPORTED,
                    game.name() + " 의 전적은 아직 가져오지 않습니다");
            case NOT_FETCHABLE -> throw unavailable();
        };
    }

    /**
     * 전용 풀에 던지고 상한만큼 기다린다. <b>기다림을 넘기거나 · 던질 자리가 없거나 · 긁다가 터지면 전부 503 이다</b> —
     * 실패의 갈래를 응답으로 가르지 않고 로그로만 남긴다. 예외는 둘이다 — ① {@code linking} 이 켜져 있으면(게임 계정 연결) 계정이 없다는 것만 404 로 가른다
     * ({@code RIOT_ID_NOT_FOUND} · {@code PUBG_PLAYER_NOT_FOUND} — 전적 갱신은 지금처럼 503 이다, 그 요청의 거절 갈래를 바꾸지 않았다)
     * ② <b>PUBG 의 429 는 503 에 {@code Retry-After}</b>(한도가 풀리기까지 남은 초 — {@code X-RateLimit-Reset}, 없으면 60)를 싣는다 — 연결 · 갱신 둘 다(2026-09-29 · P-36).
     */
    private <T> T await(Callable<T> task, String what, boolean linking)
    {
        if(executor.getThreadPoolExecutor().getQueue().remainingCapacity() == 0)
        {
            // DiscardPolicy 라 던져도 조용히 버려진다 — 30초를 기다린 뒤 503 이 될 것을 지금 끊는다
            log.warn("긁기를 시작하지 못했다 — 전용 풀의 큐가 꽉 찼다 {}", what);
            throw unavailable();
        }
        Future<T> scraping;
        try
        {
            scraping = executor.submit(task);
        }
        catch(RejectedExecutionException e)
        {
            // 지금 정책(DiscardPolicy)에서는 오지 않는다. 정책이 바뀌어도 500 이 되지 않게 받아 둔다
            log.warn("긁기를 시작하지 못했다 {}: {}", what, e.toString());
            throw unavailable();
        }
        try
        {
            return scraping.get(properties.refreshTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        catch(TimeoutException e)
        {
            // 자르지 않는다 — 전적 갱신은 뒤에서 끝나면 적힌다(자물쇠가 그동안 중복을 막는다). 연결은 그 결과를 버린다. 버려진 작업도 여기로 온다
            log.warn("{} 안에 끝나지 않았다 — 요청만 끊는다 {}", properties.refreshTimeout(), what);
            throw unavailable();
        }
        catch(ExecutionException e)
        {
            Throwable cause = (e.getCause() == null) ? e : e.getCause();
            if(linking && cause instanceof RiotIdNotFoundException)
            {
                log.info("Riot 에 그 Riot ID 가 없다 {}", what);
                throw new ApiException(HttpStatus.NOT_FOUND, RIOT_ID_NOT_FOUND, "Riot 에 그 이름#태그가 없습니다");
            }
            if(linking && cause instanceof PubgPlayerNotFoundException)
            {
                log.info("PUBG 에 그 닉네임이 없다 {}", what);
                throw new ApiException(HttpStatus.NOT_FOUND, PUBG_PLAYER_NOT_FOUND, "그 서버에 그 PUBG 닉네임이 없습니다");
            }
            log.warn("긁기에 실패했다 {}: {}", what, cause.toString());
            if(cause instanceof PubgApiException pubg && pubg.rateLimited())
            {
                throw ApiException.retryAfter(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "지금 전적을 가져올 수 없습니다",
                        pubg.retryAfterSeconds());
            }
            throw unavailable();
        }
        catch(InterruptedException e)
        {
            // 요청 스레드가 끊겼다(종료 중이다). 표시를 되살려 두고 실패로 답한다
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private static ApiException notFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, GAME_ACCOUNT_NOT_FOUND, "연결한 게임 계정이 없습니다");
    }

    private static ApiException tooManyRefreshes(long retryAfterSeconds)
    {
        return ApiException.retryAfter(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REFRESHES,
                "전적 갱신은 잠시 뒤에 다시 할 수 있습니다", retryAfterSeconds);
    }

    private static ApiException unavailable()
    {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "지금 전적을 가져올 수 없습니다");
    }
}
