package com.queuemate.matching.config.lol;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static com.queuemate.matching.config.RedisConfig.readScript;

/**
 * LoL 배정·취소 Lua 스크립트 빈 ({@code resources/redis/lol/}).
 *
 * <p><b>빈 이름 = 주입 필드 이름이다. 메서드 이름을 바꾸면 필드도 같이 바꿔라.</b> 다섯 개가 전부
 * {@code RedisScript<List>} 라 Spring 은 주입받는 필드 이름({@code rule/lol/Lol*Assigner.java},
 * {@code LolPartyLeaver.java}, 테스트의 {@code ProposalIdempotencyTest})으로 고른다. 한쪽만 바꾸면
 * 컴파일은 통과하고 기동 때 터진다.
 *
 * <p><b>{@code lol} 접두사를 붙인다.</b> PUBG 도 같은 타입의 빈을 두므로({@code pubg} 접두사,
 * {@link com.queuemate.matching.config.pubg.PubgRedisConfig}) 접두사가 없으면 이름이 겹치거나, 다른
 * 게임 Assigner 가 롤 이름으로 필드를 선언했을 때 <b>에러 없이 롤 스크립트가 주입된다.</b>
 */
@Configuration
public class LolRedisConfig {

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> lolCreateOrCheckPartyUntieredScript() {
        return RedisScript.of(readScript("redis/lol/create-or-check-party-untiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> lolJoinPartyUntieredScript() {
        return RedisScript.of(readScript("redis/lol/join-party.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> lolJoinPartyTieredScript() {
        return RedisScript.of(readScript("redis/lol/join-party-tiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> lolCreateOrCheckPartyTieredScript() {
        return RedisScript.of(readScript("redis/lol/create-or-check-party-tiered.lua"), List.class);
    }

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다.
     *
     * @return 1=취소, 2=취소 후 파티 삭제, 0=활성 요청 없음, -1=requestId 불일치
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> lolLeavePartyScript() {
        return RedisScript.of(readScript("redis/lol/leave-party.lua"), List.class);
    }
}
