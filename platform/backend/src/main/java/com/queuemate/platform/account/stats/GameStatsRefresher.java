package com.queuemate.platform.account.stats;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.GameAccountWithStats;
import com.queuemate.platform.common.error.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <b>LoL · PUBG 게임 계정 연결</b>({@code PUT …/game-accounts/LOL} · {@code …/PUBG} — 2026-09-27 · 2026-09-29 소유자 결정 "LoL 처럼 동기로").
 * 저장하기 <b>전에</b> 게임사 API 를 긁고, 성공해야 저장한다 — {@link #link}.
 *
 * <p>(2026-09-24 ~ 09-30 에는 사용자가 누르는 <b>전적 갱신</b>({@code POST …/game-accounts/{game}/refresh} — P-17)도 이 클래스였다. 2026-09-30 소유자 결정으로
 * 그 요청과 그것만 쓰던 쿨타임({@code qm:riot:refresh:*}) · 에러 코드 둘({@code GAME_ACCOUNT_NOT_FOUND} · {@code GAME_STATS_NOT_SUPPORTED})을 없앴다 —
 * 전적은 로그인 · 재발급 때 뒤에서 다시 받는다({@link GameStatsLoginRefresher} — P-42). 클래스 이름은 그대로 두었다.)
 *
 * <p><b>상한(30초)을 어떻게 거나 — 전용 풀에 던지고 {@link Future#get(long, TimeUnit)} 으로 기다린다.</b>
 * 요청 스레드에서 그냥 긁으면 <b>자를 방법이 없다</b>(Riot 호출 14번 × 읽기 타임아웃 3초 — 경기 10판). 상한을 넘기면 요청은 503 으로 끝내고
 * <b>늦게 끝난 긁기는 버린다</b>(저장하지 않는다 — {@link #link}). 수치는 {@link RiotProperties#refreshTimeout()} 이고 게임을 가리지 않는다.
 * 대가 — 그 풀은 스레드 둘 · 큐 50 · {@code DiscardPolicy} 라 <b>넘치면 조용히 버려진다</b>({@code RejectedExecutionException} 이 아니다).
 * 버려지면 {@code Future} 가 영원히 완료되지 않아 30초를 기다린 뒤 503 이 되므로, <b>큐가 꽉 찼으면 던지기 전에 503 으로 끊는다</b>
 * (그 검사와 실제 던지기 사이의 경쟁은 남는다 — 그때 남는 것이 30초 기다림이다). 로그인 때 다시 받기는 <b>다른 풀</b>이라 이 큐를 채우지 않는다.
 *
 * <p><b>거절</b>(상태 코드는 소유자가 정했고 <b>에러 코드의 이름과 글귀는 Claude 가 정했다</b> — 계약의 "계정" 표 · "전적을 긁는 것").
 * <ul>
 *   <li>404 {@code RIOT_ID_NOT_FOUND} — 그 이름#태그가 Riot 에 없다(LoL)</li>
 *   <li>404 {@code PUBG_PLAYER_NOT_FOUND} — 그 서버에 그 PUBG 닉네임이 없다</li>
 *   <li>429 {@code TOO_MANY_STATS_REFRESHES} + {@code Retry-After} — <b>누가 이미 같은 계정을 긁고 있다</b>(연결 둘이 겹쳤거나, 로그인 뒤 다시 받기가 돌고 있다).
 *       이름은 옛 전적 갱신 요청의 것이다 — 연결이 전부터 이 코드를 같이 썼고 프런트가 받는 코드라 바꾸지 않았다</li>
 *   <li>503 {@code GAME_STATS_UNAVAILABLE} — 지금 전적을 가져올 수 없다. 게임사 API 가 거절 · 응답이 없다 · 30초를 넘겼다 · 키({@code RIOT_API_KEY} ·
 *       {@code PUBG_API_KEY})가 없다 · 응답에 식별자가 없다 — <b>이유를 가르지 않는다</b>(PUBG 의 429 만 {@code Retry-After} 를 싣는다 — 2026-09-29)</li>
 * </ul>
 */
@Slf4j
@Component
public class GameStatsRefresher {

    static final String TOO_MANY_REFRESHES = "TOO_MANY_STATS_REFRESHES";
    static final String UNAVAILABLE = "GAME_STATS_UNAVAILABLE";
    /** LoL 게임 계정 연결에서 그 Riot ID({@code 이름#태그})가 Riot 에 없다 — 2026-09-27. 이름과 글귀는 Claude 가 정했다 */
    static final String RIOT_ID_NOT_FOUND = "RIOT_ID_NOT_FOUND";
    /** PUBG 게임 계정 연결에서 그 서버(shard)에 그 닉네임이 없다 — 2026-09-29(P-36). 이름은 계약에서 정해졌고 글귀는 Claude 가 정했다 */
    static final String PUBG_PLAYER_NOT_FOUND = "PUBG_PLAYER_NOT_FOUND";

    private final RiotProperties properties;
    private final GameStatsSyncWorker worker;
    private final GameStatsStore store;
    private final GameStatsSyncLock lock;
    private final ThreadPoolTaskExecutor executor;

    public GameStatsRefresher(RiotProperties properties, GameStatsSyncWorker worker, GameStatsStore store,
                              GameStatsSyncLock lock,
                              @Qualifier(GameStatsAsyncConfig.EXECUTOR) ThreadPoolTaskExecutor executor)
    {
        this.properties = properties;
        this.worker = worker;
        this.store = store;
        this.lock = lock;
        this.executor = executor;
        if(properties.configured())
        {
            log.info("게임 전적 동기화가 켜져 있다 — {}", properties);
        }
        else
        {
            // 키를 찍지 않는다. 환경변수의 이름만 남긴다
            log.info("RIOT_API_KEY 가 없어 게임 전적을 긁지 않는다 — LoL 게임 계정 연결이 503 이고 로그인 때 다시 받지도 않는다");
        }
    }

    /**
     * <b>LoL · PUBG 게임 계정 연결</b>({@code PUT …/game-accounts/LOL} · {@code …/PUBG} — 2026-09-27 · 2026-09-29 소유자 결정). 저장하기 <b>전에</b> 게임사 API 를 긁고,
     * 성공하면 게임 계정 줄(이름 · 서버 · 사다리별 티어 · {@code external_id})과 전적을 <b>한 트랜잭션</b>으로 적은 뒤 다시 읽어 돌려준다.
     * <b>실패하면 아무것도 적지 않는다</b> — 연동이 안 된 것이다. 거절의 갈래는 이 클래스 머리의 주석이다.
     *
     * <p><b>긁기는 전용 풀 · 저장은 이 스레드다.</b> 30초를 넘겨 이 요청이 503 으로 끝났는데 뒤에서 긁기가 끝나 저장해 버리면
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
                throw busy(GameStatsSyncLock.LOCK_TTL.toSeconds());
            }
            StatsSnapshot snapshot = await(() -> worker.fetch(game, gameNickname, server), "게임 계정 연결 userId=" + userId);
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
     * 전용 풀에 던지고 상한만큼 기다린다. <b>기다림을 넘기거나 · 던질 자리가 없거나 · 긁다가 터지면 전부 503 이다</b> —
     * 실패의 갈래를 응답으로 가르지 않고 로그로만 남긴다. 예외는 둘이다 — ① 계정이 없다는 것만 404 로 가른다
     * ({@code RIOT_ID_NOT_FOUND} · {@code PUBG_PLAYER_NOT_FOUND}) ② <b>PUBG 의 429 는 503 에 {@code Retry-After}</b>
     * (한도가 풀리기까지 남은 초 — {@code X-RateLimit-Reset}, 없으면 60)를 싣는다(2026-09-29 · P-36).
     */
    private <T> T await(Callable<T> task, String what)
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
            // 자르지 않는다 — 늦게 끝난 긁기는 저장하지 않고 버린다(link 가 결과를 받지 못했다). 버려진 작업도 여기로 온다
            log.warn("{} 안에 끝나지 않았다 — 요청만 끊는다 {}", properties.refreshTimeout(), what);
            throw unavailable();
        }
        catch(ExecutionException e)
        {
            Throwable cause = (e.getCause() == null) ? e : e.getCause();
            if(cause instanceof RiotIdNotFoundException)
            {
                log.info("Riot 에 그 Riot ID 가 없다 {}", what);
                throw new ApiException(HttpStatus.NOT_FOUND, RIOT_ID_NOT_FOUND, "Riot 에 그 이름#태그가 없습니다");
            }
            if(cause instanceof PubgPlayerNotFoundException)
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

    /** 누가 이미 같은 계정을 긁고 있다 — 연결 둘이 겹쳤거나 로그인 뒤 다시 받기가 돌고 있다. {@code Retry-After} 는 자물쇠의 수명(60초)이다 */
    private static ApiException busy(long retryAfterSeconds)
    {
        return ApiException.retryAfter(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_REFRESHES,
                "지금 그 게임 계정의 전적을 가져오는 중입니다 — 잠시 뒤에 다시 해 주세요", retryAfterSeconds);
    }

    private static ApiException unavailable()
    {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, "지금 전적을 가져올 수 없습니다");
    }
}
