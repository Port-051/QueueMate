package com.queuemate.realtime.signal;

import com.queuemate.common.logging.MdcKeys;
import com.queuemate.common.ratelimit.RateLimiter;
import com.queuemate.party.service.PartyService;
import com.queuemate.realtime.event.EventType;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.event.ServerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Duration;
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

    private static final String RATE_SCOPE = "signal";
    /**
     * 정상 사용의 최대치를 재서 잡은 값이다.
     *
     * 5인 파티에서 한 사람이 보내는 signal은 상대 4명 × (OFFER 또는 ANSWER 1개 +
     * ICE 후보 약 15개)로 60여 개이고, 파티가 만들어진 직후 몇 초에 몰린다.
     * 네트워크가 바뀌어 재협상이 일어나면 그만큼 더 든다.
     *
     * 창을 좁게 잡으면 이 정상 폭주를 막는다. 창을 10초로 넓게 두고 그 안에서 몰아 쓰게 하되,
     * 지속적으로 쏟아붓는 것은 막는다. 300개면 정상 최대의 네 배 남짓이다.
     */
    private static final int RATE_LIMIT = 300;
    private static final Duration RATE_WINDOW = Duration.ofSeconds(10);

    private final PartyService parties;
    private final RealtimeEventPublisher events;
    private final RateLimiter rateLimiter;

    public SignalRelayService(PartyService parties, RealtimeEventPublisher events,
                              RateLimiter rateLimiter) {
        this.parties = parties;
        this.events = events;
        this.rateLimiter = rateLimiter;
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
        if (!rateLimiter.tryAcquire(RATE_SCOPE, senderId.toString(), RATE_LIMIT, RATE_WINDOW)) {
            // 파티 소속 확인보다 먼저 본다. 소속 확인은 DB를 읽으므로,
            // 쏟아붓는 요청에 DB 조회가 딸려가면 막으려던 것을 그대로 겪는다.
            log.warn("signal rate limit 초과");
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
