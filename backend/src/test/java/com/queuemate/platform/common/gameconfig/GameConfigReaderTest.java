package com.queuemate.platform.common.gameconfig;

import com.queuemate.platform.account.domain.Game;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>{@link GameConfigReader} 의 fail-open</b>(2026-09-24 소유자 결정 — {@code contracts/platform-api.md} "gameconfig 를 읽는 것").
 * gameconfig 를 읽을 수 없으면 <b>검증을 건너뛰고 통과시킨다</b> — 글 쓰기 · 게임 계정 연결이 Redis 에 묶여 같이 죽지 않게 한다.
 *
 * <p>스프링을 띄우지 않는다 — {@code StringRedisTemplate} 의 명령이 지나는 {@code execute} 를 갈아 끼우면 진짜 Redis 가 필요하지 않다.
 * <b>진짜 Redis 를 죽이는 테스트는 만들지 않는다</b>(같은 Redis 를 {@code room} 과 다른 프로젝트가 쓴다 — {@code CLAUDE.md} §9).
 * "있는데 없는 값이다 → 400" 쪽은 gameconfig 를 심어 놓고 보는 API 테스트({@code PostApiTest} · {@code UserApiTest})가 덮는다.
 */
class GameConfigReaderTest {

    @Test
    @DisplayName("Redis 를 못 읽으면 통과시킨다 — 그리고 정말 물어보기는 했다")
    void failsOpenWhenRedisIsDown()
    {
        AtomicInteger attempts = new AtomicInteger();
        GameConfigReader reader = new GameConfigReader(new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline)
            {
                attempts.incrementAndGet();
                throw new RedisConnectionFailureException("테스트 — Redis 가 죽었다");
            }
        });

        assertThat(reader.hasMode(Game.LOL, "무엇이든")).isTrue();
        assertThat(reader.hasTier(Game.LOL, "무엇이든")).isTrue();
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("gameconfig 가 안 심긴 Redis 에서도 통과시킨다 — 검증할 원본이 없는 것과 값이 틀린 것은 다르다")
    void failsOpenWhenGameConfigIsNotSeeded()
    {
        // 모든 명령이 "그런 키 없다"로 답하는 Redis — seed 를 붓지 않고 앱을 띄운 상태다
        GameConfigReader reader = new GameConfigReader(new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline)
            {
                return null;
            }
        });

        assertThat(reader.hasMode(Game.PUBG, "NORMAL_SQUAD_TPP")).isTrue();
        assertThat(reader.hasTier(Game.PUBG, "GOLD_1")).isTrue();
    }
}
