package com.queuemate.chat.config;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 방 채팅 전송 줄(stripe) 묶음 — 줄마다 스레드 하나 · 유한한 큐. 방은 {@code roomId} 의 해시로 늘 같은 줄에 간다.
 *
 * <ul>
 *   <li>같은 방의 메시지는 같은 줄 → <b>순서가 지켜진다</b>(한 줄은 FIFO · 스레드 하나).</li>
 *   <li>같은 방의 소켓에 쓰는 스레드가 하나라 {@code WebSocketSession.sendMessage} 의 동시 호출이 없다(그 메서드는 동시 호출에 안전하지 않다).
 *       뒤에 ping 처럼 다른 스레드가 같은 소켓에 쓰게 되면 그때 {@code synchronized(session)} 또는 {@code ConcurrentWebSocketSessionDecorator} 를 더한다.</li>
 *   <li>느린 브라우저가 막는 범위는 그 줄에 걸린 방들이다 — 리스너 스레드와 다른 줄은 영향이 없다.</li>
 * </ul>
 *
 * 거부 정책은 기본값(AbortPolicy — 예외)이다. {@code CallerRunsPolicy} 로 바꾸지 마라 — 넘치는 순간 Redis 리스너 스레드가 직접 보내게 되어
 * 느린 브라우저 하나가 모든 방을 막는다(notification 과 같은 이유). 넘친 메시지는 호출한 쪽이 예외를 잡아 버린다.
 */
public class RoomSendExecutors {

    private final ThreadPoolTaskExecutor[] stripes;

    public RoomSendExecutors(int stripeCount, int queueCapacity) {
        if (stripeCount < 1) {
            throw new IllegalArgumentException("stripes 는 1 이상이어야 한다: " + stripeCount);
        }
        this.stripes = new ThreadPoolTaskExecutor[stripeCount];
        for (int i = 0; i < stripeCount; i++) {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setThreadNamePrefix("chat-send-" + i + "-");
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);          // 줄 하나 = 스레드 하나. 늘리면 순서가 깨진다
            executor.setQueueCapacity(queueCapacity);
            executor.initialize();
            stripes[i] = executor;
        }
    }

    /** 이 방의 줄. 같은 roomId 는 늘 같은 줄이다. */
    public Executor forRoom(String roomId) {
        int index = Math.floorMod(roomId.hashCode(), stripes.length);
        return stripes[index];
    }

    public int stripeCount() {
        return stripes.length;
    }
}
