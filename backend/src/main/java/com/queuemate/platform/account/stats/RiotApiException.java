package com.queuemate.platform.account.stats;

/**
 * Riot 을 부르다 실패했다 — 거절(4xx · 5xx)이거나 · 응답이 없거나(타임아웃) · 응답을 읽을 수 없다.
 *
 * <p><b>이 예외는 사용자에게 가지 않는다</b> — 전적을 긁는 것은 비동기이고, 실패해도 본 작업(게임 계정 연결 · 글 쓰기)은 이미 성공했다.
 * {@link GameStatsSyncWorker} 가 잡아서 로그만 남긴다. 기존 전적 줄은 지우지 않는다 — 옛 값이라도 있는 편이 낫다.
 *
 * <p>예외의 메시지에 Riot 의 응답 본문을 담지 않는다 — 키가 섞여 들어갈 일은 없지만 남의 계정 정보가 로그에 쌓일 이유도 없다.
 */
public class RiotApiException extends RuntimeException {

    /** HTTP 상태. 타임아웃 · 읽기 실패처럼 상태가 없으면 {@code 0} 이다. <b>429 면 rate limit 이다</b> — 재시도하지 않고 그 자리에서 포기한다 */
    private final int status;

    RiotApiException(String message, int status)
    {
        super(message);
        this.status = status;
    }

    RiotApiException(String message, int status, Throwable cause)
    {
        super(message, cause);
        this.status = status;
    }

    public int status()
    {
        return status;
    }

    public boolean rateLimited()
    {
        return status == 429;
    }
}
