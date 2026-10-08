package com.queuemate.notification.subscription;

import com.queuemate.notification.config.AsyncConfig;
import com.queuemate.notification.redisKeys.PushChannels;
import com.queuemate.notification.sse.SseConnections;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class PushBoardListener implements MessageListener
{
    private final SseConnections connections;
    private final Executor pushExecutor;

    public PushBoardListener(SseConnections connections,
                               @Qualifier(AsyncConfig.SSE_PUSH_EXECUTOR) Executor pushExecutor) {
        this.connections = connections;
        this.pushExecutor = pushExecutor;
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern)
    {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            pushExecutor.execute(() -> connections.sendAll(json));
        } catch (RejectedExecutionException e) {
            // 알림은 휘발성이다. 넘친 것은 버리고 클라이언트가 상태 조회로 복구한다
            log.warn("전송 풀이 가득 차 알림을 버린다 channel={}", channel);
        }
    }
}
