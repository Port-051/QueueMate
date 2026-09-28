package com.queuemate.matching.rule.valorant;

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
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VALORANT 의 {@link CandidateRule} 구현체. <b>일을 어디에 맡길지만 정한다.</b>
 *
 * <p>배정도 취소도 직접 하지 않는다. 모드 설정을 읽고, 차단 목록을 (락 밖에서) 가져오고,
 * 티어 유무로 assigner 를 골라 후보 풀 락 안에서 부르는 데까지가 배정에 대한 몫이다. 실제 인자
 * 조립과 스크립트 호출은 {@link ValorantUntieredAssigner} / {@link ValorantTieredAssigner} 가 한다.
 *
 * <p>취소도 같은 모양이다. {@link ValorantPartyLeaver} 가 통째로 맡는다 — 배정과 달리 고를 것이
 * 없어(스크립트가 한 벌이다) 여기서는 넘기기만 한다.
 */
@Component
@RequiredArgsConstructor
public class ValorantCandidateRule implements CandidateRule {

    private final StringRedisTemplate redis;
    private final PoolLock poolLock;
    private final BlockRepository blockRepository;
    private final ValorantPartyKeys keys;
    private final ValorantUntieredAssigner untieredAssigner;
    private final ValorantTieredAssigner tieredAssigner;
    private final ValorantPartyLeaver partyLeaver;

    @Override
    public boolean supports(GameKey game) {
        return GameKey.VALORANT == game;
    }

    @Override
    public void canJoin(CreateMatchRequestCommand command) {
        ValorantModeConfig config = loadModeConfig(command);

        // 내 차단 목록은 어느 후보를 보든 같다. 후보마다 다시 물을 이유가 없으므로 락을 잡기 전에
        // 한 번만 가져온다. 락 안에서 DB 를 치면 응답이 늦을 때 유지 시간을 넘겨 락이 저 혼자
        // 풀린다 (PoolLock 클래스 주석).
        // 차단(DB)에 더해 최근 거절 기록(Redis qm:user:declined:{me}, score = 풀리는 시각)도 같이 거른다 —
        // 거절 때 양쪽에 적으므로 내 키 하나로 충분하다(ProposalService#recordDeclined).
        // BlockedUsers.of 는 고칠 수 없는 집합을 주므로 복사해서 합친다
        Set<String> blockedUserIds = new HashSet<>(BlockedUsers.of(blockRepository, command.getUserId()));
        blockedUserIds.addAll(redis.opsForZSet().rangeByScore(
                SharedKeys.declinedKey(command.getUserId()), System.currentTimeMillis(), Double.POSITIVE_INFINITY));

        // 티어 유무는 모드 이름이 아니라 요청에 tier 가 있는지로 가른다. 값 검증은
        // ValorantConditionValidator 가 이미 끝냈다 — tierRule 이 NONE 인 모드에 tier 가
        // 붙어 있거나 그 반대면 여기까지 오지 못한다.
        if (command.getTier() == null) {
            poolLock.run(keys.poolKey(command),
                    () -> untieredAssigner.assign(command, config, blockedUserIds));
        } else {
            poolLock.run(keys.poolKey(command),
                    () -> tieredAssigner.assign(command, config, blockedUserIds));
        }
    }

    /**
     * 이번 배정에 필요한 모드 설정. gameconfig 는 Redis 에서 읽기만 한다 (CLAUDE.md §3).
     *
     * <p>{@code positionUniqueness} 도 같이 읽는다. 지금은 모든 모드가 {@code true} 지만, 역할군
     * 중복 금지는 게임 규칙이 아니라 우리가 정한 제품 규칙이라 나중에 바뀔 수 있다. 그때 고칠 곳이
     * 시드 한 곳이 되도록 코드에 굳히지 않는다 ({@link ValorantModeConfig} 주석).
     */
    private ValorantModeConfig loadModeConfig(CreateMatchRequestCommand command) {
        HashOperations<String, String, String> ops = redis.opsForHash();
        List<String> config = ops.multiGet(
                keys.gameConfigKey(command),
                List.of("targetPartySize", "positionUniqueness", "tierRule"));

        boolean unique = "true".equals(config.get(1));

        // 중복을 금지하면 역할군 4개가 색인 대상이고, 허용하는 모드라면 들어온 값 하나뿐이다.
        // 취소(ValorantPartyLeaver)도 같은 규칙이어야 한다 — 되돌릴 자리는 등록한 자리와 짝이다.
        List<String> keyValues = unique
                ? ValorantPartyKeys.ROLES
                : List.of(command.getKeyCondition().getValue());

        return new ValorantModeConfig(config.get(0), unique, keyValues, config.get(2));
    }

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다. {@link ValorantPartyLeaver} 에 그대로 넘긴다.
     *
     * <p>배정처럼 티어 유무로 갈라 주지 않는다. {@code leave-party.lua} 는 한 벌이고 티어를 보지
     * 않는 모드를 빈 접미사로 접어 처리하므로, 고를 것이 애초에 없다.
     */
    @Override
    public CancelResult leave(ActiveRequest active, String expectedRequestId) {
        return partyLeaver.leave(active, expectedRequestId);
    }
}
