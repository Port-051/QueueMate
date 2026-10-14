package com.queuemate.chat.room;

import com.queuemate.chat.redisKeys.SharedRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RoomChannelSubscriber {

    private final RedisMessageListenerContainer container;
    private final RoomChatListener roomChatListener;

    public void subscribe(String roomId) {
        container.addMessageListener(roomChatListener, Topic.channel(SharedRedisKeys.roomChatKey(roomId)));
    }

    public void unsubscribe(String roomId) {
        // 채널을 꼭 준다 — 한 인자짜리 removeMessageListener(listener) 는 이 리스너를 "모든 채널" 에서 떼어 다른 방의 구독까지 끊는다(2026-10-10)
        container.removeMessageListener(roomChatListener, Topic.channel(SharedRedisKeys.roomChatKey(roomId)));
    }

}
