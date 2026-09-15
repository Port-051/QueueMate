package com.queuemate.matching.config.lol;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static com.queuemate.matching.config.RedisConfig.readScript;

/**
 * LoL 배정·취소 Lua 스크립트 빈 ({@code resources/redis/lol/}).
 *
 * <p><b>메서드 이름을 바꾸지 마라.</b> 다섯 개가 전부 {@code RedisScript<List>} 라 Spring 은 주입받는
 * 필드 이름({@code rule/lol/*Assigner.java}, {@code PartyLeaver.java})으로 고른다. 바꾸면 컴파일은
 * 통과하고 기동 때 터진다. LoL 은 게임을 나누기 전에 생긴 이름이라 게임 접두사가 없다 — 다른 게임의
 * 빈은 이 이름과 겹치지 않게 접두사를 붙인다({@link com.queuemate.matching.config.pubg.PubgRedisConfig}).
 */
@Configuration
public class LolRedisConfig {

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> createOrCheckPartyUntieredScript() {
        return RedisScript.of(readScript("redis/lol/create-or-check-party-untiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> joinPartyUntieredScript() {
        return RedisScript.of(readScript("redis/lol/join-party.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> joinTieredPartyUntieredScript() {
        return RedisScript.of(readScript("redis/lol/join-party-tiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> createOrCheckPartyTieredScript() {
        return RedisScript.of(readScript("redis/lol/create-or-check-party-tiered.lua"), List.class);
    }

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다.
     *
     * @return 1=취소, 2=취소 후 파티 삭제, 0=활성 요청 없음, -1=requestId 불일치
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> leavePartyScript() {
        return RedisScript.of(readScript("redis/lol/leave-party.lua"), List.class);
    }
}
