package com.queuemate.chat.room;


import com.queuemate.chat.config.ChatProperties;
import com.queuemate.chat.domain.Type;
import com.queuemate.chat.dto.Input;
import com.queuemate.chat.dto.Output;
import com.queuemate.chat.dto.Reply;
import com.queuemate.chat.redisKeys.SharedRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;


@Slf4j
@Component
@RequiredArgsConstructor
public class RoomChatHandler extends TextWebSocketHandler{

    private static final int MAX_NICK_LENGTH = 16;    // platform 의 닉네임 규칙(2~16자)과 같은 상한

    private final RoomSockets roomSockets;
    private final RoomChannelSubscriber roomChannelSubscriber;
    private final StringRedisTemplate redis;
    private final ChatProperties properties;          // queuemate.chat.* — 500자 등
    // Spring 이 만든 ObjectMapper 를 쓴다 — java.time(Instant → ISO 문자열) 등이 설정돼 있다. new ObjectMapper() 는 sentAt 직렬화가 다를 수 있다
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("연결 sessionId={} attrs={}", session.getId(), session.getAttributes());
        String roomId = (String) session.getAttributes().get(RoomMembershipInterceptor.ROOM_ID);
        boolean first = roomSockets.add(roomId, session);
        if(first){
            roomChannelSubscriber.subscribe(roomId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        // 본문은 DEBUG 로만 — 운영 INFO 로그에 채팅 내용이 남지 않게(D-58 "저장하지 않는다" · 2026-10-13 소유자 결정)
        log.debug("받음 sessionId={} payload={}", session.getId(), textMessage.getPayload());
        String roomId = (String) session.getAttributes().get(RoomMembershipInterceptor.ROOM_ID);
        String userId = (String) session.getAttributes().get(RoomMembershipInterceptor.USER_ID);

        // 1) JSON 파싱 — 깨졌으면 cid 도 모른다
        String payload = textMessage.getPayload().trim();
        if (payload.isEmpty()) {
            return;                                                     // 빈 프레임 — 답할 cid 도 없어 조용히 버린다
        }
        Input input;
        try {
            input = objectMapper.readValue(payload, Input.class);
        } catch (Exception e) {
            reply(session, Reply.error(null, "BAD_JSON"));
            return;
        }
        if (input.type() != Type.SEND) {
            log.debug("모르는 type 은 버린다 sessionId={} type={}", session.getId(), input.type());
            return;
        }

        // 2) 초당 5개 — 토큰 버킷(SendBudget). 통이 비면 그 메시지만 거절하고 소켓은 끊지 않는다
        SendBudget budget = (SendBudget) session.getAttributes().get(RoomMembershipInterceptor.SEND_BUDGET);
        if (!budget.tryTake()) {
            reply(session, Reply.error(input.cid(), "TOO_FAST"));
            return;
        }
        // 3) 본문 검사
        String text = input.text() == null ? "" : input.text().trim();
        if (text.isEmpty()) {
            reply(session, Reply.error(input.cid(), "EMPTY"));
            return;
        }
        if (text.length() > properties.maxTextLength()) {
            reply(session, Reply.error(input.cid(), "TOO_LONG"));
            return;
        }
        String nick = input.nick() == null || input.nick().isBlank() ? null
                : input.nick().strip().substring(0, Math.min(input.nick().strip().length(), MAX_NICK_LENGTH));

        // 4) 봉투 조립 — from 은 세션의 userId(브라우저 값이 아니다) · 발행 — 자기 소켓에도 직접 쓰지 않는다(C-2 · 리스너가 쏜다)
        Output out = new Output(Type.MSG, roomId, userId, nick, text, input.cid(), Instant.now());
        try {
            redis.convertAndSend(SharedRedisKeys.roomChatKey(roomId), objectMapper.writeValueAsString(out));
        } catch (Exception e) {                                         // Redis 장애 조치 중 등 — 서버가 쌓아 두지 않고 보낸 사람에게 돌려준다(D-58)
            log.warn("발행 실패 roomId={} sessionId={} {}: {}", roomId, session.getId(), e.getClass().getSimpleName(), e.getMessage());
            reply(session, Reply.error(input.cid(), "RETRY"));
            return;
        }

        // 5) 영수증 — 보낸 소켓에만
        reply(session, Reply.ack(input.cid()));
    }

    /**
     * 보낸 소켓에만 쓰는 ACK · ERROR. 받은 {@code session} 에 직접 쓰지 않고 장부의 <b>포장된</b> 소켓으로 쓴다 — 전송 줄 스레드와 같은 소켓에 동시에 쓰는 것을
     * 포장(ConcurrentWebSocketSessionDecorator)이 막아 준다. 장부에 없으면(이미 끊김) 조용히 버린다.
     */
    private void reply(WebSocketSession session, Reply reply) {
        String roomId = (String) session.getAttributes().get(RoomMembershipInterceptor.ROOM_ID);
        roomSockets.sendTo(roomId, session.getId(), objectMapper.writeValueAsString(reply));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("끊김 sessionId={} status={}", session.getId(), status);
        String roomId = (String) session.getAttributes().get(RoomMembershipInterceptor.ROOM_ID);
        if(roomSockets.remove(roomId, session))
        {
            roomChannelSubscriber.unsubscribe(roomId);
        }
    }
}
