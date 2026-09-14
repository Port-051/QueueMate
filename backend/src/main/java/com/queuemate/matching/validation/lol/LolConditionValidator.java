package com.queuemate.matching.validation.lol;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.lol.LolPosition;
import com.queuemate.matching.rule.lol.LolPartyKeys;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.validation.GameConditionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LolConditionValidator implements GameConditionValidator
{
    private static final String NO_POSITION = LolPosition.NONE.name();

    /**
     * enum 이름 집합.
     *
     * valueOf + try/catch 로 값을 검증하면 그 블록 안에서 생긴 다른 사고(NPE 등)까지
     * catch 에 빨려 들어가 "잘못된 값"으로 둔갑한다. 조회로 바꾸면 그럴 일이 없다.
     *
     * <p><b>티어에는 같은 집합이 없다.</b> 티어 값은 enum 이 아니라 Redis 데이터이고
     * ({@code qm:gameconfig:LOL:tier}), 값이 진짜인지는 아래 {@link #validTier} 가
     * 티어별 허용 범위 표를 조회하는 것으로 함께 확인한다 — 표에 없는 티어는 범위도 없다.
     */
    private static final Set<String> POSITIONS = names(LolPosition.values());

    private final StringRedisTemplate redis;
    private final LolPartyKeys keys;

    @Override
    public boolean supports(GameKey game)
    {
        return game == GameKey.LOL;
    }

    @Override
    public boolean validate(CreateMatchRequestCommand command)
    {
        // keyCondition 은 DTO 에서 @NotNull 을 뺐다. 없는 게임이 있을 수 있어서인데,
        // LoL 에서는 필수이므로 여기서 거른다. 이 가드가 없으면 아래에서 NPE(500)가 난다.
        if (command.getKeyCondition() == null)
        {
            return false;
        }

        if (command.getKeyCondition().getType() != KeyConditionType.POSITION)
        {
            return false;
        }

        // 모드 이름이 아니라 설정값으로 가른다.
        // 이름 규칙(startsWith("ARAM"))에 기대면 포지션 없는 모드가 새로 생길 때 조용히 깨진다.
        String configKey = "qm:gameconfig:" + GameKey.LOL + ":" + command.getModeKey();
        HashOperations<String, String, String> ops = redis.opsForHash();
        List<String> values = ops.multiGet(configKey, List.of("positionUniqueness", "tierRule"));
        String positionUniqueness = values.getFirst();
        String tierRule = values.get(1);

        // 존재 확인을 따로 하지 않는다. 없는 모드면 실제로 쓰는 두 값이 그대로 null 로 온다.
        // 필드 하나만 빠진 모드도 같이 걸린다 - 모드를 redis-cli 로 손으로 넣다 보면 흔한 실수이고,
        // 그때 positionUniqueness 를 null 로 두면 "true".equals(null) 이 false 가 되어
        // 랭크 모드에 칼바람 규칙이 조용히 적용된다. 설정이 불완전하면 매칭하지 않는다.
        if (positionUniqueness == null || tierRule == null)
        {
            return false;
        }
        boolean unique = "true".equals(positionUniqueness);

        // 포지션과 티어는 독립된 축이다. 한쪽을 다른 쪽 안에 넣으면
        // 설정을 켰는데 조용히 무시되는 경우가 생긴다.
        return validPosition(command, unique) && validTier(command, tierRule);
    }

    private boolean validPosition(CreateMatchRequestCommand c, boolean unique)
    {
        String position = c.getKeyCondition().getValue();

        if (!unique)
        {
            // 포지션 개념이 없는 모드다. NONE 하나로 모여야 색인이 갈라지지 않는다.
            return NO_POSITION.equals(position);
        }

        // 포지션이 겹치면 안 되는 모드다. 실제 포지션을 골라야 한다.
        return !NO_POSITION.equals(position) && POSITIONS.contains(position);
    }

    /**
     * 티어 조건. 볼지 말지는 gameconfig 의 {@code tierRule} 이 정한다.
     *
     *   NONE    티어를 안 보는 모드 (일반/칼바람)
     *   그 외    본다. 어떻게 보는지는 티어별 허용 범위 표가 정한다
     *
     * <p>예전에는 {@code TABLE} / {@code WINDOW} 로 갈려 "폭으로 거를지 표로 거를지"까지
     * 이 값이 정했다. 없앤 이유는 그 값이 실제 데이터와 어긋날 수 있었기 때문이다 —
     * {@code WINDOW} 라고 적어 놓고 {@code maxTierGap} 을 빠뜨리면 폭이 0 이 되어
     * 자기 티어하고만 매칭되는데 에러는 나지 않았다.
     *
     * <p>표 조회 하나가 두 가지를 같이 본다. <b>값이 진짜 티어인가</b>(표에 없으면
     * 규칙을 모르는 값이다)와 <b>그 모드에서 파티가 가능한가</b>({@code SOLO_ONLY})다.
     * 그래서 티어 이름 목록을 따로 들고 있을 필요가 없다.
     *
     * <p>규칙을 바꾸는 것은 전부 Redis 쪽이다. 표를 고쳐도 이 코드는 그대로다.
     */
    private boolean validTier(CreateMatchRequestCommand c, String tierRule)
    {
        // tierRule 이 null 인 경우는 호출부에서 이미 걸렀다.
        if ("NONE".equals(tierRule))
        {
            return true;
        }

        if (c.getTier() == null)
        {
            return false;
        }
        // 표에 그 티어가 아예 없으면 규칙을 모르는 것이므로 통과시키지 않는다.
        String range = redis.<String, String>opsForHash()
                .get(keys.tierRangeKey(c), c.getTier());
        return range != null && !"SOLO_ONLY".equals(range);
    }

    private static Set<String> names(Enum<?>[] values)
    {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
