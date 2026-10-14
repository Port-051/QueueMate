package com.queuemate.chat;

import com.queuemate.chat.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class ChatApplicationTests {

	// 공개 키가 없으면 컨텍스트가 뜨지 않는다 — 모든 @SpringBootTest 에 이 둘을 붙인다 (CLAUDE.md §5)
	@DynamicPropertySource
	static void jwt(DynamicPropertyRegistry registry) {
		TestTokens.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
