package com.queuemate.realtime.ws;

/** contracts/events.md의 handshake 규약. */
public final class WebSocketProtocol {

    public static final String VERSION = "queuemate.v1";
    public static final String BEARER_PREFIX = "bearer.";
    /** handshake attribute key. 인증된 주체를 session으로 넘긴다. */
    public static final String USER_ID_ATTRIBUTE = "queuemate.userId";

    private WebSocketProtocol() {
    }
}
