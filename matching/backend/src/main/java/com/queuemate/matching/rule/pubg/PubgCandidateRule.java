package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import com.queuemate.matching.redisLock.PoolLock;
import com.queuemate.matching.rule.CandidateRule;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PubgCandidateRule implements CandidateRule
{
    private final StringRedisTemplate redis;
    private final PoolLock poolLock;
    private final PubgPartyKeys keys;
    private final PubgTieredAssigner tieredAssigner;
    private final PubgUntieredAssigner untieredAssigner;
    private final PubgPartyLeaver partyLeaver;

    @Override
    public boolean supports(GameKey game)
    {
        return GameKey.PUBG == game;
    }

    @Override
    public void canJoin(CreateMatchRequestCommand command)
    {
        PubgModeConfig config = loadModeConfig(command);

        // 최근 거절 기록(Redis qm:user:declined:{me}, score = 풀리는 시각 — docs/11 D-45)은 어느 후보를 보든 같다.
        // 후보마다 다시 물을 이유가 없으므로 락을 잡기 전에 한 번만 가져온다. 거절 때 양쪽에 적으므로 내 키 하나로
        // 충분하다(ProposalService#recordDeclined).
        // 차단(INV-6)은 여기서 읽지 않는다 — 합류 스크립트가 qm:user:block-rel:{me} 를 KEYS 로 받아 같은 원자 실행 안에서
        // 본다(docs/11 D-57). 2026-10-02 까지는 여기서 DB 의 blocks 를 읽어 이 집합에 합쳤다
        Set<String> declinedUserIds = redis.opsForZSet().rangeByScore(
                SharedKeys.declinedKey(command.getUserId()), System.currentTimeMillis(), Double.POSITIVE_INFINITY);

        if (command.getTier() == null) {
            poolLock.run(keys.poolKey(command),
                    () -> untieredAssigner.assign(command, config, declinedUserIds));
        } else {
            poolLock.run(keys.poolKey(command),
                    () -> tieredAssigner.assign(command, config, declinedUserIds));
        }

    }

    private PubgModeConfig loadModeConfig(CreateMatchRequestCommand command) {
        HashOperations<String, String, String> ops = redis.opsForHash();
        List<String> config = ops.multiGet(
                keys.gameConfigKey(command),
                List.of("targetPartySize", "tierRule"));

        return new PubgModeConfig(config.get(0), config.get(1));
    }

    @Override
    public CancelResult leave(ActiveRequest active, String expectedRequestId) {
        return partyLeaver.leave(active, expectedRequestId);
    }
}
