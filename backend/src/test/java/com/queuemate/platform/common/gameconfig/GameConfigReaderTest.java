package com.queuemate.platform.common.gameconfig;

import com.queuemate.platform.account.domain.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>{@link GameConfigReader} 의 두 정책</b> — {@code mode} · {@code tier} 검증의 <b>fail-open</b>(2026-09-24 소유자 결정 — {@code contracts/platform-api.md} "gameconfig 를 읽는 것".
 * gameconfig 를 읽을 수 없으면 검증을 건너뛰고 통과시킨다 — 글 쓰기 · 게임 계정 연결이 Redis 에 묶여 같이 죽지 않게 한다)과, 게시판 방 먼저 합류가 읽는 쪽의
 * <b>fail-closed</b>(2026-09-28 — 못 읽으면 {@link GameConfigUnavailableException}).
 *
 * <p>죽은 Redis · 안 심긴 Redis 는 스프링을 띄우지 않는다 — {@code StringRedisTemplate} 의 명령이 지나는 {@code execute} 를 갈아 끼우면 진짜 Redis 가 필요하지 않다.
 * <b>진짜 Redis 를 죽이는 테스트는 만들지 않는다</b>(같은 Redis 를 {@code room} 과 다른 프로젝트가 쓴다 — {@code CLAUDE.md} §9).
 * "있는데 없는 값이다 → 400" 쪽은 gameconfig 를 심어 놓고 보는 API 테스트({@code PostApiTest} · {@code UserApiTest} · {@code AutoJoinTest})가 덮는다.
 *
 * <p><b>새 메서드({@code seeded} · {@code modeConfig} · {@code tierScores} · {@code tierRanges})는 진짜 Redis(6380)에서 읽어 본다</b> — 접두사 · 필드 이름이 seed 와 한 글자만
 * 어긋나도 컴파일 · 테스트가 통과한 채로 조용히 틀리는 종류라, 가짜가 아니라 seed 의 모양대로 심은 키를 읽어야 뜻이 있다. 이 테스트가 심은 키만 지운다.
 */
class GameConfigReaderTest {

    // ---- fail-open (2026-09-24) ----

