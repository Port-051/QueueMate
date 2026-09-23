package com.queuemate.platform.party.domain;

import java.util.Optional;

/** 무엇을 하려고 모이는가. 값의 이름은 {@code matching} 의 {@code PlayPurpose} 와 같다 ({@code contracts/platform-api.md} "글 한 줄") */
public enum PlayPurpose {

    RANK_UP,
    NORMAL,
    FUN;

    public static Optional<PlayPurpose> fromName(String name)
    {
        for(PlayPurpose value : values())
        {
            if(value.name().equals(name))
            {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
