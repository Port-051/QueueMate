package com.queuemate.realtime.signal;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.party.service.PartyService;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WebRTC signaling을 상대에게 넘긴다. 서버는 중개만 하고 내용은 보지 않는다 (docs/13).
 *
 * 검증 실패를 클라이언트에게 알리지 않는다. 실패 이유를 돌려주면 targetUserId를 바꿔가며
 * 어떤 파티가 존재하는지, 누가 그 파티에 있는지 알아낼 수 있다.
 */
@Service
public class SignalRelayService {

    private static final Logger log = LoggerFactory.getLogger(SignalRelayService.class);

    private final PartyService parties;
    private final RealtimeEventPublisher events;

    public SignalRelayService(PartyService parties, RealtimeEventPublisher events) {
        this.parties = parties;
        this.events = events;
    }

    /**
     * @param senderId handshake에서 인증된 주체. 클라이언트가 보낸 값이 아니다.
     * @return 전달했으면 true. 거절은 조용히 false다.
     */
    public boolean relay(UUID senderId, ClientMessage message) {
        if (!message.isWebRtcSignal()) {
            // client → server는 signaling만 허용된다. 나머지는 REST로 온다.
            log.debug("허용되지 않은 client 메시지 type={}", message.type());
            return false;
        }
        if (!message.hasRequiredFields()) {
            log.debug("형식이 맞지 않는 signal signalType={}", message.signalType());
            return false;
        }
        if (!parties.bothActiveMembers(message.partyId(), senderId, message.targetUserId())) {
            // 보낸 사람과 받을 사람이 모두 그 파티에 있어야 한다. 자기 자신도 거절된다.
            log.warn("파티 밖 signal 시도 partyId={} target={}",
                    message.partyId(), message.targetUserId());
            return false;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("partyId", message.partyId());
        // 보낸 사람은 세션의 주체로 채운다. 클라이언트가 준 값을 쓰면 신원을 위조할 수 있다.
        payload.put("fromUserId", senderId);
        payload.put("signalType", message.signalType());
        payload.put("data", message.data());

        MDC.put(MdcKeys.PARTY_ID, message.partyId().toString());
        events.publish(List.of(message.targetUserId()),
                ServerEvent.of(EventType.WEBRTC_SIGNAL, payload));
        MDC.remove(MdcKeys.PARTY_ID);
        return true;
    }
}
