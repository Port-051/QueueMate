package com.queuemate.matching.config.redis.pubg;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static com.queuemate.matching.config.redis.RedisConfig.readScript;

/**
 * PUBG 배정·취소 Lua 스크립트 빈 ({@code resources/redis/pubg/}).
 *
 * <p><b>빈 이름에 {@code pubg} 접두사를 붙인다.</b> LoL 과 타입이 같은({@code RedisScript<List>}) 빈이
 * 여럿이라 Spring 은 주입받는 필드 이름으로 고르는데, LoL 빈({@code lolCreateOrCheckPartyTieredScript} 등)과
 * 메서드 이름이 같으면 빈 이름이 겹쳐 기동이 실패한다. PUBG 규칙 클래스는 이 메서드 이름 그대로
 * 필드를 선언해야 한다.
 */
@Configuration
public class PubgRedisConfig {

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> pubgCreateOrCheckPartyUntieredScript() {
        return RedisScript.of(readScript("redis/pubg/create-or-check-party-untiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> pubgJoinPartyUntieredScript() {
        return RedisScript.of(readScript("redis/pubg/join-party.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> pubgCreateOrCheckPartyTieredScript() {
        return RedisScript.of(readScript("redis/pubg/create-or-check-party-tiered.lua"), List.class);
    }
}
