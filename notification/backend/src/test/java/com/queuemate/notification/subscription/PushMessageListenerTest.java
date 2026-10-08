package com.queuemate.notification.subscription;

import com.queuemate.notification.redisKeys.PushChannels;
import com.queuemate.notification.sse.SseConnections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * {@link PushMessageListener} 가 채널에서 userId 를 꺼내 전송 풀로 <b>넘기기만</b> 하는지 본다.
 */
class PushMessageListenerTest {

    private SseConnections connections;
    private HoldingExecutor executor;
    private PushMessageListener listener;

    @BeforeEach
    void setUp() {
        connections = mock(SseConnections.class);
        executor = new HoldingExecutor();
        listener = new PushMessageListener(connections, executor);
    }

    @Test
    @DisplayName("채널 qm:pubsub:push:u1 로 온 본문이 connections.send(\"u1\", 본문) 으로 넘어간다")
    void 채널의_userId_와_본문을_그대로_넘긴다() {
        String json = "{\"type\":\"MATCH_CONFIRMED\",\"eventId\":\"e-1\",\"payload\":{}}";

        listener.onMessage(message(PushChannels.pushChannel("u1"), json), null);
        executor.runAll();

        verify(connections).send("u1", json);
        verifyNoMoreInteractions(connections);
    }

    @Test
    @DisplayName("한글이 들어간 UTF-8 본문도 깨지지 않는다")
    void 한글_본문도_깨지지_않는다() {
        String json = "{\"type\":\"MATCH_CONFIRMED\",\"eventId\":\"e-2\",\"payload\":{\"nickname\":\"큐메이트\",\"memo\":\"같이 해요 😀\"}}";

        listener.onMessage(message(PushChannels.pushChannel("u1"), json), null);
        executor.runAll();

        verify(connections).send("u1", json);
    }

    @Test
    @DisplayName("onMessage 를 호출한 스레드에서 send 가 실행되지 않는다 - 전송 풀로 넘기고 바로 리턴한다 (CLAUDE.md §5)")
    void 리스너_스레드에서_직접_보내지_않는다() {
        String json = "{\"type\":\"MATCH_QUEUE_UPDATED\",\"eventId\":\"e-3\",\"payload\":{}}";

        listener.onMessage(message(PushChannels.pushChannel("u1"), json), null);

        // onMessage 가 리턴한 시점: 일은 풀에 넘어갔을 뿐 아직 아무것도 보내지 않았다
        assertThat(executor.held).hasSize(1);
        verifyNoInteractions(connections);

        // 풀이 그 일을 실행해야 비로소 전송된다
        executor.runAll();
        verify(connections).send("u1", json);
    }

    @Test
    @DisplayName("접두사가 안 맞는 채널이면 아무것도 안 한다 (executor 에도 안 넘긴다)")
    void 접두사가_다른_채널은_무시한다() {
        listener.onMessage(message("qm:party:u1", "{}"), null);
        listener.onMessage(message("other:pubsub:push:u1", "{}"), null);

        assertThat(executor.held).isEmpty();
        verifyNoInteractions(connections);
    }

    @Test
    @DisplayName("Executor 가 RejectedExecutionException 을 던져도 onMessage 가 예외를 밖으로 안 올린다")
    void 전송_풀이_가득_차도_예외를_올리지_않는다() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("queue full");
        };
        PushMessageListener rejectingListener = new PushMessageListener(connections, rejecting);

        assertThatCode(() -> rejectingListener.onMessage(message(PushChannels.pushChannel("u1"), "{}"), null))
                .doesNotThrowAnyException();

        verify(connections, never()).send(any(), any());
    }

    private static Message message(String channel, String body) {
        return new DefaultMessage(channel.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
    }

    /** 받은 일을 실행하지 않고 모아 두는 가짜 풀. {@link #runAll()} 을 불러야 실행된다 */
    static class HoldingExecutor implements Executor {

        final List<Runnable> held = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            held.add(command);
        }

        void runAll() {
            List<Runnable> toRun = new ArrayList<>(held);
            held.clear();
            toRun.forEach(Runnable::run);
        }
    }
}
