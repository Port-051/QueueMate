package com.queuemate.platform.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 브라우저를 상대하는 설정값. {@code application.yaml} 의 {@code platform.web.*} 이고 환경변수로 바꾼다.
 *
 * @param allowedOrigins 상태를 바꾸는 요청에 허용하는 {@code Origin} 의 목록(환경변수 {@code ALLOWED_ORIGINS}, 쉼표로 구분).
 *                       운영에서는 CloudFront 도메인 하나다 (CLAUDE.md §5.1 "전제")
 * @param cookieSecure   access 쿠키에 {@code Secure} 를 붙일지(환경변수 {@code COOKIE_SECURE}). 운영은 {@code true} 다.
 *                       로컬은 http 라서 켜면 브라우저가 쿠키를 버린다
 */
@ConfigurationProperties(prefix = "platform.web")
public record WebSecurityProperties(
        @DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins,
        @DefaultValue("false") boolean cookieSecure
) {
}
