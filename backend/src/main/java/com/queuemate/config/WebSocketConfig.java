package com.queuemate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

@Configuration
@EnableWebSocket
public class WebSocketConfig {
    // Member 3 owns authenticated signaling handler registration.
}
