package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import org.springframework.stereotype.Component;

@Component
public class PubgPartyKeys {

    public static final String PARTY_PREFIX = "qm:party:";


    public String gameConfigKey(CreateMatchRequestCommand command) {
        return gameConfigKey(command.getModeKey());
    }

    public String gameConfigKey(String modeKey) {
        return "qm:gameconfig:PUBG:" + modeKey;
    }

    public String poolKey(CreateMatchRequestCommand command) {
        return "qm:party:open:PUBG:" + command.getModeKey() + ":"
                + command.getVoicePreference().name() + ":"
                + command.getPlayPurpose().name();
    }

    public String partyKey(String partyId) {
        return PARTY_PREFIX + partyId;
    }

    public String activeRequestKey(String userId) {
        return "qm:user:active-request:" + userId;
    }

    /** 모드별 티어 범위 표. HASH, 필드 = 티어 이름, 값 = "최저:최고" 또는 SOLO_ONLY. */
    public String tierRangeKey(CreateMatchRequestCommand command) {
        return "qm:gameconfig:PUBG:tier-range:" + command.getModeKey();
    }

    /** 티어 사다리. ZSET, score = 단계 번호. */
    public String tierKey() {
        return "qm:gameconfig:PUBG:tier";
    }

    /**
     * 티어 접미사가 없는 needs 키. 티어 모드에서는 Lua 가 {@code ':' .. 티어이름} 을 붙여 칸을 만든다.
     */
    public String needsKey(CreateMatchRequestCommand command) {
        return poolKey(command) + ":needs:" + command.getKeyCondition().getValue();
    }

    /**
     * 취소용 needs 키. 요청 DTO 가 아니라 Redis 에 저장된 활성 요청에서 만든다.
     * 배정 쪽 {@link #needsKey(CreateMatchRequestCommand)} 와 같은 문자열이어야 한다 —
     * 배정이 올려 둔 바로 그 키에서 빼고 되돌리기 때문이다.
     */
    public String needsKey(ActiveRequest active) {
        return "qm:party:open:PUBG:" + active.modeKey() + ":"
                + active.voicePreference().name() + ":"
                + active.playPurpose().name()
                + ":needs:" + active.keyValue();
    }

    /** 티어 한 칸의 needs 키. 자바에서 칸 크기를 셀 때만 쓴다 — 스크립트에는 접미사 없는 키를 넘긴다. */
    public String needsKey(CreateMatchRequestCommand command, String tier) {
        return needsKey(command) + ":" + tier;
    }
}
