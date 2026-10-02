package com.queuemate.platform.room;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 방 안의 일의 Lua 스크립트 빈. {@code resources/lua/} 의 파일 하나에 빈 하나다. 2026-09-25 에 {@code room} 앱을 합치며
 * 그쪽의 {@code config/redis/RedisConfig} 를 옮겨 왔다 — {@code StringRedisTemplate} 은 이 앱의 것(Boot 의 자동 설정)을 같이 쓰고,
 * 옮겨 온 것은 스크립트 빈뿐이다. 방의 설정값({@link RoomProperties})도 여기서 켠다.
 *
 * <p>입장 · 나가기 · 강퇴는 {@code RedisScript<List>} 다 — 코드 하나가 아니라 <b>코드와 알림을 받을 사람들</b>을 돌려주기 때문이다.
 * 제네릭은 {@code List.class} 까지만 줄 수 있어 원시 타입을 쓴다({@code matching} 의 배정 스크립트들과 같다).
 *
 * <p><b>빈 이름 주의.</b> 스크립트 빈은 같은 타입({@code RedisScript<Long>})이 여럿이 되므로 Spring 은
 * <b>주입받는 필드 이름 = 빈 메서드 이름</b>으로 고른다. 메서드 이름을 바꾸면 컴파일은 통과하고
 * 기동 때 터진다.
 */
@Configuration
@EnableConfigurationProperties(RoomProperties.class)
public class RoomScriptConfig {

    /**
     * 스크립트를 기동 시 한 번만 읽어 문자열로 들고 있는다.
     *
     * RedisScript.of(Resource, ...) 를 쓰면 Spring이 EVALSHA에 쓸 sha1을 구할 때마다
     * "파일이 바뀌었나"를 확인한다. 그 확인이 전역 락 안에서 클래스패스를 훑고
     * 파일 stat 을 호출하므로, 모든 요청이 락 하나에 줄을 서게 된다.
     * 개발 환경이 WSL(/mnt/c)이면 stat 한 번이 5ms라 병목이 크게 드러난다
     * ({@code matching} 의 RedisConfig 에서 겪은 일이다).
     *
     * 스크립트는 실행 중에 바뀌지 않으므로 매번 확인할 이유가 없다.
     * 대신 <b>Lua 파일을 고치면 앱을 다시 띄워야 반영된다.</b>
     */
    public static String readScript(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 스크립트를 읽지 못했다: " + path, e);
        }
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> updateRoomSettingsScript() {
        return RedisScript.of(readScript("lua/update-room-settings.lua"), List.class);
    }

    /**
     * 방 만들기. 확인(활성 요청 키 · 방장 키 · 입장 표시 키)과 쓰기를 한 번에 한다. 만든 사람이 방장이다.
     * 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    public RedisScript<Long> createRoomScript() {
        return RedisScript.of(readScript("lua/create-room.lua"), Long.class);
    }

    /**
     * 입장. 확인(활성 요청 키 · 입장 표시 키 · 방이 있는가 · 정원)과 쓰기를 한 번에 한다.
     * 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> enterRoomScript() {
        return RedisScript.of(readScript("lua/enter-room.lua"), List.class);
    }

    /**
     * 나가기. 방장이 나가면 방장 키 · 멤버 HASH · 남은 전원의 입장 표시 키를 한 번에 지운다.
     * 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> leaveRoomScript() {
        return RedisScript.of(readScript("lua/leave-room.lua"), List.class);
    }

    /**
     * 방 안 사람 목록. 묻는 사람이 그 방에 있는지 확인하고 방장과 멤버를 <b>같은 순간의 것으로</b> 읽는다.
     * 돌려주는 것이 코드 하나가 아니라 {@code {코드, 방장, 멤버들…}} 이라 {@code RedisScript<List>} 다.
     * 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> membersRoomScript() {
        return RedisScript.of(readScript("lua/members-room.lua"), List.class);
    }

    /**
     * 접속 확인. 신호를 보낸 사람이 그 방에 있는지 확인하고 키의 수명을 다시 건다.
     * 방장의 신호는 방의 수명도 늘리고 멤버 HASH 의 유령을 뺀다 — 뺀 사람들이 코드 뒤에 붙어 온다.
     * 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> heartbeatRoomScript() {
        return RedisScript.of(readScript("lua/heartbeat-room.lua"), List.class);
    }

    /**
     * 강퇴. 부른 사람이 방장인지(방장 키와 비교) 확인하고 대상을 멤버 HASH 에서 빼며 입장 표시를 지운다 —
     * 남은 사람들이 코드 뒤에 붙어 온다. 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> kickRoomScript() {
        return RedisScript.of(readScript("lua/kick-room.lua"), List.class);
    }

    /**
     * 시그널을 전달해도 되는지 확인한다. 보낸 사람과 받는 사람의 입장 표시가 둘 다 이 방을 가리켜야 한다. 읽기만 한다.
     * 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    public RedisScript<Long> signalRoomScript() {
        return RedisScript.of(readScript("lua/signal-room.lua"), Long.class);
    }

    /**
     * 방장 확정. 부른 사람이 방장인지 확인하고 확정 표시 키를 쓴 뒤 그 순간의 멤버를 돌려준다.
     * 확정되면 입장 스크립트가 새 사람을 거절한다. 반환값의 뜻은 스크립트 머리의 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> confirmRoomScript() {
        return RedisScript.of(readScript("lua/confirm-room.lua"), List.class);
    }

    /**
     * 자동 매칭 파티의 방 — 없으면 만들고 있으면 들어간다(2026-09-27 소유자 결정 — docs/11 D-42). {@code matching} 의 파티 HASH 를 <b>읽기만 하고</b>
     * (확정인가 · 파티원인가 · 정원) 방 키를 쓴다. 활성 요청 키는 보지 않는다 — 이유는 스크립트 머리에 있다. 반환값의 뜻도 그 주석이 원본이다.
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> enterMatchRoomScript() {
        return RedisScript.of(readScript("lua/enter-match-room.lua"), List.class);
    }
}
