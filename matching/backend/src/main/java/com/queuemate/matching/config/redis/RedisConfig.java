package com.queuemate.matching.config.redis;

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
 * <b>게임과 무관한</b> Lua 스크립트 빈. 게임별 스크립트는 {@code config/redis/{game}/} 의 설정 클래스가 갖는다
 * ({@link com.queuemate.matching.config.redis.lol.LolRedisConfig},
 * {@link com.queuemate.matching.config.redis.pubg.PubgRedisConfig}).
 *
 * <p>나누는 기준은 {@code resources/redis/} 디렉터리와 같다 (docs/11 D-7).
 * {@code shared/} 와 {@code proposal/} 은 여기, {@code lol/} · {@code pubg/} 는 각 게임 설정이다.
 * 한 게임 스크립트를 고치러 들어온 사람이 다른 게임 빈을 건드리지 않게 하려는 것이다.
 *
 * <p><b>빈 이름 주의.</b> 스크립트 빈은 같은 타입({@code RedisScript<List>} 등)이 여럿이라 Spring 은
 * <b>주입받는 필드 이름 = 빈 메서드 이름</b>으로 고른다. 메서드 이름을 바꾸면 컴파일은 통과하고
 * 기동 때 터진다. 서로 다른 설정 클래스에 같은 메서드 이름을 두면 빈 이름이 겹쳐 역시 기동이 실패한다 —
 * 그래서 게임별 빈은 {@code lol} / {@code pubg} 처럼 게임 접두사를 붙인다. 여기 있는 공통 빈만 접두사가 없다.
 */
@Configuration
public class RedisConfig {

    /**
     * 스크립트를 기동 시 한 번만 읽어 문자열로 들고 있는다. 게임별 설정도 이것을 쓴다.
     *
     * RedisScript.of(Resource, ...) 를 쓰면 Spring이 EVALSHA에 쓸 sha1을 구할 때마다
     * "파일이 바뀌었나"를 확인한다. 그 확인이 전역 락 안에서 클래스패스를 훑고
     * 파일 stat 을 호출하므로, 모든 요청이 락 하나에 줄을 서게 된다.
     * 개발 환경이 WSL(/mnt/c)이면 stat 한 번이 5ms라 병목이 크게 드러나고,
     * 리눅스 서버에서도 크기만 작을 뿐 직렬화는 그대로 남는다.
     *
     * 스크립트는 실행 중에 바뀌지 않으므로 매번 확인할 이유가 없다.
     */
    public static String readScript(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Lua 스크립트를 읽지 못했다: " + path, e);
        }
    }

    /** 활성 요청 선점 (INV-1). 게임을 보지 않는다 — 나누면 게임마다 큐를 하나씩 잡을 수 있게 된다 */
    @Bean
    public RedisScript<Long> claimRequestScript() {
        return RedisScript.of(readScript("redis/shared/claim-request.lua"), Long.class);
    }

    /**
     * 제안 수락 + 확정 (INV-4 / INV-5). 파티 HASH 위에서만 돌고 조건을 읽지 않아 게임과 무관하다.
     *
     * @return NOT_FOUND | NOT_A_MEMBER | ACCEPTED | CONFIRMED | DECLINED
     */
    @Bean
    public RedisScript<String> acceptProposalScript() {
        return RedisScript.of(readScript("redis/proposal/accept-proposal.lua"), String.class);
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
        return RedisScript.of(readScript("redis/proposal/decline-proposal.lua"), String.class);
    }

    /**
     * 시한이 지난 제안 정리 (INV-5 expired).
     *
     * <p>수락·거절과 달리 {@code RedisScript<List>} 다. 돌려줄 것이 문자열 하나가 아니라
     * <b>수락하지 않은 사용자 목록</b>이기 때문이다 — 스위퍼가 그 사람들만 큐에서 뺀다.
     * 제네릭은 {@code List.class} 까지만 줄 수 있어 원시 타입을 쓴다(배정 스크립트들과 같다).
     *
     * <p>할 일이 없을 때(이미 확정·거절됐거나 아직 시한 전)는 <b>빈 목록</b>이 온다.
     * Lua 배열에 {@code nil}/{@code false} 를 넣으면 거기서 잘리므로 "없음"은 빈 테이블로 돌려준다.
     *
     * @return 수락하지 않은 userId 목록. 비어 있으면 처리할 것이 없었다는 뜻이다
     */
    /**
     * 확정된 제안 뒷정리 (파티원의 활성 요청 삭제 + 파티·수락자 집합 TTL).
     *
     * <p>확정을 찍는 {@link #acceptProposalScript} 와 나눠 둔 이유는 그 스크립트의 멱등성 때문이다 —
     * 수락자 집합을 다시 세어 판단하는데, 같은 실행에서 지워 버리면 재시도가 셀 근거를 잃는다.
     *
     * @return 정리한 파티원 userId 목록. 확정된 제안이 아니면 빈 목록
     */
    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> cleanupConfirmedScript() {
        return RedisScript.of(readScript("redis/proposal/cleanup-confirmed.lua"), List.class);
    }

    @Bean
    @SuppressWarnings("rawtypes")
    public RedisScript<List> expireProposalScript() {
        return RedisScript.of(readScript("redis/proposal/expiry-proposal.lua"), List.class);
    }
}
