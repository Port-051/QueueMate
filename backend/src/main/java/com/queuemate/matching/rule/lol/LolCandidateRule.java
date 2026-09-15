package com.queuemate.matching.rule.lol;

import com.queuemate.matching.block.BlockRepository;
import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.lol.LolPosition;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisLock.PoolLock;
import com.queuemate.matching.rule.CandidateRule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * LoL 의 {@link CandidateRule} 구현체. <b>일을 어디에 맡길지만 정한다.</b>
 *
 * <p>배정도 취소도 직접 하지 않는다. 모드 설정을 읽고, 차단 목록을 (락 밖에서) 가져오고,
 * 티어 유무로 assigner 를 골라 후보 풀 락 안에서 부르는 데까지가 배정에 대한 몫이다.
 * 실제 인자 조립과 스크립트 호출은 {@link LolUntieredAssigner} / {@link LolTieredAssigner} 가 한다.
 *
 * <p>취소도 같은 모양이다. {@link LolPartyLeaver} 가 통째로 맡는다 — 배정과 달리 고를 것이
 * 없어(스크립트가 한 벌이다) 여기서는 넘기기만 한다.
 */
@Component
@RequiredArgsConstructor
public class LolCandidateRule implements CandidateRule {

    private final StringRedisTemplate redis;
    private final PoolLock poolLock;
    private final BlockRepository blockRepository;
    private final LolPartyKeys keys;
    private final LolUntieredAssigner untieredAssigner;
    private final LolTieredAssigner tieredAssigner;
    private final LolPartyLeaver partyLeaver;

    @Override
    public boolean supports(GameKey game) {
        return game == GameKey.LOL;
    }

    @Override
    public void canJoin(CreateMatchRequestCommand command) {
        LolModeConfig config = loadModeConfig(command);

        // 내 차단 목록은 어느 후보를 보든 같다. 후보마다 다시 물을 이유가 없으므로
        // 락을 잡기 전에 한 번만 가져온다. 락 안에서 DB 를 치면 응답이 늦을 때
        // 유지 시간을 넘겨 락이 저 혼자 풀린다 (PoolLock 클래스 주석).
        Set<String> blockedUserIds = Set.copyOf(blockRepository.findBlockedUserIds(command.getUserId()));

        if (command.getTier() == null) {
            poolLock.run(keys.poolKey(command),
                    () -> untieredAssigner.assign(command, config, blockedUserIds));
        } else {
            poolLock.run(keys.poolKey(command),
                    () -> tieredAssigner.assign(command, config, blockedUserIds));
        }
    }

    /** 이번 배정에 필요한 모드 설정. gameconfig 는 Redis 에서 읽기만 한다 (CLAUDE.md §3). */
    private LolModeConfig loadModeConfig(CreateMatchRequestCommand command) {
        HashOperations<String, String, String> ops = redis.opsForHash();
        List<String> config = ops.multiGet(
                keys.gameConfigKey(command),
                List.of("targetPartySize", "positionUniqueness", "tierRule"));

        boolean unique = "true".equals(config.get(1));

        // 중복을 금지하는 모드면 포지션 5개가 색인 대상이고,
        // 칼바람처럼 포지션이 없는 모드는 들어온 값 하나뿐이다.
        List<String> keyValues = unique
                ? Arrays.stream(LolPosition.values()).map(Enum::name).toList()
                : List.of(command.getKeyCondition().getValue());

        return new LolModeConfig(config.get(0), unique, keyValues, config.get(2));
    }

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다. {@link LolPartyLeaver} 에 그대로 넘긴다.
     *
     * <p>배정처럼 티어 유무로 갈라 주지 않는다. {@code leave-party.lua} 는 한 벌이고
     * 티어 모드를 T 가 1보다 큰 격자로만 볼 뿐이라, 고를 것이 애초에 없다.
     */
    @Override
    public CancelResult leave(ActiveRequest active, String expectedRequestId) {
        return partyLeaver.leave(active, expectedRequestId);
    }
}
