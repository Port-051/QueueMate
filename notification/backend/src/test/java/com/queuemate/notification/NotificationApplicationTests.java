package com.queuemate.notification;

import org.junit.jupiter.api.Test;
import com.queuemate.notification.security.TestTokens;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class NotificationApplicationTests {

	@DynamicPropertySource
	static void jwt(DynamicPropertyRegistry registry) {
		TestTokens.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
