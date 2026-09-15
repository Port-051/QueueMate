package com.queuemate.auth.oauth;

import com.queuemate.auth.api.AuthDtos.OAuthExchangeRequest;
import com.queuemate.auth.api.AuthDtos.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 소셜 로그인 전체 흐름. 제공자는 test 프로파일에서만 뜨는 가짜(DEV)를 쓴다.
 *
 * <p>여기서 지키려는 것은 세 가지다. 서버가 만들지 않은 state는 통과하지 못한다.
 * 교환 코드는 한 번만 쓸 수 있다. 로그인 후 이동 경로는 앱 내부로만 나간다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthLoginFlowIntegrationTest {

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

    @Autowired TestRestTemplate http;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE users CASCADE").update();
        // 리다이렉트를 따라가면 Location을 볼 수 없다. 이 흐름의 검증 대상이 바로 그 Location이다.
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void 제공자_목록에는_설정된_것만_나온다() {
        // test 프로파일은 expose-unconfigured-providers가 꺼져 있다. 운영과 같은 판단이다.
        ResponseEntity<Map[]> res = http.getForEntity("/api/v1/auth/oauth/providers", Map[].class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        // 카카오·네이버는 자격 증명이 없으므로 목록에 없다. 눌러도 실패할 버튼을 그리지 않기 위해서다.
        assertTrue(java.util.Arrays.stream(Objects.requireNonNull(res.getBody()))
                .allMatch(m -> "DEV".equals(m.get("provider"))), "설정되지 않은 제공자가 노출됐다");
    }

    @Test
    void 동의부터_토큰까지_한_번에_이어진다() {
        String state = startAuthorize("/app/home");

        URI frontend = callback("dev-1", state);
        assertNull(query(frontend, "error"));
        assertEquals("/app/home", query(frontend, "redirect"));

        String handoff = query(frontend, "code");
        assertNotNull(handoff);

        ResponseEntity<TokenResponse> tokens = exchange(handoff);
        assertEquals(HttpStatus.OK, tokens.getStatusCode());
        assertNotNull(Objects.requireNonNull(tokens.getBody()).accessToken());
        assertEquals(1, userCount());
    }

    @Test
    void 교환_코드는_한_번만_쓸_수_있다() {
        String handoff = query(callback("dev-1", startAuthorize(null)), "code");

        assertEquals(HttpStatus.OK, exchange(handoff).getStatusCode());
        // 두 번째는 거절한다. URL에 남은 코드가 나중에 주워져도 로그인되지 않아야 한다.
        assertEquals(HttpStatus.UNAUTHORIZED, exchange(handoff).getStatusCode());
    }

    @Test
    void 서버가_만들지_않은_state는_통과하지_못한다() {
        // state 검증이 이 흐름의 CSRF 방어다. 없으면 피해자를 공격자 계정에 로그인시킬 수 있다.
        URI frontend = callback("dev-1", "만들어낸-state");

        assertEquals("INVALID_STATE", query(frontend, "error"));
        assertNull(query(frontend, "code"));
        assertEquals(0, userCount());
    }

    @Test
    void 같은_state를_두_번_쓸_수_없다() {
        String state = startAuthorize(null);
        assertNotNull(query(callback("dev-1", state), "code"));

        assertEquals("INVALID_STATE", query(callback("dev-1", state), "error"));
    }

    @Test
    void 외부_주소로는_돌려보내지_않는다() {
        // open redirect 방어. 우리 도메인의 로그인 링크로 다른 사이트에 떨어뜨릴 수 없어야 한다.
        String state = startAuthorize("https://evil.example.com");

        assertEquals("/app/home", query(callback("dev-1", state), "redirect"));
    }

    @Test
    void 같은_제공자_계정은_언제나_같은_사용자다() {
        exchange(query(callback("dev-1", startAuthorize(null)), "code"));
        exchange(query(callback("dev-1", startAuthorize(null)), "code"));

        assertEquals(1, userCount());
    }

    @Test
    void 동의를_거부하면_오류를_달고_돌아간다() {
        ResponseEntity<Void> res = http.getForEntity(
                "/api/v1/auth/oauth/dev/callback?error=access_denied", Void.class);

        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        assertEquals("ACCESS_DENIED", query(location(res), "error"));
    }

    @Test
    void 설정되지_않은_제공자는_없는_것으로_본다() {
        assertEquals(HttpStatus.NOT_FOUND,
                http.getForEntity("/api/v1/auth/oauth/kakao/authorize", Void.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                http.getForEntity("/api/v1/auth/oauth/steam/authorize", Void.class).getStatusCode());
    }

    /** authorize를 호출해 서버가 만든 state를 얻는다. */
    private String startAuthorize(String redirect) {
        String path = "/api/v1/auth/oauth/dev/authorize"
                + (redirect == null ? "" : "?redirect=" + java.net.URLEncoder.encode(
                        redirect, java.nio.charset.StandardCharsets.UTF_8));
        ResponseEntity<Void> res = http.getForEntity(path, Void.class);
        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        String state = query(location(res), "state");
        assertNotNull(state);
        return state;
    }

    /** 제공자가 브라우저를 돌려보낸 상황. 호스트는 버리고 우리 서버로 같은 경로를 친다. */
    private URI callback(String code, String state) {
        ResponseEntity<Void> res = http.getForEntity(
                "/api/v1/auth/oauth/dev/callback?code={code}&state={state}", Void.class, code, state);
        assertEquals(HttpStatus.FOUND, res.getStatusCode());
        return location(res);
    }

    private ResponseEntity<TokenResponse> exchange(String code) {
        return http.postForEntity("/api/v1/auth/oauth/exchange",
                new OAuthExchangeRequest(code), TokenResponse.class);
    }

    private int userCount() {
        return jdbc.sql("SELECT count(*) FROM users").query(Integer.class).single();
    }

    private static URI location(ResponseEntity<?> res) {
        return Objects.requireNonNull(res.getHeaders().getLocation());
    }

    private static String query(URI uri, String key) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(key);
    }
}
