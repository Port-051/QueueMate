package com.queuemate.auth;

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

/**
 * 계정 대량 생성 방어.
 *
 * 로그인 제한과 달리 성공해도 카운터를 지우지 않는다. 로그인에서는 성공이 정상
 * 사용자라는 증거지만, 가입에서는 성공 자체가 아껴야 할 자원이다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignupRateLimitIntegrationTest {

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
    private static final String IP = "127.0.0.1";

    @Autowired TestRestTemplate http;
    @Autowired RateLimiter rateLimiter;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE users CASCADE").update();
        // 테스트가 모두 같은 IP에서 온다. 앞 테스트가 쓴 한도가 남으면 뒤 테스트가 엉뚱하게 막힌다.
        rateLimiter.reset("signup:ip:burst", IP, Duration.ofMinutes(10));
        rateLimiter.reset("signup:ip:daily", IP, Duration.ofDays(1));
    }

    @Test
    void 짧은_시간에_계정을_많이_만들면_막힌다() {
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.CREATED, signup("user" + i).getStatusCode());
        }

        // 사람은 가입을 한 번 한다. 같은 자리에서 10분에 여섯 번은 사람의 사용 방식이 아니다.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, signup("user5").getStatusCode());
    }

    @Test
    void 성공한_가입은_한도를_돌려주지_않는다() {
        for (int i = 0; i < 5; i++) {
            assertEquals(HttpStatus.CREATED, signup("ok" + i).getStatusCode());
        }

        // 로그인은 성공하면 카운터를 지운다. 여기서 같은 규칙을 쓰면 한도가 없는 것과 같다.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, signup("ok5").getStatusCode());
    }

    @Test
    void 중복으로_실패한_가입도_한도를_쓴다() {
        assertEquals(HttpStatus.CREATED, signup("taken").getStatusCode());
        for (int i = 0; i < 4; i++) {
            assertEquals(HttpStatus.CONFLICT, signup("taken").getStatusCode());
        }

        // 실패를 빼 주면 이미 있는 이메일을 넣어 한도를 피해 가는 길이 생긴다.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, signup("fresh").getStatusCode());
    }

    @Test
    void 형식이_잘못된_요청은_한도를_쓰지_않는다() {
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    http.postForEntity("/api/v1/auth/signup",
                            new SignupRequest("not-an-email", "short", ""), String.class)
                            .getStatusCode());
        }

        // 검증에서 걸린 요청은 비밀번호 해싱까지 가지 않는다. 아낄 자원을 쓰지 않았다.
        assertEquals(HttpStatus.CREATED, signup("valid").getStatusCode());
    }

    @Test
    void 로그인_한도와_따로_센다() {
        for (int i = 0; i < 5; i++) {
            signup("mixed" + i);
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, signup("mixed5").getStatusCode());

        // 가입 한도가 로그인까지 막으면 이미 가입한 사용자가 남의 가입 때문에 못 들어온다.
        assertEquals(HttpStatus.OK, http.postForEntity("/api/v1/auth/login",
                new com.queuemate.auth.api.AuthDtos.LoginRequest(
                        "mixed0@queuemate.dev", PASSWORD), String.class).getStatusCode());
    }

    private ResponseEntity<String> signup(String nickname) {
        return http.postForEntity("/api/v1/auth/signup",
                new SignupRequest(nickname + "@queuemate.dev", PASSWORD, nickname), String.class);
    }
}
