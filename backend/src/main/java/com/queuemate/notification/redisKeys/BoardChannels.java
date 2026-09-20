package com.queuemate.notification.redisKeys;

import com.queuemate.notification.domain.GameKey;

public class BoardChannels {

    private static final String prefix = "qm:pubsub:board:";

    public static String boardChannel(GameKey gameKey) {
        return prefix + gameKey.name();
    }
}
