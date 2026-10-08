package com.queuemate.platform.party.domain;

import java.util.Optional;

/** 음성 채팅을 쓰는가. 값의 이름은 {@code matching} 의 {@code VoicePreference} 와 같다 ({@code contracts/platform-api.md} "글 한 줄") */
public enum VoicePreference {

    REQUIRED,
    NO_VOICE;

    public static Optional<VoicePreference> fromName(String name)
    {
        for(VoicePreference value : values())
        {
            if(value.name().equals(name))
            {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
