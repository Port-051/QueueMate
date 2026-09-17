package com.queuemate.matching.domain;

import com.queuemate.matching.domain.condition.PlayPurpose;
import com.queuemate.matching.domain.condition.VoicePreference;

/**
 * qm:user:active-request:{userId} 에서 읽어온 활성 요청.
 *
 * 요청 접수는 DTO(CreateMatchRequestCommand)로 들어오지만,
 * 취소는 들어오는 값이 아니라 저장된 값을 근거로 움직인다.
 * 그래서 Redis에서 읽은 것을 담을 타입이 따로 필요하다.
 */
public record ActiveRequest(
        String requestId,
        String userId,
        GameKey game,
        String modeKey,
        VoicePreference voicePreference,
        PlayPurpose playPurpose,
        String keyValue,
        String partyId,
        String tier) {
    /** 아직 파티에 배정되기 전일 수 있다. */
    public boolean hasParty() {
        return partyId != null && !partyId.isBlank();
    }
}
