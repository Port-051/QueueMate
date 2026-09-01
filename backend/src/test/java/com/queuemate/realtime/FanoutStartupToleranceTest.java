package com.queuemate.realtime;

import com.queuemate.realtime.event.EventFanoutSubscription;
import com.queuemate.realtime.event.RealtimeEventPublisher;
import com.queuemate.realtime.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Redis가 죽어 있어도 애플리케이션이 뜨는지 본다.
 *
 * 노트 014에 노드 간 전달만 멈추고 같은 노드에는 계속 보낸다고 적었는데,
 * 구독 실패가 기동을 막으면 그 결정이 무의미해진다. 전부 잃는다.
 *
 * Redis 컨테이너를 띄우지 않는다. 닿을 수 없는 포트를 준다.
 */
@Testcontainers(disabledWithoutDocker = true)
// MOCK 환경에는 jakarta.websocket의 ServerContainer가 없어 WebSocketConfig가 뜨지 않는다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FanoutStartupToleranceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate").withUsername("queuemate").withPassword("queuemate");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        // 아무것도 듣고 있지 않은 포트다.
        registry.add("spring.data.redis.port", () -> 1);
    }

    @Autowired EventFanoutSubscription subscription;
    @Autowired RealtimeEventPublisher publisher;
    @Autowired SessionRegistry sessions;

    @Test
    void Redis가_없어도_애플리케이션은_뜬다() {
        // 여기까지 왔다는 것이 곧 컨텍스트가 떴다는 뜻이다.
        assertNotNull(publisher);
        assertNotNull(sessions);
    }

    @Test
    void 구독은_붙지_않은_채로_남고_다시_시도할_수_있다() {
        assertFalse(subscription.isSubscribed(), "닿을 수 없는데 붙었다고 하면 안 된다");

        // 실패한 뒤에도 다시 시도할 수 있어야 한다. 한 번 실패하고 끝나면
        // Redis가 살아나도 이 노드만 영원히 고립된다.
        subscription.ensureSubscribed();
        assertFalse(subscription.isSubscribed());
    }
}
