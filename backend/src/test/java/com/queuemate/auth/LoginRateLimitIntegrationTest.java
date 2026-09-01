package com.queuemate.auth;

import com.queuemate.auth.api.AuthDtos.LoginRequest;
import com.queuemate.auth.api.AuthDtos.SignupRequest;
import com.queuemate.common.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 비밀번호 대입 방어. signaling과 달리 Redis 장애 시 통과시키지 않는다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginRateLimitIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate").withUsername("queuemate").withPassword("queuemate");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final String PASSWORD = "password12";
    private static final Duration WINDOW = Duration.ofMinutes(10);

    @Autowired TestRestTemplate http;
    @Autowired RateLimiter rateLimiter;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE users CASCADE").update();
        // 테스트가 모두 같은 IP에서 온다. 앞 테스트의 시도가 남으면 뒤 테스트가 엉뚱하게 막힌다.
        rateLimiter.reset("login:ip", "127.0.0.1", WINDOW);
    }

    @Test
    void 비밀번호를_계속_틀리면_막힌다() {
        String email = signup("brute");
        // 한도가 5회다. 사람이 오타를 내는 횟수를 충분히 넘는다.
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, login(email, "wrong-password").getStatusCode());
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                login(email, "wrong-password").getStatusCode());
    }

    @Test
    void 막힌_뒤에는_올바른_비밀번호도_거절된다() {
        String email = signup("locked");
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password");
        }

        // 여기서 통과시키면 공격자가 맞는 비밀번호를 찾았을 때 그대로 들어온다.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, login(email, PASSWORD).getStatusCode());
    }

    @Test
    void 성공하면_그동안의_시도가_지워진다() {
        String email = signup("recover");
        for (int i = 0; i < 4; i++) {
            login(email, "wrong-password");
        }

        assertEquals(HttpStatus.OK, login(email, PASSWORD).getStatusCode());

        // 오타 몇 번 뒤에 성공한 사용자가 다음 로그인에서 벌을 받으면 안 된다.
        for (int i = 0; i < 4; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, login(email, "wrong-password").getStatusCode());
        }
    }

    @Test
    void 다른_계정은_따로_센다() {
        String first = signup("alpha");
        String second = signup("bravo");
        for (int i = 0; i < 5; i++) {
            login(first, "wrong-password");
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, login(first, "wrong-password").getStatusCode());

        // 계정별 한도가 서로를 깎으면 한 계정 공격이 다른 사용자를 막는다.
        assertEquals(HttpStatus.OK, login(second, PASSWORD).getStatusCode());
    }

    @Test
    void 대소문자만_바꿔서_한도를_새로_받을_수_없다() {
        String email = signup("caseshift");
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password");
        }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                login(email.toUpperCase(), "wrong-password").getStatusCode());
    }

    @Test
    void 계정이_없어도_같은_한도를_쓴다() {
        String missing = "nobody-here@queuemate.dev";
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.UNAUTHORIZED, login(missing, "whatever").getStatusCode());
        }

        // 존재하는 계정과 다르게 반응하면 어느 이메일이 가입돼 있는지 알아낼 수 있다.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, login(missing, "whatever").getStatusCode());
    }

    @Test
    void 계정을_바꿔가며_시도해도_IP_한도가_막는다() {
        // 계정별 한도만 있으면 공격자가 계정을 바꿔 한도를 새로 받는다.
        // 계정 6개 × 5회 = 30회로 IP 한도를 정확히 채운다.
        for (int i = 0; i < 6; i++) {
            String email = signup("ipuser" + i);
            for (int attempt = 0; attempt < 5; attempt++) {
                assertEquals(HttpStatus.UNAUTHORIZED, login(email, "wrong-password").getStatusCode());
            }
        }

        // 새 계정에 올바른 비밀번호를 넣어도 IP 한도에 걸린다.
        String fresh = signup("late");
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, login(fresh, PASSWORD).getStatusCode());
    }

    private String signup(String nickname) {
        String email = nickname + "@queuemate.dev";
        // 가입은 로그인 한도를 건드리지 않는다. 제한은 /login에만 걸려 있다.
        http.postForEntity("/api/v1/auth/signup",
                new SignupRequest(email, PASSWORD, nickname), String.class);
        return email;
    }

    private ResponseEntity<String> login(String email, String password) {
        return http.postForEntity("/api/v1/auth/login",
                new LoginRequest(email, password), String.class);
    }
}
