package com.queuemate.config;

import com.queuemate.realtime.ws.QueueMateHandshakeHandler;
import com.queuemate.realtime.ws.QueueMateWebSocketHandler;
import com.queuemate.realtime.ws.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QueueMateWebSocketHandler handler;
    private final WebSocketAuthInterceptor authInterceptor;
    private final QueueMateHandshakeHandler handshakeHandler;

    public WebSocketConfig(QueueMateWebSocketHandler handler,
                           WebSocketAuthInterceptor authInterceptor,
                           QueueMateHandshakeHandler handshakeHandler) {
        this.handler = handler;
        this.authInterceptor = authInterceptor;
        this.handshakeHandler = handshakeHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(authInterceptor)
                // origin 검증은 배포 환경에서 설정한다. 기본값(same origin)은 개발에서 막힌다.
                .setAllowedOriginPatterns("*");
    }
}
