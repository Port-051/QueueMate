package com.queuemate.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "queuemate.auth")
public record AuthProperties(
        String jwtSecret,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds
) {
    public Duration accessTokenTtl() {
        return Duration.ofSeconds(accessTokenTtlSeconds);
    }

    public Duration refreshTokenTtl() {
        return Duration.ofSeconds(refreshTokenTtlSeconds);
    }
}
