package com.queuemate.chat.config;

import com.queuemate.chat.room.RoomChatHandler;
import com.queuemate.chat.room.RoomMembershipInterceptor;
import com.queuemate.chat.web.WebSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RoomChatHandler roomChatHandler;
    private final RoomMembershipInterceptor roomMembershipInterceptor;
    private final WebSecurityProperties web;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(roomChatHandler, "/ws/chat/rooms/{roomId}")
                .addInterceptors(roomMembershipInterceptor)
                .setAllowedOrigins(web.allowedOrigins().toArray(String[]::new));
    }
}
