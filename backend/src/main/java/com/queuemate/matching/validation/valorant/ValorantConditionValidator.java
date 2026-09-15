package com.queuemate.matching.validation.valorant;


import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.valorant.ValorantRole;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.validation.GameConditionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class ValorantConditionValidator implements GameConditionValidator
{
    private final StringRedisTemplate redis;

    @Override
    public boolean supports(GameKey game)
    {
        return GameKey.VALORANT == game;
    }

    @Override
    public boolean validate(CreateMatchRequestCommand command)
    {
        // keyCondition 은 DTO 에서 @NotNull 이 빠져 있다. 발로란트에서는 필수이므로 여기서 거른다.
        // 이 가드가 없으면 아래 getType() 에서 NPE(500)가 난다.
        if (command.getKeyCondition() == null)
        {
            return false;
        }

        KeyConditionType type = command.getKeyCondition().getType();
        String role = command.getKeyCondition().getValue();

        if(type !=  KeyConditionType.ROLE)
        {
            return false;
        }
        boolean match = Arrays.stream(ValorantRole.values()).map(ValorantRole::name).anyMatch(role::equals);
        if(!match)
        {
            return false;
        }
        String mode = command.getModeKey();
        String tierRule = redis.<String, String>opsForHash().get("qm:gameconfig:VALORANT:" + mode, "tierRule");
        String tier = command.getTier();
        if (tierRule == null)
        {
            return false;
        }
        if(tierRule.equals("NONE"))
        {
            return tier == null;
        }
        // 티어를 보는 모드인데 tier 가 없다. 아래 rank(key, null) 은 Spring 이
        // IllegalArgumentException 을 던져 400 이 아니라 500 이 되므로 먼저 막는다.
        if (tier == null)
        {
            return false;
        }
        Long exist = redis.<String, String>opsForZSet().rank("qm:gameconfig:VALORANT:tier", tier);
        if (exist == null)
        {
            return false;
        }

        // 사다리에 있어도 이 모드에서 파티를 못 짜는 티어가 있다 (UNRANKED, 트리오의 IMMORTAL_1 이상).
        // 여기서 통과시키면 요청은 201 로 나가고 비동기 배정의 Lua 가 -1 로 조용히 끝나
        // 사용자는 claim 이 만료될 때까지 대기 화면에 남는다. 그래서 표까지 본다.
        String range = redis.<String, String>opsForHash()
                .get("qm:gameconfig:VALORANT:tier-range:" + mode, tier);
        return range != null && !range.equals("SOLO_ONLY");
    }
}
