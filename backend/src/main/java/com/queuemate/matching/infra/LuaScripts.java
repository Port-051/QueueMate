package com.queuemate.matching.infra;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * classpath의 Lua script를 읽어 온다.
 * script는 {@code backend/src/main/resources/redis/}에 파일로 두고 버전 관리한다 (docs/07 §5).
 * 자바 문자열 안에 Lua를 넣으면 diff에서 로직 변경이 보이지 않는다.
 */
final class LuaScripts {

    private LuaScripts() {
    }

    static <T> RedisScript<T> load(String classpathPath, Class<T> resultType) {
        try (var in = new ClassPathResource(classpathPath).getInputStream()) {
            return new DefaultRedisScript<>(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), resultType);
        } catch (IOException e) {
            throw new IllegalStateException("Redis script를 읽지 못했다: " + classpathPath, e);
        }
    }
}
