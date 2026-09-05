package com.queuemate.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queuemate.common.security.JwtTokenService;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * docs/15의 CRUD 테스트 케이스가 공유하는 바탕.
 *
 * <p>필터 체인과 Jackson을 실제로 통과시켜야 하므로 {@code @WebMvcTest} 슬라이스를 쓰지 않는다.
 * 이 문서가 잡으려는 문제(enum 역직렬화가 예외 핸들러를 비껴가는 것,
 * 파라미터 검증이 500으로 나가는 것)는 전부 필터·핸들러 조합에서만 재현된다.
 *
 * <p>컨테이너는 {@code @Container}가 아니라 static 블록에서 직접 띄운다. 테스트 클래스마다
 * 껐다 켜면 클래스 수만큼 Flyway가 다시 돌아 느리다. 여기서는 JVM당 한 번만 띄우고
 * 정리는 Ryuk에 맡긴다.
 */
@EnabledIf(value = "com.queuemate.api.DockerAvailability#isAvailable",
        disabledReason = "Docker가 없으면 계약 테스트를 돌릴 수 없다")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiContractTestSupport {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("queuemate")
                    .withUsername("queuemate")
                    .withPassword("queuemate");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** docs/14 §0.8의 고정 더미. 존재하지 않는 id다. */
    protected static final UUID NONE = UUID.fromString("00000000-0000-4000-8000-000000000000");

    protected static final String PASSWORD = "Qm!passw0rd";

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Autowired protected TestRestTemplate http;
    @Autowired protected JwtTokenService tokenService;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JdbcClient jdbc;
    @Autowired protected StringRedisTemplate redis;

    @BeforeEach
    void resetState() {
        // TestRestTemplate의 기본 요청 팩토리는 PATCH를 아예 못 보낸다.
        // 예약 수정이 PATCH라 이걸 바꾸지 않으면 그 케이스를 짤 수 없다.
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        jdbc.sql("TRUNCATE reports, blocks, friendships, friend_requests, "
                + "party_members, parties, proposal_members, match_proposals, "
                + "match_requests, reservations, game_accounts, users CASCADE").update();

        redis.execute((RedisCallback<Void>) (RedisConnection connection) -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    // ---------------------------------------------------------------- 요청

    /**
     * @param actor 토큰을 실을 사용자. null이면 Authorization 헤더를 아예 붙이지 않는다
     * @param body  JSON 문자열. null이면 바디도 Content-Type도 붙이지 않는다
     */
    protected ResponseEntity<String> send(HttpMethod method, String path, UUID actor, String body) {
        HttpHeaders headers = new HttpHeaders();
        if (actor != null) {
            headers.setBearerAuth(tokenService.issueAccessToken(actor));
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> get(String path, UUID actor) {
        return send(HttpMethod.GET, path, actor, null);
    }

    protected ResponseEntity<String> post(String path, UUID actor, String body) {
        return send(HttpMethod.POST, path, actor, body);
    }

    protected ResponseEntity<String> put(String path, UUID actor, String body) {
        return send(HttpMethod.PUT, path, actor, body);
    }

    protected ResponseEntity<String> patch(String path, UUID actor, String body) {
        return send(HttpMethod.PATCH, path, actor, body);
    }

    protected ResponseEntity<String> delete(String path, UUID actor) {
        return send(HttpMethod.DELETE, path, actor, null);
    }

    /** 바디는 있는데 Content-Type을 일부러 빼는 경우. TC-X-04가 쓴다. */
    protected ResponseEntity<String> postWithoutContentType(String path, UUID actor, String body) {
        HttpHeaders headers = new HttpHeaders();
        if (actor != null) {
            headers.setBearerAuth(tokenService.issueAccessToken(actor));
        }
        headers.setContentType(MediaType.TEXT_PLAIN);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    // ---------------------------------------------------------------- 응답 해석

    protected JsonNode body(ResponseEntity<String> response) {
        assertNotNull(response.getBody(), "본문이 있어야 하는 응답인데 비어 있다");
        try {
            return JSON.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError("JSON이 아니다: " + response.getBody(), e);
        }
    }

    /** 상태 코드와 에러 code를 한 번에 본다. docs/14 §0.6의 공통 형태를 강제한다. */
    protected void assertError(ResponseEntity<String> response, int status, String code) {
        assertEquals(status, response.getStatusCode().value(),
                () -> "본문: " + response.getBody());
        JsonNode error = body(response);
        assertEquals(code, error.path("code").asText(),
                () -> "본문: " + response.getBody());
        assertTrue(error.hasNonNull("message"), "message가 없다: " + response.getBody());
    }

    protected void assertStatus(ResponseEntity<String> response, int status) {
        assertEquals(status, response.getStatusCode().value(),
                () -> "본문: " + response.getBody());
    }

    // ---------------------------------------------------------------- 픽스처

    /** 비밀번호까지 제대로 해싱된 사용자. 로그인 케이스도 이 사용자를 쓴다. */
    protected UUID user(String nickname) {
        return users.save(User.create(
                nickname + "@queuemate.dev", passwordEncoder.encode(PASSWORD), nickname)).getId();
    }

    protected UUID alpha() {
        return user("alpha");
    }

    protected UUID bravo() {
        return user("bravo");
    }

    protected UUID charlie() {
        return user("charlie");
    }

    /** docs/14 §4.1의 LoL 조건. position만 갈아 끼운다. */
    protected static String lolCondition(String position) {
        return """
                {
                  "game": "LOL",
                  "modeKey": "SOLO_DUO_RANKED",
                  "keyCondition": { "type": "POSITION", "value": "%s" },
                  "voicePreference": "OPTIONAL",
                  "playPurpose": "RANK_UP"
                }
                """.formatted(position);
    }
}
