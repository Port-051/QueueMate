package com.queuemate.platform.common.push;

/**
 * 개인 알림 채널의 이름 — {@code qm:pubsub:push:{userId}}.
 *
 * <p><b>접두사는 이 앱이 정하지 않는다. 원본은 {@code matching} 의 {@code redisKeys/SharedKeys.PUSH_CHANNEL_PREFIX} 다</b>(CLAUDE.md §3.2).
 * 여기는 그 값을 글자까지 같게 옮겨 적은 것이다 — 발행하는 앱({@code matching} · {@code room} · 이 앱)과 구독하는 앱({@code notification})의 약속이라
 * 따로 바꾸거나 오타를 내면 <b>컴파일도 테스트도 통과한 채로 알림이 전부 끊긴다</b>(발행 쪽은 구독자 0명을 실패로 보지 않는다).
 * 그래서 이 앱에서는 <b>이 한 곳에만</b> 둔다. 바꿀 때는 {@code matching} 에서 먼저 바꾸고 같이 바꾼다.
 */
public final class PushChannels {

    /** {@code matching} 의 {@code SharedKeys.PUSH_CHANNEL_PREFIX} 와 같은 값이다 */
    public static final String PUSH_CHANNEL_PREFIX = "qm:pubsub:push:";

    private PushChannels()
    {
    }

    /**
     * 한 사용자의 알림 채널 — {@code qm:pubsub:push:42}. 사용자 id 는 <b>사용자 번호</b>({@code account.users.id})를 십진 문자열로 적은 것이다
     * (2026-09-22 소유자 결정 — 로그인 아이디가 아니다). {@code notification} 이 access 토큰의 {@code sub} 로 여는 채널과 같은 글자여야 한다
     */
    public static String pushChannel(Long userId)
    {
        return PUSH_CHANNEL_PREFIX + userId;
    }
}
