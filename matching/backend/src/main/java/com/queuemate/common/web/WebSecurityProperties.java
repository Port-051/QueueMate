package com.queuemate.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 브라우저를 상대하는 설정값. {@code application.yaml} 의 {@code queuemate.web.*} 이다.
 *
 * @param allowedOrigins 상태를 바꾸는 요청에 허용하는 {@code Origin}(환경변수 {@code ALLOWED_ORIGINS}, 쉼표로 구분).
 *                       기본값은 {@code platform} 과 같다. 운영은 CloudFront 도메인 하나다
 */
@ConfigurationProperties(prefix = "queuemate.web")
public record WebSecurityProperties(
        @DefaultValue({"http://localhost:5173", "http://localhost:3000"}) List<String> allowedOrigins
) {
}
