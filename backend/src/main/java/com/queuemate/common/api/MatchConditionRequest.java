package com.queuemate.common.api;

import com.queuemate.common.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MatchConditionRequest(
        @NotNull GameKey game,
        @NotBlank String modeKey,
        @Valid @NotNull KeyConditionRequest keyCondition,
        @NotNull VoicePreference voicePreference,
        @NotNull PlayPurpose playPurpose
) {
    public record KeyConditionRequest(
            @NotBlank String type,
            @NotBlank String value
    ) {}
}
