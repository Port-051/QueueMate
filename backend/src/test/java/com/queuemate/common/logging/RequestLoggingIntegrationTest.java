package com.queuemate.common.logging;

import com.queuemate.common.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** filter chain에 실제로 얹혔을 때의 동작. 단위 테스트는 필터를 직접 부르느라 순서를 못 본다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestLoggingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

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

    @Autowired TestRestTemplate http;
    @Autowired JwtTokenService tokenService;

    @Test
    void 인증에_실패한_요청도_requestId를_돌려준다() {
        // security chain보다 필터가 먼저 돌아야 401 응답에도 상관관계 id가 붙는다.
        ResponseEntity<String> response = http.getForEntity("/api/v1/users/me", String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        String requestId = response.getHeaders().getFirst(RequestLoggingFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(requestId.matches("[a-f0-9]{32}"), "생성된 id여야 한다: " + requestId);
    }

    @Test
    void edge가_준_requestId를_그대로_돌려준다() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(RequestLoggingFilter.REQUEST_ID_HEADER, "edge-request-1");

        ResponseEntity<String> response = http.exchange(
                "/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals("edge-request-1",
                response.getHeaders().getFirst(RequestLoggingFilter.REQUEST_ID_HEADER));
    }

    @Test
    void 인증된_요청이_없는_경로를_치면_404다() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.issueAccessToken(UUID.randomUUID()));

        ResponseEntity<String> response = http.exchange(
                "/api/v1/definitely-not-a-route", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // /error dispatch가 막혀 있으면 여기서 401이 나오고 access log의 status와도 어긋난다.
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
