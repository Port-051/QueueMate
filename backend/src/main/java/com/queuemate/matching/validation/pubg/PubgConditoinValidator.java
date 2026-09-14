package com.queuemate.matching.validation.pubg;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.validation.GameConditionValidator;

public class PubgConditoinValidator implements GameConditionValidator {

    @Override
    public boolean supports(GameKey game) {

        return game.equals(GameKey.PUBG);
    }

    @Override
    public boolean validate(CreateMatchRequestCommand command) {





        return true;
    }
}
