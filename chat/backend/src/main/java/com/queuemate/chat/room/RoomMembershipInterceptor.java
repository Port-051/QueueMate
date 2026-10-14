package com.queuemate.chat.room;

import com.queuemate.chat.config.ChatProperties;
import com.queuemate.chat.redisKeys.SharedRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class RoomMembershipInterceptor implements HandshakeInterceptor
{

    /** 세션 attributes 의 열쇠 — 여기서 넣고 핸들러가 꺼낸다. 글자를 두 곳에 따로 적지 않는다 */
    public static final String ROOM_ID = "roomId";
    public static final String USER_ID = "userId";
    public static final String SEND_BUDGET = "sendBudget";

    private final StringRedisTemplate redis;
    private final ChatProperties properties;   // queuemate.chat.max-messages-per-second

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String path = request.getURI().getPath();
        // roomId 는 문자열이다 — 게시판 방은 글 번호("123"), 빠른매치 방은 파티 UUID("3f2a…")라 숫자로 바꾸지 않는다(platform P-30 · 2026-10-10).
        // userId 도 같은 이유로 문자열로 들고 간다 — JWT sub 가 곧 사용자 번호의 십진 문자열이고 Redis 필드도 그 글자다
        String roomId = path.substring(path.lastIndexOf('/') + 1);
        String userId = request.getPrincipal().getName();
        Boolean exist = redis.opsForHash().hasKey(SharedRedisKeys.roomMemberKey(roomId), userId);
        if(!Boolean.TRUE.equals(exist))
        {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            log.info("방 멤버 아님 roomId={} userId={}", roomId, userId);
            return false;
        }
        attributes.put(ROOM_ID, roomId);
        attributes.put(USER_ID, userId);
        int perSecond = properties.maxMessagesPerSecond();
        attributes.put(SEND_BUDGET, new SendBudget(perSecond, perSecond));   // 소켓마다 통 하나 — 초당 N 개(yaml)
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, @Nullable Exception exception) {

    }
}
