package com.queuemate.chat.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 브라우저를 상대하는 설정값. {@code application.yaml} 의 {@code queuemate.web.*} 이다.
 *
 * @param allowedOrigins 상태를 바꾸는 요청과 WebSocket 핸드셰이크에 허용하는 {@code Origin} 의 목록(환경변수 {@code ALLOWED_ORIGINS}, 쉼표로 구분).
 *                       {@code platform} · {@code notification} 과 같은 이름 · 같은 기본값이다. 운영에서는 CloudFront 도메인 하나다.
 *                       소켓 쪽({@code setAllowedOrigins})도 이 목록을 그대로 쓴다 — 두 곳에 따로 적지 않는다
 */
@ConfigurationProperties(prefix = "queuemate.web")
public record WebSecurityProperties(
        @DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins
) {
}
