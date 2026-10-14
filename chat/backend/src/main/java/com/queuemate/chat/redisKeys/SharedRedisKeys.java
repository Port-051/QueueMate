package com.queuemate.chat.redisKeys;

public final class SharedRedisKeys {

    public static String roomMemberKey(String roomId)
    {
        return "qm:room:" + roomId + ":members";
    }
    public static String roomChatKey(String roomId) { return "qm:pubsub:chat:" + roomId; }
}
