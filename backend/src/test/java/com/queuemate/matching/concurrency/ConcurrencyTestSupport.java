package com.queuemate.matching.concurrency;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.condition.PlayPurpose;
import com.queuemate.matching.domain.condition.VoicePreference;
import com.queuemate.matching.domain.condition.KeyConditionType;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * 동시성 테스트 공통 준비.
 *
 * 개발용 데이터를 건드리지 않도록 Redis DB 15번을 쓰고, 매 테스트마다 비운다.
 * (docker compose의 로컬 Redis가 떠 있어야 한다)
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.database=15",
        "logging.level.com.queuemate=WARN",
        // INV-6 이 미구현이라 blocks 스키마가 없다 (CLAUDE.md §4). 그런데 배정 경로는
        // 락을 잡기 전에 BlockRepository 를 부르므로(LolCandidateRule#canJoin),
        // 테이블이 없으면 모든 join 이 조용히 실패해 파티가 하나도 안 생긴다.
        // H2 인메모리에 엔티티대로 테이블만 만들어 준다. Flyway 가 생기면 지운다.
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public abstract class ConcurrencyTestSupport {

    @Autowired
    protected StringRedisTemplate redis;

    @BeforeEach
    protected void resetRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        seedLolGameConfig("RANKED_SOLO", 2, true);
        seedLolGameConfig("RANKED_FLEX_5", 5, true);
        seedLolGameConfig("ARAM_5", 5, false);
    }

    private void seedLolGameConfig(String modeKey, int targetPartySize, boolean positionUniqueness) {
        redis.opsForHash().putAll("qm:gameconfig:LOL:" + modeKey, Map.of(
                "targetPartySize", String.valueOf(targetPartySize),
                "positionUniqueness", String.valueOf(positionUniqueness)));
    }

    /**
     * LoL 기본 요청. 기존 테스트가 전부 이 3-arg 형태를 쓰고 있으므로 시그니처를 유지한다.
     */
    protected CreateMatchRequestCommand command(String userId, String modeKey, String keyValue) {
        return command(userId, GameKey.LOL, KeyConditionType.POSITION, modeKey, keyValue);
    }

    /** 게임과 조건 타입까지 지정하는 형태. 게임을 늘릴 때 이 오버로드를 쓴다. */
    protected CreateMatchRequestCommand command(String userId,
                                                GameKey game,
                                                KeyConditionType type,
                                                String modeKey,
                                                String keyValue) {
        CreateMatchRequestCommand.KeyCondition keyCondition = new CreateMatchRequestCommand.KeyCondition();
        keyCondition.setType(type);
        keyCondition.setValue(keyValue);

        CreateMatchRequestCommand command = new CreateMatchRequestCommand();
        command.setUserId(userId);
        command.setGame(game);
        command.setModeKey(modeKey);
        command.setKeyCondition(keyCondition);
        command.setVoicePreference(VoicePreference.REQUIRED);
        command.setPlayPurpose(PlayPurpose.RANK_UP);
        return command;
    }

    /**
     * 여러 스레드를 만들어 두고 한 번에 출발시킨다.
     *
     * 순차 실행이면 경합이 재현되지 않는다. CountDownLatch로 전부 대기시켰다가
     * 동시에 풀어야 "확인 후 쓰기" 사이의 틈이 드러난다.
     */
    protected void runConcurrently(int threads, IntConsumer task) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread.startVirtualThread(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(index);
                } catch (Exception ignored) {
                    // 실패한 시도는 개별 테스트가 카운터로 센다
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
    }

    /**
     * 파티 HASH 키 전부.
     *
     * <p><b>주의</b> — 게임을 가리지 않는다. 한 테스트 메서드에서 서로 다른 게임을 함께 join시키면
     * 서로의 파티까지 세게 된다. 게임별 단언은 그 게임만 join한 상태에서 해라.
     */
    protected List<String> partyKeys() {
        return redis.keys("qm:party:*").stream()
                .filter(k -> !k.contains(":open:"))
                .toList();
    }

    /** 파티 HASH에서 member:{userId} 필드의 값(= keyValue)만 뽑는다. */
    protected List<String> memberValues(String partyKey) {
        return redis.<Object, Object>opsForHash().entries(partyKey).entrySet().stream()
                .filter(e -> ((String) e.getKey()).startsWith("member:"))
                .map(e -> (String) e.getValue())
                .toList();
    }
}
