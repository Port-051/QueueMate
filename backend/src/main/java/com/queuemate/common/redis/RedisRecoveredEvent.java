package com.queuemate.common.redis;

/**
 * Redis가 다시 쓰기를 받기 시작했다.
 *
 * <p>common이 matching을 직접 부르지 않으려고 이벤트로 알린다. 복구 후 무엇을 할지는
 * 각 모듈이 정한다. 매칭은 밀린 대기열을 즉시 훑는다.
 *
 * @param source 무엇이 복구를 알렸는가. {@code pubsub}이면 Sentinel이, {@code timer}면
 *               서킷의 유지 시간이 알린 것이다. timer가 계속 나오면 Sentinel 구독이 끊겼다는 뜻이다
 */
public record RedisRecoveredEvent(String source) {
}
