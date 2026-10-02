package com.queuemate.platform.social;

import com.queuemate.platform.room.RoomScriptConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 차단 관계 사본(Redis {@code qm:user:block-rel:*} — 2026-10-02 소유자 결정 · docs/11 D-57 · {@code contracts/platform-api.md} P-52)의 Lua 스크립트 빈과 설정.
 * 스크립트는 {@code resources/lua/} 의 파일 하나에 빈 하나이고, 읽는 법은 방의 스크립트와 같다({@link RoomScriptConfig#readScript} — 기동 때 한 번 읽어 문자열로 든다.
 * Lua 를 고치면 앱을 다시 띄워야 반영된다).
 *
 * <p><b>빈 이름 주의</b> — 같은 타입({@code RedisScript<Long>})이 여럿이라 주입받는 필드 이름 = 빈 메서드 이름으로 고른다({@code RoomScriptConfig} 와 같다).
 *
 * <p><b>{@code @EnableScheduling} 이 여기 있다</b> — 이 앱에서 주기적으로 도는 일은 차단 관계 사본의 재구성 하나다({@code social.service.BlockRelationSync}).
 * 2026-10-02 전에는 스케줄러가 없었다(타이머로 도는 전적 갱신은 두지 않는다 — CLAUDE.md §11).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(BlockProperties.class)
public class BlockRelationConfig {

    /** 차단 한 쌍을 양쪽 집합에 더한다({@code SADD} 둘). 반환값의 뜻은 스크립트 머리의 주석이 원본이다 */
    @Bean
    public RedisScript<Long> addBlockRelScript()
    {
        return RedisScript.of(RoomScriptConfig.readScript("lua/add-block-rel.lua"), Long.class);
    }

    /** 차단 한 쌍을 양쪽 집합에서 뺀다({@code SREM} 둘). 반대 방향 줄이 없을 때만 부른다 — 스크립트 머리의 주석 */
    @Bean
    public RedisScript<Long> removeBlockRelScript()
    {
        return RedisScript.of(RoomScriptConfig.readScript("lua/remove-block-rel.lua"), Long.class);
    }

    /** 한 사용자의 집합을 통째로 갈아 끼운다({@code DEL} + {@code SADD}) — 재구성만 쓴다. 스크립트 머리의 주석 */
    @Bean
    public RedisScript<Long> replaceBlockRelScript()
    {
        return RedisScript.of(RoomScriptConfig.readScript("lua/replace-block-rel.lua"), Long.class);
    }
}
