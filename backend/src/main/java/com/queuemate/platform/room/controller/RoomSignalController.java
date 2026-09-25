package com.queuemate.platform.room.controller;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.SignalResult;
import com.queuemate.platform.room.dto.SignalRequest;
import com.queuemate.platform.room.service.RoomSignalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms/{roomId}/")
@RequiredArgsConstructor
public class RoomSignalController {

    private final RoomSignalService roomSignalService;

    /**
     * WebRTC 시그널을 같은 방의 상대에게 보낸다. WebSocket 은 없다 — 보내는 길은 이 {@code POST}, 받는 길은 SSE 다 (docs/11 D-9).
     * 보낸 사람은 access 토큰의 사용자다 — 남의 이름으로 보낼 길이 없다.
     *
     * <p><b>202 다.</b> "받았고 상대의 채널에 발행했다"까지가 이 응답의 뜻이고 상대에게 도착했다는 뜻이 아니다.
     * 여러 {@code POST} 의 도착 순서도 보장하지 않는다. 결과마다의 상태 코드는 {@code contracts/platform-api.md} 가 원본이다.
     */
    @PostMapping("signals")
    public ResponseEntity<Void> send(@CurrentUserId Long userId, @PathVariable String roomId,
                                     @RequestBody @Valid SignalRequest request)
    {
        SignalResult result = roomSignalService.send(roomId, String.valueOf(userId), request.toUserId(), request.signal());

        return switch (result) {
            case SENT -> ResponseEntity.accepted().build();
            case NOT_IN_ROOM -> throw RoomErrors.notInRoom();
            // 상대가 그 사이에 나갔다. 클라이언트는 그 사람과의 연결을 정리한다
            case TARGET_NOT_IN_ROOM -> throw new ApiException(HttpStatus.NOT_FOUND, RoomErrors.TARGET_NOT_IN_ROOM,
                    "받는 사람이 이 파티방에 없습니다");
        };
    }
}
