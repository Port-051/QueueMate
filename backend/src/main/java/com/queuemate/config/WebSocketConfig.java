package com.queuemate.config;

import com.queuemate.realtime.ws.QueueMateHandshakeHandler;
import com.queuemate.realtime.ws.QueueMateWebSocketHandler;
import com.queuemate.realtime.ws.WebSocketAuthInterceptor;
import com.queuemate.realtime.presence.PresenceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(PresenceProperties.class)
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

    /**
     * 프레임 상한. Tomcat 기본값은 8KB인데 codec이 많은 SDP는 그걸 넘길 수 있다.
     * 상한을 아예 없애면 한 클라이언트가 큰 프레임으로 힙을 밀어버릴 수 있으므로 값을 정해 둔다.
     *
     * 이 빈은 실제 서블릿 컨테이너를 요구한다. 웹 서버를 띄우지 않는 @SpringBootTest는
     * ServerContainer가 없어 컨텍스트 로딩이 실패하므로, 통합 테스트는 실제 서버로 띄운다.
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
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
