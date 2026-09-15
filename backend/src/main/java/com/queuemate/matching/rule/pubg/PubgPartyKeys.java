package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import jakarta.validation.constraints.NotBlank;
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

    public String needsKey(CreateMatchRequestCommand command) {
        return poolKey(command) + ":needs:" + command.getKeyCondition().getValue();
    }
}
