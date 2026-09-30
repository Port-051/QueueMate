package com.queuemate.platform.room.notification;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * 테스트용 구독자. {@code notification} 이 하는 것처럼 알림 채널을 실제로 구독해서 무엇이 나갔는지 본다.
 * 2026-09-25 에 {@code room} 앱을 합치며 그쪽 테스트에서 옮겨 왔다 — 이 앱의 다른 테스트는 구독을 각자 짜 두었다({@code FriendPushTest} 등).
 *
 * <p>발행은 틀려도 조용하다 — 예외를 밖으로 내보내지 않고, 구독자 0명도 실패가 아니다. 그래서 발행 쪽 테스트는
 * "불렀다"가 아니라 <b>"구독자에게 이것이 도착했다"</b>로 확인해야 한다 (CLAUDE.md §3.2).
 */
public final class PushSubscriber implements AutoCloseable {

    /** 도착한 알림 하나. 어느 사용자의 채널로 왔는지(이름표로 — {@code RoomTestSupport#labelOf})와 봉투 */
    public record Received(String userId, JsonNode envelope) {
    }

    private static final String CHANNEL_PREFIX = "qm:pubsub:push:";

    /** 구독이 걸렸는지 확인하는 알림을 받는 가짜 사용자. 테스트가 쓰는 userId 와 겹치지 않는다 */
    private static final String PROBE_USER = "__subscriber-probe__";

    private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    private final BlockingQueue<Received> received = new LinkedBlockingQueue<>();

    /**
     * @param labeler 채널의 사용자 번호를 이름표로 바꾼다({@code this::labelOf}). 테스트가 {@code "host"} · {@code "u1"} 로 비교하게 하려는 것이다
     */
    public PushSubscriber(RedisConnectionFactory factory, ObjectMapper objectMapper, UnaryOperator<String> labeler)
            throws InterruptedException
    {
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            received.add(new Received(labeler.apply(channel.substring(CHANNEL_PREFIX.length())), objectMapper.readTree(body)));
        }, new PatternTopic(CHANNEL_PREFIX + "*"));
        container.afterPropertiesSet();
        container.start();
        awaitSubscribed(factory);
    }

    /**
     * 구독이 Redis 에 실제로 걸릴 때까지 기다린다. 걸리기 전에 발행된 알림은 사라지므로, 정해진 시간만 자고 시작하면
     * 기계가 바쁠 때 첫 알림을 놓쳐 테스트가 가끔 실패한다. 확인용 알림을 쏴서 그것이 돌아오는 것을 보고 시작한다.
     */
    private void awaitSubscribed(RedisConnectionFactory factory) throws InterruptedException
    {
        StringRedisTemplate probe = new StringRedisTemplate(factory);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline)
        {
            probe.convertAndSend(CHANNEL_PREFIX + PROBE_USER, "{\"probe\":true}");
            if (received.poll(100, TimeUnit.MILLISECONDS) != null)
            {
                // 그 사이 쏜 확인용 알림이 더 도착할 수 있다. 잠깐 기다렸다가 전부 비운다
                Thread.sleep(150);
                received.clear();
                return;
            }
        }
        throw new IllegalStateException("5초가 지나도록 알림 채널 구독이 걸리지 않았다");
    }

    /** 다음 알림을 기다린다. 2초 안에 안 오면 {@code null} */
    public Received next() throws InterruptedException
    {
        return received.poll(2, TimeUnit.SECONDS);
    }

    /** 잠깐 기다려 보고 더 온 것이 없는지 본다. "아무에게도 가지 않았다"를 확인할 때 쓴다 */
    public boolean nothingMore() throws InterruptedException
    {
        return received.poll(300, TimeUnit.MILLISECONDS) == null;
    }

    @Override
    public void close()
    {
        container.stop();
        try
        {
            container.destroy();
        }
        catch (Exception ignored)
        {
        }
    }
}
