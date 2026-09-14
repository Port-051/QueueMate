package com.queuemate.matching.config;

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

@Configuration
public class RedisConfig {

    /**
     * 스크립트를 기동 시 한 번만 읽어 문자열로 들고 있는다.
     *
     * RedisScript.of(Resource, ...) 를 쓰면 Spring이 EVALSHA에 쓸 sha1을 구할 때마다
     * "파일이 바뀌었나"를 확인한다. 그 확인이 전역 락 안에서 클래스패스를 훑고
     * 파일 stat 을 호출하므로, 모든 요청이 락 하나에 줄을 서게 된다.
     * 개발 환경이 WSL(/mnt/c)이면 stat 한 번이 5ms라 병목이 크게 드러나고,
     * 리눅스 서버에서도 크기만 작을 뿐 직렬화는 그대로 남는다.
     *
     * 스크립트는 실행 중에 바뀌지 않으므로 매번 확인할 이유가 없다.
     */
    private static String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 스크립트를 읽지 못했다: " + path, e);
        }
    }

    @Bean
    public RedisScript<Long> claimRequestScript() {
        return RedisScript.of(read("redis/shared/claim-request.lua"), Long.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> createOrCheckPartyUntieredScript() {
        return RedisScript.of(read("redis/lol/create-or-check-party-untiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> joinPartyUntieredScript() {
        return RedisScript.of(read("redis/lol/join-party.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> joinTieredPartyUntieredScript() {
        return RedisScript.of(read("redis/lol/join-party-tiered.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> createOrCheckPartyTieredScript() {
        return RedisScript.of(read("redis/lol/create-or-check-party-tiered.lua"), List.class);
    }


    /**
     * 매칭 요청을 취소하고 파티에서 뺀다.
     *
     * @return 1=취소, 2=취소 후 파티 삭제, 0=활성 요청 없음, -1=requestId 불일치
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> leavePartyScript() {
        return RedisScript.of(read("redis/lol/leave-party.lua"), List.class);
    }

    /**
     * 제안 수락 + 확정 (INV-4 / INV-5).
     *
     * @return NOT_FOUND | NOT_A_MEMBER | ACCEPTED | CONFIRMED | DECLINED
     */
    @Bean
    public RedisScript<String> acceptProposalScript() {
        return RedisScript.of(read("redis/proposal/accept-proposal.lua"), String.class);
    }

    /**
     * 제안 거절 (INV-5).
     *
     * <p>같은 타입({@code RedisScript<String>}) 빈이 둘이라 Spring 은 <b>필드 이름</b>으로
     * 고른다. 주입받는 쪽의 필드명이 이 메서드 이름과 같아야 한다 — 빈 이름을 바꾸면
     * 컴파일은 통과하고 기동 때 터진다.
     *
     * @return NOT_FOUND | NOT_A_MEMBER | ALREADY_RESPONDED | DECLINED | CONFIRMED
     */
    @Bean
    public RedisScript<String> declineProposalScript() {
        return RedisScript.of(read("redis/proposal/decline-proposal.lua"), String.class);
    }
}
