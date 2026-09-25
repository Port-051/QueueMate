package com.queuemate.platform.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * 시그널 보내기의 요청 본문.
 *
 * @param toUserId 받는 사람
 * @param signal   WebRTC 시그널. <b>모양은 클라이언트끼리의 약속이고 서버는 열어 보지 않는다</b> — offer · answer · ICE 후보의 구분,
 *                 SDP, 재협상을 구분할 식별자가 전부 이 안에 들어간다. 그래서 타입이 특정 record 가 아니라 {@link JsonNode} 다.
 *                 받은 그대로 상대에게 실어 보낸다
 */
public record SignalRequest(
        @NotBlank String toUserId,
        @NotNull JsonNode signal) {
}
