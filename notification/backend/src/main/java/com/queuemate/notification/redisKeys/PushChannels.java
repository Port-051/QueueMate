package com.queuemate.notification.redisKeys;

/**
 * 이 서비스가 구독하는 Redis Pub/Sub 채널 이름을 한 자리에 모은다.
 *
 * <p><b>원본은 이 파일이 아니다.</b> {@code matching} 의
 * {@code redisKeys/SharedKeys.java} 에 있는 {@code PUSH_CHANNEL_PREFIX} 가 원본이고,
 * 여기는 그 값을 베껴 둔 것이다. <b>여기서만 값을 바꾸거나 오타를 내면 컴파일도
 * 테스트도 통과한 채로 알림이 전부 끊긴다</b> — 발행 쪽은 구독자 0명을 실패로 보지 않고
 * 조용히 버리기 때문이다. 바꿔야 하면 {@code matching} 과 같이 바꾼다 (CLAUDE.md §3).
 *
 * <p>이 서비스 안에서도 접두사는 이 상수 한 곳에만 둔다. 다른 클래스에 같은 문자열을
 * 따로 적지 마라.
 */
public final class PushChannels {

    /**
     * 푸시 알림 채널 접두사. {@code matching} 의 {@code SharedKeys.PUSH_CHANNEL_PREFIX} 와
     * 글자 하나까지 같아야 한다.
     * <pre>{@code "qm:pubsub:push:"  →  qm:pubsub:push:u123}</pre>
     */
    public static final String PUSH_CHANNEL_PREFIX = "qm:pubsub:push:";

    /**
     * 한 사용자의 알림 채널. {@code SUBSCRIBE} 할 때 쓴다.
     * <pre>{@code qm:pubsub:push:u123}</pre>
     */
    public static String pushChannel(String userId) {
        return PUSH_CHANNEL_PREFIX + userId;
    }

    /**
     * 채널 이름에서 userId 를 되꺼낸다. 리스너가 받은 메시지를 어느 사용자 연결로 보낼지
     * 정할 때 쓴다. 접두사로 시작하지 않는 채널이면 {@code null} 을 돌려준다 — 이 서비스는
     * 이 접두사 채널만 구독하므로 정상 상황에서는 일어나지 않는다.
     */
    public static String userIdOf(String channel) {
        return channel != null && channel.startsWith(PUSH_CHANNEL_PREFIX)
                ? channel.substring(PUSH_CHANNEL_PREFIX.length())
                : null;
    }

    private PushChannels() {
    }
}