    @Test
    @DisplayName("Redis 를 못 읽으면 통과시킨다 — 그리고 정말 물어보기는 했다")
    void failsOpenWhenRedisIsDown()
    {
        AtomicInteger attempts = new AtomicInteger();
        GameConfigReader reader = new GameConfigReader(deadRedis(attempts));
        assertThat(reader.hasMode(Game.LOL, "무엇이든")).isTrue();
        assertThat(reader.hasTier(Game.LOL, "무엇이든")).isTrue();
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("gameconfig 가 안 심긴 Redis 에서도 통과시킨다 — 검증할 원본이 없는 것과 값이 틀린 것은 다르다")
    void failsOpenWhenGameConfigIsNotSeeded()
    {
        GameConfigReader reader = new GameConfigReader(emptyRedis());
        assertThat(reader.hasMode(Game.PUBG, "NORMAL_SQUAD_TPP")).isTrue();
        assertThat(reader.hasTier(Game.PUBG, "GOLD_1")).isTrue();
    }

    // ---- fail-closed (2026-09-28 — 게시판 방 먼저 합류) ----

    @Test
    @DisplayName("게시판 방 먼저 합류가 읽는 쪽은 Redis 를 못 읽으면 던진다 — 통과시키지 않는다")
    void failsClosedWhenRedisIsDown()
    {
        AtomicInteger attempts = new AtomicInteger();
        GameConfigReader reader = new GameConfigReader(deadRedis(attempts));
        assertThatThrownBy(() -> reader.seeded(Game.LOL)).isInstanceOf(GameConfigUnavailableException.class);
        assertThatThrownBy(() -> reader.modeConfig(Game.LOL, "RANKED_SOLO")).isInstanceOf(GameConfigUnavailableException.class);
        assertThatThrownBy(() -> reader.tierScores(Game.LOL, List.of("GOLD_4"))).isInstanceOf(GameConfigUnavailableException.class);
        assertThatThrownBy(() -> reader.tierRanges(Game.LOL, "RANKED_SOLO", List.of("GOLD_4"))).isInstanceOf(GameConfigUnavailableException.class);
        assertThat(attempts).hasValue(4);
    }

    @Test
    @DisplayName("안 심긴 Redis 는 '안 심겼다' 로 답한다 — 모드 설정은 비어 있고 티어는 하나도 없다")
    void reportsNotSeeded()
    {
        GameConfigReader reader = new GameConfigReader(emptyRedis());
        assertThat(reader.seeded(Game.LOL)).isFalse();
        assertThat(reader.modeConfig(Game.LOL, "RANKED_SOLO")).isEmpty();
        assertThat(reader.tierScores(Game.LOL, List.of("GOLD_4"))).isEmpty();
        assertThat(reader.tierRanges(Game.LOL, "RANKED_SOLO", List.of("GOLD_4"))).isEmpty();
    }

    // ---- 진짜 Redis 에서 seed 의 모양대로 읽는다 ----

    /** 이 테스트가 심은 키 — 끝나면 지운다. 사다리 키는 있으면 건드리지 않는다 */
    private final List<String> seededKeys = new ArrayList<>();
    private LettuceConnectionFactory factory;

    @AfterEach
    void cleanUp()
    {
        if(factory != null)
        {
            if(!seededKeys.isEmpty())
            {
                new StringRedisTemplate(factory).delete(seededKeys);
            }
            factory.destroy();
        }
    }

    @Test
    @DisabledIf(value = "com.queuemate.platform.ApiTestSupport#pointsAtForeignPorts",
            disabledReason = "REDIS_PORT=6379 다 — 다른 프로젝트의 것이다. 테스트용을 6380 으로 띄워라")
    @DisplayName("모드 HASH 의 tierRule · targetPartySize, 사다리의 score, tier-range 의 줄을 seed 의 모양 그대로 읽는다 — 없는 것은 없다고 답한다")
    void readsSeededGameConfigFromRedis()
    {
        StringRedisTemplate redis = realRedis();
        GameConfigReader reader = new GameConfigReader(redis);
        // 이 테스트만의 모드 이름이다 — 소유자의 seed 와 겹치지 않는다. 필드 이름 · 값의 모양은 matching/seed/gameconfig.redis 의 것이다
        String mode = "TEST_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String modeKey = "qm:gameconfig:LOL:" + mode;
        String rangeKey = "qm:gameconfig:LOL:tier-range:" + mode;
        String ladderKey = "qm:gameconfig:LOL:tier";
        seededKeys.add(modeKey);
        seededKeys.add(rangeKey);
        redis.opsForHash().putAll(modeKey, Map.of("targetPartySize", "3", "positionUniqueness", "true", "tierRule", "EXIST"));
        redis.opsForHash().putAll(rangeKey, Map.of("GOLD_4", "SILVER_4:PLATINUM_1", "UNRANKED", "SOLO_ONLY"));
        if(!Boolean.TRUE.equals(redis.hasKey(ladderKey)))
        {
            seededKeys.add(ladderKey);
        }
        // seed 의 score 그대로다 — 소유자의 사다리가 있으면 같은 값이라 바뀌지 않는다
        redis.opsForZSet().add(ladderKey, "SILVER_4", 9);
        redis.opsForZSet().add(ladderKey, "GOLD_4", 13);
        redis.opsForZSet().add(ladderKey, "PLATINUM_1", 20);

        assertThat(reader.seeded(Game.LOL)).isTrue();
        assertThat(reader.modeConfig(Game.LOL, mode)).contains(new ModeConfig("EXIST", 3));
        assertThat(reader.modeConfig(Game.LOL, mode + "_NOPE")).isEmpty();
        assertThat(reader.tierScores(Game.LOL, List.of("GOLD_4", "SILVER_4", "PLATINUM_1", "NO_SUCH_TIER")))
                .containsEntry("GOLD_4", 13.0).containsEntry("SILVER_4", 9.0).containsEntry("PLATINUM_1", 20.0)
                .doesNotContainKey("NO_SUCH_TIER");
        assertThat(reader.tierRanges(Game.LOL, mode, List.of("GOLD_4", "UNRANKED", "NO_SUCH_TIER")))
                .containsEntry("GOLD_4", "SILVER_4:PLATINUM_1").containsEntry("UNRANKED", "SOLO_ONLY")
                .doesNotContainKey("NO_SUCH_TIER");
        // 읽기만 했다 — 심은 모양 그대로다
        assertThat(redis.opsForHash().entries(modeKey)).hasSize(3);
    }

    private StringRedisTemplate realRedis()
    {
        String host = envOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(envOrDefault("REDIS_PORT", "6380"));
        factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        factory.start();
        return new StringRedisTemplate(factory);
    }

    private static String envOrDefault(String name, String defaultValue)
    {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /** 모든 명령에 연결 실패를 던지는 Redis — 죽은 Redis 다. 몇 번 물었는지 센다 */
    private static StringRedisTemplate deadRedis(AtomicInteger attempts)
    {
        return new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline)
            {
                attempts.incrementAndGet();
                throw new RedisConnectionFailureException("테스트 — Redis 가 죽었다");
            }
        };
    }

    /** 모든 명령이 "그런 키 없다"로 답하는 Redis — seed 를 붓지 않고 앱을 띄운 상태다 */
    private static StringRedisTemplate emptyRedis()
    {
        return new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline)
            {
                return null;
            }
        };
    }
}
