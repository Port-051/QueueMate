package com.queuemate.matching.validation.pubg;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import com.queuemate.matching.validation.GameConditionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PubgConditionValidator implements GameConditionValidator {

    /** 스팀과 카카오는 서버가 분리돼 서로 파티를 맺을 수 없다 (KeyConditionType 클래스 주석) */
    private static final Set<String> PLATFORMS = Set.of("STEAM", "KAKAO");

    private final StringRedisTemplate redis;

    @Override
    public boolean supports(GameKey game) {

        return game.equals(GameKey.PUBG);
    }

    @Override
    public boolean validate(CreateMatchRequestCommand command)
    {
        // keyCondition 은 DTO 에서 @NotNull 을 뺐다(없는 게임이 있을 수 있어서). PUBG 에서는
        // 필수이므로 여기서 거른다. 이 가드가 없으면 아래 getValue() 에서 NPE(500)가 난다.
        if (command.getKeyCondition() == null)
        {
            return false;
        }

        if (command.getKeyCondition().getType() != KeyConditionType.PLATFORM)
        {
            return false;
        }

        String platform = command.getKeyCondition().getValue();
        if (!PLATFORMS.contains(platform))
        {
            return false;
        }

        // 모드가 있는지는 모드 HASH 가 답한다. 없는 모드면 필드가 null 로 온다.
        String mode = command.getModeKey();
        String tierRule = redis.<String, String>opsForHash()
                .get(SharedKeys.gameConfigKey(GameKey.PUBG, mode), "tierRule");

        if (tierRule == null)
        {
            return false;
        }

        String tier  = command.getTier();

        // 티어를 안 보는 모드에는 tier 를 싣지 않는다. 롤과 같은 규약이다(LolConditionValidator#validTier) —
        // 게임마다 클라이언트 규약이 달라지지 않게 맞춘다. 실려 오면 통과시키지 않는다.
        if (tierRule.equals("NONE"))
        {
            return tier == null;
        }

        if (tier == null)
        {
            return false;
        }

        Double score = redis.opsForZSet().score(SharedKeys.tierKey(GameKey.PUBG), tier);

        if(score == null)
        {
            return false;
        }

        String range = redis.<String, String>opsForHash()
                .get(SharedKeys.tierRangeKey(GameKey.PUBG, mode), tier);

        return range != null && !range.equals("SOLO_ONLY");
    }
}
