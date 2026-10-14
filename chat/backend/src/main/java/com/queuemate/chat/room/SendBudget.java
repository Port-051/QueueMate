package com.queuemate.chat.room;

/**
 * 소켓 하나의 보내기 예산 — 토큰 버킷. 통에 토큰이 {@code capacity} 개까지 들어가고, 시간이 흐른 만큼 초당 {@code refillPerSecond} 개씩 다시 찬다.
 * 메시지 하나가 토큰 하나다. 통이 비면 거절(TOO_FAST). 스케줄러가 없다 — 보낼 때만 그 소켓의 통 하나를 계산한다(CLAUDE.md §3.1 · 2026-10-11).
 *
 * <p>세션 attributes 에 하나씩 든다({@link RoomMembershipInterceptor#SEND_BUDGET}). <b>소켓 단위</b>다 — 같은 사람이 탭을 더 열면 그만큼 늘어난다.
 * 이것이 막는 것은 사고(눌린 키 · 프런트의 루프 · 소켓 하나의 폭주)이고 작정한 남용은 아니다. 사용자 단위로 올리는 것은 메시지마다 Redis 왕복이 붙어 두지 않았다.
 *
 * <p>한 세션의 프레임은 서블릿 컨테이너가 순서대로 넘기므로 {@link #tryTake()} 가 겹쳐 불리지 않는다 — 그래도 {@code synchronized} 로 보험을 둔다(값 둘을 함께 바꾼다).
 */
public final class SendBudget {

    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastNanos;

    public SendBudget(int capacity, int refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;                 // 처음엔 가득
        this.lastNanos = System.nanoTime();
    }

    /** 토큰 하나를 꺼낸다. 꺼냈으면 true, 통이 비어 있으면 false(아무것도 바꾸지 않는다). */
    public synchronized boolean tryTake() {
        long now = System.nanoTime();
        double refilled = (now - lastNanos) / 1_000_000_000.0 * refillPerSecond;
        tokens = Math.min(capacity, tokens + refilled);   // 상한은 통의 크기 — 오래 쉬었다고 100개가 쌓이지 않는다
        lastNanos = now;
        if (tokens < 1) {
            return false;
        }
        tokens -= 1;
        return true;
    }
}
