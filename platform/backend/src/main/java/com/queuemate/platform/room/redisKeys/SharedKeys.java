package com.queuemate.platform.room.redisKeys;

/**
 * {@code matching} 이 주인인 Redis 키. <b>이 앱이 정하는 값이 아니다.</b>
 *
 * <p><b>{@code matching} 과의 약속이다(docs/11 D-19)</b> — 활성 요청 키는 {@code matching} 이 쓰고 platform 은 {@code EXISTS} 만 한다.
 * 쓰지도, 지우지도, 수명을 걸지도 않고 값도 읽지 않는다. 원본은 {@code matching} 의 {@code redisKeys/SharedKeys.java} 다 — 여기서 따로 바꾸거나
 * 오타를 내면 컴파일도 테스트도 통과한 채로 조용히 어긋난다(늘 "키 없음"을 보고 매칭 대기 중인 사용자를 입장시킨다). 그래서 이 파일 한 곳에만 둔다.
 * {@code SharedPrefixTest} 가 옆 폴더의 원본과 글자를 비교한다.
 *
 * <p>반대 방향의 약속(입장 표시 키 — platform 이 쓰고 {@code matching} 이 {@code EXISTS} 로 본다)은 {@link RoomKeys#ACTIVE_ROOM_PREFIX} 에 있다.
 * 알림 채널 접두사는 {@code common.push.PushChannels}, 게시판 채널은 {@code party.board.BoardChannels} 다.
 */
public final class SharedKeys {

    /**
     * 활성 요청 HASH 접두사. 원본은 {@code matching} 의 {@code SharedKeys.ACTIVE_REQUEST_PREFIX} 다.
     * <pre>{@code "qm:user:active-request:"  →  qm:user:active-request:42}</pre>
     */
    public static final String ACTIVE_REQUEST_PREFIX = "qm:user:active-request:";

    private SharedKeys()
    {
    }

    /**
     * 사용자의 활성 매칭 요청. 방을 만들거나 입장할 때 이 키가 있으면 거절한다 (docs/11 D-11 9번).
     * <pre>{@code qm:user:active-request:42}</pre>
     */
    public static String activeRequestKey(String userId)
    {
        return ACTIVE_REQUEST_PREFIX + userId;
    }
}
