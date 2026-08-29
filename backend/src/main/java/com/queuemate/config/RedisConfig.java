package com.queuemate.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {
    // Spring Boot auto-configures StringRedisTemplate/RedisConnectionFactory.
    // Member 2: add serializers / Lua script beans here only if shared config is required.
}
