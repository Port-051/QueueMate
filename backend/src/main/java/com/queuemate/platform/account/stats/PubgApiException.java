package com.queuemate.platform.account.stats;

/**
 * PUBG API 를 부르다 실패했다 — 거절(4xx · 5xx)이거나 · 응답이 없거나(타임아웃) · 응답을 읽을 수 없다. {@link RiotApiException} 과 같은 자리다.
 *
 * <p><b>이 예외 자체는 사용자에게 가지 않는다</b> — {@link GameStatsRefresher} 가 받아 503 {@code GAME_STATS_UNAVAILABLE} 로 옮긴다
 * (PUBG 게임 계정 연결이면 아무것도 저장하지 않고, 전적 갱신이면 기존 전적 줄을 지우지 않는다). <b>429 면 {@code Retry-After} 를 싣는다</b> —
 * {@link #retryAfterSeconds()}. 플레이어 조회의 404(그 닉네임이 없다)만은 {@link PubgPlayerNotFoundException} 으로 바뀌어 따로 간다.
 *
 * <p>예외의 메시지에 PUBG 의 응답 본문을 담지 않는다.
 */
public class PubgApiException extends RuntimeException {

    /** 429 에 {@code X-RateLimit-Reset} 이 없을 때 기다리라고 할 초 — 한도가 분 단위다 */
    static final long DEFAULT_RETRY_AFTER_SECONDS = 60;

    /** HTTP 상태. 타임아웃 · 읽기 실패처럼 상태가 없으면 {@code 0} 이다 */
    private final int status;
    /** 429 일 때만 — 한도가 풀리기까지 남은 초(1 이상). 그 밖에는 {@code 0} */
    private final long retryAfterSeconds;

    PubgApiException(String message, int status)
    {
        this(message, status, 0, null);
    }

    PubgApiException(String message, int status, long retryAfterSeconds, Throwable cause)
    {
        super(message, cause);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status()
    {
        return status;
    }

    /** 한도(키 하나에 분당 10회 — 개발용)를 넘겼다. <b>재시도하지 않는다</b> — 503 + {@code Retry-After} 로 끊는다 */
    public boolean rateLimited()
    {
        return status == 429;
    }

    public long retryAfterSeconds()
    {
        return retryAfterSeconds;
    }
}
