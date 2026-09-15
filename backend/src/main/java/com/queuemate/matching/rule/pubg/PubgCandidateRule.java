package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.block.BlockRepository;
import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
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
    private final BlockRepository blockRepository;
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

        Set<String> blockedUserIds = Set.copyOf(blockRepository.findBlockedUserIds(command.getUserId()));

        if (command.getTier() == null) {
            poolLock.run(keys.poolKey(command),
                    () -> untieredAssigner.assign(command, config, blockedUserIds));
        } else {
            poolLock.run(keys.poolKey(command),
                    () -> tieredAssigner.assign(command, config, blockedUserIds));
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
