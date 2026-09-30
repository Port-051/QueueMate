package com.queuemate.platform.room.notification;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 테스트용 게시판 채널 구독자. {@code notification} 이 하는 것처럼 {@code qm:pubsub:board} 를 실제로 구독한다.
 * 2026-09-25 에 {@code room} 앱을 합치며 그쪽 테스트에서 옮겨 왔다({@code party.BoardSignalTest} 는 구독을 따로 짜 두었다).
 *
 * <p>채널 이름을 {@code BoardChannels} 에서 가져오지 않고 <b>여기에 다시 적는다.</b> 상수에 오타가 나면 발행하는 쪽과
 * 같이 틀려서 테스트가 통과해 버린다 — 이 이름은 {@code notification} 과의 약속이다 (docs/11 D-22).
 */
public final class BoardSubscriber implements AutoCloseable {

    private static final String BOARD_CHANNEL = "qm:pubsub:board";

    /** 구독이 걸렸는지 확인하려고 쏘는 가짜 신호. 테스트가 기다리는 봉투와 섞이지 않게 표시해 둔다 */
    private static final String PROBE = "{\"probe\":true}";

    private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    private final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();

    public BoardSubscriber(RedisConnectionFactory factory, ObjectMapper objectMapper) throws InterruptedException
    {
        container.setConnectionFactory(factory);
        container.addMessageListener((message, pattern) ->
                received.add(objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8))),
                new ChannelTopic(BOARD_CHANNEL));
        container.afterPropertiesSet();
        container.start();
        awaitSubscribed(factory);
    }

    /** 구독이 Redis 에 실제로 걸릴 때까지 기다린다. 이유는 {@link PushSubscriber} 와 같다 */
    private void awaitSubscribed(RedisConnectionFactory factory) throws InterruptedException
    {
        StringRedisTemplate probe = new StringRedisTemplate(factory);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline)
        {
            probe.convertAndSend(BOARD_CHANNEL, PROBE);
            if (received.poll(100, TimeUnit.MILLISECONDS) != null)
            {
                drain();
                return;
            }
        }
        throw new IllegalStateException("5초가 지나도록 게시판 채널 구독이 걸리지 않았다");
    }

    /** 지금까지 온 것을 버린다. 준비 단계(방 만들기 · 입장)가 낸 신호를 치우고 보려는 동작만 볼 때 쓴다 */
    public void drain() throws InterruptedException
    {
        Thread.sleep(200);
        received.clear();
    }

    /** 다음 신호를 기다린다. 2초 안에 안 오면 {@code null} */
    public JsonNode next() throws InterruptedException
    {
        return received.poll(2, TimeUnit.SECONDS);
    }

    /** 잠깐 기다려 보고 더 온 것이 없는지 본다 */
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
