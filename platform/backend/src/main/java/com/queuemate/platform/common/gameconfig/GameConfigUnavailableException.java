package com.queuemate.platform.common.gameconfig;

/**
 * gameconfig 를 <b>읽지 못했다</b>(Redis 에 닿지 못했다) — {@link GameConfigReader} 의 <b>fail-closed</b> 쪽 메서드가 던진다.
 * 부르는 쪽이 503 으로 옮긴다({@code party.service.AutoJoinService} — {@code ROOM_STATE_UNAVAILABLE}). fail-open 인 {@code hasMode} · {@code hasTier} 는 던지지 않는다.
 */
public class GameConfigUnavailableException extends RuntimeException {

    public GameConfigUnavailableException(Throwable cause)
    {
        super("gameconfig 를 읽지 못했다", cause);
    }
}
