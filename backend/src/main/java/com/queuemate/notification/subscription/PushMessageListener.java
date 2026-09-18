package com.queuemate.notification.subscription;

import com.queuemate.notification.config.AsyncConfig;
import com.queuemate.notification.sse.SseConnections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Redis 에서 받은 메시지를 해당 사용자의 SSE 연결로 넘긴다.
 *
 * <p>모든 사용자 채널이 이 리스너 <b>하나</b>를 공유한다. 채널 이름
 * ({@code qm:pubsub:push:{userId}})에서 userId 를 꺼내 어느 연결로 보낼지 정한다.
 *
 * <p>본문은 열어 보지 않는다. {@code type} 별 분기·필터링을 하지 않고, 모르는
 * {@code type} 도 그대로 보낸다 (CLAUDE.md §2).
 *
 * <p><b>이 메서드는 Redis 리스너 스레드에서 불린다.</b> 여기서 직접 보내지 않고 전송 풀로
 * 넘긴 뒤 즉시 리턴한다. 느린 클라이언트 하나가 이 스레드를 막으면 모든 사용자의 알림이
 * 멈춘다 (CLAUDE.md §5).
 */
@Slf4j
@Component
public class PushMessageListener implements MessageListener {

    private final SseConnections connections;
    private final Executor pushExecutor;

    public PushMessageListener(SseConnections connections,
                               @Qualifier(AsyncConfig.SSE_PUSH_EXECUTOR) Executor pushExecutor) {
        this.connections = connections;
        this.pushExecutor = pushExecutor;
    }

    /**
     * @param pattern 패턴 구독일 때만 값이 있다. 이 서비스는 채널 구독만 쓰므로 항상 {@code null} 이다
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        // TODO: 채널명(message.getChannel()) → PushChannels.userIdOf(), 본문(message.getBody()) → UTF-8 문자열로 바꾼 뒤
        //       pushExecutor.execute(() -> connections.send(userId, json)) 으로 넘기고 즉시 리턴한다
    }
}
