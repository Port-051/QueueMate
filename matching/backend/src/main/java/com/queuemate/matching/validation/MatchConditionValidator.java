package com.queuemate.matching.validation;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 요청의 game에 맞는 GameConditionValidator를 골라 검증을 위임한다.
 */
@Service
@RequiredArgsConstructor
public class MatchConditionValidator
{
    private final List<GameConditionValidator> validators;

    public boolean validate(CreateMatchRequestCommand command)
    {
        return validators.stream()
                .filter(v -> v.supports(command.getGame()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 게임: " + command.getGame()))
                .validate(command);
    }
}
