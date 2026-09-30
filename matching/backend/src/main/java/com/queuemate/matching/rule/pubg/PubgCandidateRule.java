package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.block.BlockRepository;
import com.queuemate.matching.block.BlockedUsers;
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

import java.util.HashSet;
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

        // 내 차단 목록은 어느 후보를 보든 같으므로 락을 잡기 전에 한 번만 가져온다 (PoolLock 클래스 주석).
        // 차단(DB)에 더해 최근 거절 기록(Redis qm:user:declined:{me}, score = 풀리는 시각)도 같이 거른다 —
        // 거절 때 양쪽에 적으므로 내 키 하나로 충분하다(ProposalService#recordDeclined).
        // BlockedUsers.of 는 고칠 수 없는 집합을 주므로 복사해서 합친다
        Set<String> blockedUserIds = new HashSet<>(BlockedUsers.of(blockRepository, command.getUserId()));
        blockedUserIds.addAll(redis.opsForZSet().rangeByScore(
                SharedKeys.declinedKey(command.getUserId()), System.currentTimeMillis(), Double.POSITIVE_INFINITY));

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
