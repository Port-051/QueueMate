package com.queuemate.matching.rule.lol;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import org.springframework.stereotype.Component;

/**
 * LoL 배정이 쓰는 Redis 키를 조립한다.
 *
 * <p>키 조립만 따로 모은 이유는 <b>배정 경로와 취소 경로가 같은 키를 만들어야</b> 하기 때문이다.
 * 조립 규칙이 여러 클래스로 흩어지면 한쪽만 고쳐도 컴파일은 통과하고, 그 순간부터
 * 되돌려 놓은 자리를 아무도 찾지 못한다.
 */
@Component
public class LolPartyKeys {

    /** 이 클래스가 조립하는 것은 LoL 의 needs 색인과 gameconfig 뿐이다. 나머지는 {@link SharedKeys} */
    private static final GameKey GAME = GameKey.LOL;

    /** 잠글 후보 풀. needs 키에서 keyValue 만 뺀 조합이어야 같은 색인을 잠근다 (PoolLock 참고). */
    public String poolKey(CreateMatchRequestCommand command) {
        return SharedKeys.poolKey(GAME, command.getModeKey(),
                command.getVoicePreference().name(),
                command.getPlayPurpose().name());
    }

    /** 그 keyValue를 아직 못 채운 파티 목록 */
    public String needsKey(CreateMatchRequestCommand command, String keyValue) {
        return SharedKeys.needsKey(poolKey(command), keyValue);
    }

    /** 취소 경로에서 쓰는 needs 키. 조립 규칙은 canJoin 쪽과 같아야 한다. */
    public String needsKey(ActiveRequest active, String keyValue) {
        return SharedKeys.needsKey(poolKey(active), keyValue);
    }

    /** 취소 경로의 후보 풀 키. 배정 쪽과 같은 문자열이어야 한다. */
    private String poolKey(ActiveRequest active) {
        return SharedKeys.poolKey(GAME, active.modeKey(),
                active.voicePreference().name(),
                active.playPurpose().name());
    }

    /**
     * 티어 격자의 한 칸을 가리키는 needs 키.
     *
     * <p>배정({@link LolTieredAssigner})과 취소({@link LolPartyLeaver})가 <b>문자열까지 같은 키</b>를
     * 만들어야 한다. 취소는 배정이 색인에 올려 둔 파티를 그 키에서 빼거나 되돌리는데,
     * 접미사 규칙이 한쪽만 달라지면 컴파일은 통과하고 비운 자리를 아무도 못 찾게 된다.
     * 그래서 양쪽 다 인라인으로 붙이던 {@code ":" + tier} 를 여기 한 자리로 모았다.
     *
     * @param tierName 티어 사다리({@code qm:gameconfig:LOL:tier})에 있는 티어 이름
     */
    public String needsKey(CreateMatchRequestCommand command, String keyValue, String tierName) {
        return SharedKeys.withTier(needsKey(command, keyValue), tierName);
    }

    /** 위와 같은 키를 취소 경로에서 만든다. */
    public String needsKey(ActiveRequest active, String keyValue, String tierName) {
        return SharedKeys.withTier(needsKey(active, keyValue), tierName);
    }

    public String partyKey(String partyId) {
        return SharedKeys.partyKey(partyId);
    }

    public String activeRequestKey(String userId) {
        return SharedKeys.activeRequestKey(userId);
    }

    public String gameConfigKey(CreateMatchRequestCommand command) {
        return gameConfigKey(command.getModeKey());
    }

    public String gameConfigKey(String modeKey) {
        return SharedKeys.gameConfigKey(GAME, modeKey);
    }

    public String tierRangeKey(CreateMatchRequestCommand command) {
        return SharedKeys.tierRangeKey(GAME, command.getModeKey());
    }

    public String tierKey() {
        return SharedKeys.tierKey(GAME);
    }
}
