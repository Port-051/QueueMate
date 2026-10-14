package com.queuemate.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 방 채팅의 상한 — {@code application.yaml} 의 {@code queuemate.chat.*}(CLAUDE.md §3.1 · §7 ⑥ "숫자는 yaml 그대로").
 *
 * @param maxTextLength        한 메시지의 최대 글자 수. 넘으면 {@code ERROR TOO_LONG}. 환경변수 {@code CHAT_MAX_TEXT_LENGTH}
 * @param maxMessagesPerSecond 소켓 하나가 초당 보낼 수 있는 메시지 수(토큰 버킷 {@code SendBudget} 의 크기 · 채우는 속도). 넘으면 {@code ERROR TOO_FAST}. {@code CHAT_MAX_MESSAGES_PER_SECOND}
 * @param pingIntervalMs       서버 → 브라우저 ping 간격. ALB 유휴 타임아웃보다 짧아야 한다. {@code CHAT_PING_INTERVAL_MS}
 */
@ConfigurationProperties(prefix = "queuemate.chat")
public record ChatProperties(
        @DefaultValue("500") int maxTextLength,
        @DefaultValue("5") int maxMessagesPerSecond,
        @DefaultValue("20000") long pingIntervalMs
) {
}
