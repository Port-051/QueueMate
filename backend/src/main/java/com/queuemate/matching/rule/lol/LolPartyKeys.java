package com.queuemate.matching.rule.lol;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
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

    /** 파티 HASH 키 접두사. Lua 가 기존 파티 키를 조립할 때 ARGV 로도 받아 간다 */
    public static final String PARTY_PREFIX = "qm:party:";

    /** needs 키 뒤에 티어를 붙일 때 쓰는 구분자 */
    private static final String TIER_SUFFIX_SEPARATOR = ":";

    /** 잠글 후보 풀. needs 키에서 keyValue 만 뺀 조합이어야 같은 색인을 잠근다 (PoolLock 참고). */
    public String poolKey(CreateMatchRequestCommand command) {
        return "qm:party:open:LOL:" + command.getModeKey() + ":"
                + command.getVoicePreference().name() + ":"
                + command.getPlayPurpose().name();
    }

    /** 그 keyValue를 아직 못 채운 파티 목록 */
    public String needsKey(CreateMatchRequestCommand command, String keyValue) {
        return poolKey(command) + ":needs:" + keyValue;
    }

    /** 취소 경로에서 쓰는 needs 키. 조립 규칙은 canJoin 쪽과 같아야 한다. */
    public String needsKey(ActiveRequest active, String keyValue) {
        return "qm:party:open:LOL:" + active.modeKey() + ":"
                + active.voicePreference().name() + ":"
                + active.playPurpose().name() + ":needs:" + keyValue;
    }

    /**
     * 티어 격자의 한 칸을 가리키는 needs 키.
     *
     * <p>배정({@link TieredAssigner})과 취소({@link PartyLeaver})가 <b>문자열까지 같은 키</b>를
     * 만들어야 한다. 취소는 배정이 색인에 올려 둔 파티를 그 키에서 빼거나 되돌리는데,
     * 접미사 규칙이 한쪽만 달라지면 컴파일은 통과하고 비운 자리를 아무도 못 찾게 된다.
     * 그래서 양쪽 다 인라인으로 붙이던 {@code ":" + tier} 를 여기 한 자리로 모았다.
     *
     * @param tierName 티어 사다리({@code qm:gameconfig:LOL:tier})에 있는 티어 이름
     */
    public String needsKey(CreateMatchRequestCommand command, String keyValue, String tierName) {
        return needsKey(command, keyValue) + TIER_SUFFIX_SEPARATOR + tierName;
    }

    /** 위와 같은 키를 취소 경로에서 만든다. */
    public String needsKey(ActiveRequest active, String keyValue, String tierName) {
        return needsKey(active, keyValue) + TIER_SUFFIX_SEPARATOR + tierName;
    }

    public String partyKey(String partyId) {
        return PARTY_PREFIX + partyId;
    }

    public String activeRequestKey(String userId) {
        return "qm:user:active-request:" + userId;
    }

    public String gameConfigKey(CreateMatchRequestCommand command) {
        return gameConfigKey(command.getModeKey());
    }

    public String gameConfigKey(String modeKey) {
        return "qm:gameconfig:LOL:" + modeKey;
    }

    public String tierRangeKey(CreateMatchRequestCommand command) {
        return "qm:gameconfig:LOL:tier-range:" + command.getModeKey();
    }

    public String  tierKey() { return "qm:gameconfig:LOL:tier"; }
}
