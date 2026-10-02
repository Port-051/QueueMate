package com.queuemate.matching.config.redis.valorant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static com.queuemate.matching.config.redis.RedisConfig.readScript;

/**
 * VALORANT 배정·취소 Lua 스크립트 빈 ({@code resources/redis/valorant/}).
 *
 * <p><b>빈 이름에 {@code valorant} 접두사를 붙인다.</b> LoL·PUBG 도 같은 타입
 * ({@code RedisScript<List>})의 빈을 두므로 접두사가 없으면 빈 이름이 겹쳐 기동이 실패하거나,
 * 더 나쁘게는 <b>에러 없이 다른 게임 스크립트가 주입된다</b> — Spring 이 같은 타입 여럿 중
 * 하나를 고르는 기준이 주입 필드 이름이기 때문이다. 그래서 {@code rule/valorant/*} 의 필드
 * 이름은 여기 메서드 이름과 글자까지 같아야 한다. 한쪽만 바꾸면 컴파일은 통과하고 기동 때 터진다.
 */
@Configuration
public class ValorantRedisConfig {

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> valorantCreateOrCheckPartyUntieredScript() {
        return RedisScript.of(readScript("redis/valorant/create-or-check-party-untiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> valorantJoinPartyUntieredScript() {
        return RedisScript.of(readScript("redis/valorant/join-party.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> valorantCreateOrCheckPartyTieredScript() {
        return RedisScript.of(readScript("redis/valorant/create-or-check-party-tiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> valorantJoinPartyTieredScript() {
        return RedisScript.of(readScript("redis/valorant/join-party-tiered.lua"), List.class);
    }

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다. 배정과 달리 티어 유무로 나뉘지 않는다 —
     * 한 벌이 티어를 보지 않는 모드를 빈 접미사로 접어 같은 루프로 처리한다.
     *
     * @return 1=취소, 2=취소 후 파티 삭제, 0=활성 요청 없음, -1=requestId 불일치
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> valorantLeavePartyScript() {
        return RedisScript.of(readScript("redis/valorant/leave-party.lua"), List.class);
    }
}
