package com.queuemate.party.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** contracts/openapi.yaml의 PartyView와 필드 이름을 맞춘다. */
public final class PartyDtos {

    private PartyDtos() {
    }

    public record PartyView(
            UUID id,
            String game,
            String modeKey,
            int targetSize,
            String status,
            List<MemberView> members
    ) {
    }

    public record MemberView(UUID userId, String nickname, boolean ready) {
    }

    public record ReadyRequest(@NotNull Boolean ready) {
    }
}
