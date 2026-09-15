package com.queuemate.realtime.signal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * client → server로 오는 유일한 메시지. contracts/events.md의 Client → Server와 같다.
 *
 * data는 해석하지 않고 JsonNode로 들고만 있는다. SDP와 ICE candidate는 브라우저끼리
 * 주고받는 내용이라 서버가 읽을 이유가 없다. 파싱하지 않으면 그만큼 공격면도 줄어든다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientMessage(
        String type,
        UUID partyId,
        UUID targetUserId,
        String signalType,
        JsonNode data
) {

    public static final String WEBRTC_SIGNAL = "WEBRTC_SIGNAL";

    public boolean isWebRtcSignal() {
        return WEBRTC_SIGNAL.equals(type);
    }

    /** 계약이 정한 세 가지 외에는 받지 않는다. */
    public boolean hasValidSignalType() {
        return "OFFER".equals(signalType) || "ANSWER".equals(signalType) || "ICE".equals(signalType);
    }

    public boolean hasRequiredFields() {
        return partyId != null && targetUserId != null && hasValidSignalType();
    }
}
