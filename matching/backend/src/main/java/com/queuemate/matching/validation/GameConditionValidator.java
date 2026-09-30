package com.queuemate.matching.validation;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.dto.CreateMatchRequestCommand;

/**
 * 게임별 조건 값 검증.
 * modeKey와 keyCondition.value는 String이라 Bean Validation 어노테이션으로 잡을 수 없어
 * 게임마다 별도로 확인한다.
 */
public interface GameConditionValidator {

    /** CandidateRule.supports와 같은 시그니처로 맞춰 둔다. 라우팅 기준은 게임 하나뿐이다. */
    boolean supports(GameKey game);

    boolean validate(CreateMatchRequestCommand command);
}
