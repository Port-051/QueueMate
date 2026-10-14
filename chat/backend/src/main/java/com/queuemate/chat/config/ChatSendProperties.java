package com.queuemate.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 방 채팅 전송의 설정값 — {@code application.yaml} 의 {@code queuemate.chat.send.*}.
 *
 * @param stripes         전송 줄(스레드) 수. 방은 해시로 줄에 나뉜다. 환경변수 {@code CHAT_SEND_STRIPES}
 * @param queueCapacity   줄마다 쌓아 둘 수 있는 메시지 수. 넘치면 버린다. {@code CHAT_SEND_QUEUE_CAPACITY}
 * @param timeLimitMs     소켓 하나에 한 번 쓰기가 이 시간을 넘기면 그 소켓을 끊는다({@code ConcurrentWebSocketSessionDecorator}). 느린 브라우저가 줄을 오래 잡지 않게. {@code CHAT_SEND_TIME_LIMIT_MS}
 * @param bufferSizeLimit 소켓 하나에 쓰기를 기다리는 바이트가 이 상한을 넘기면 끊는다(같은 데코레이터). {@code CHAT_SEND_BUFFER_SIZE_LIMIT}
 */
@ConfigurationProperties(prefix = "queuemate.chat.send")
public record ChatSendProperties(
        @DefaultValue("4") int stripes,
        @DefaultValue("1000") int queueCapacity,
        @DefaultValue("10000") int timeLimitMs,
        @DefaultValue("65536") int bufferSizeLimit
) {
}
