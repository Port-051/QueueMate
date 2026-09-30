package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEMP-DEV-LOGIN — 개발용 로그인 {@code POST /api/v1/auth/dev-login}(2026-09-29 소유자 결정 · {@code contracts/platform-api.md} P-34).
 * 걷어낼 때 이 파일째 지운다.
 *
 * <p>테스트 설정({@code src/test/resources/config/application.yaml})이 켜 두었다 — 그래서 다른 API 테스트와 스프링 컨텍스트를 같이 쓴다.
 * 꺼졌을 때(운영의 기본값)는 {@link DevLoginDisabledTest} 가 본다.
 */
class DevLoginApiTest extends ApiTestSupport {

    static final String PATH = "/api/v1/auth/dev-login";
    static final String DEFAULT_NICKNAME = "dev-tester";

    @Test
    @DisplayName("닉네임을 주면 그 사용자를 만들고 진짜 쿠키 둘(access · refresh)을 준다 — 그 쿠키로 users/me 가 200 이고 소셜 연결은 없다 · refresh 로 재발급도 된다")
    void createsUserAndIssuesRealCookies() throws Exception
    {
        String nickname = newNickname();

        MvcResult result = devLogin(json("nickname", nickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        Long userId = userIdOf(nickname);
        assertThat(userId).isNotNull().isEqualTo(userIdFrom(result));
        Cookie access = result.getResponse().getCookie("qm_access");
        assertThat(access).isNotNull();
        assertThat(access.getValue()).isNotBlank();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getPath()).isEqualTo("/");
        Cookie refresh = refreshCookieOf(result);
        assertThat(refresh).isNotNull();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth/refresh");

        mockMvc.perform(get("/api/v1/users/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.socialProviders.length()").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from social_identities where user_id = ?", Integer.class, userId)).isZero();

        // refresh 도 진짜다 — 재발급이 같은 사용자로 200 이다
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userId), Long.class))
                .andReturn();
        refreshCookieOf(refreshed);
    }

    @Test
    @DisplayName("같은 닉네임으로 두 번 부르면 같은 사용자다. 이미 있는 사용자(소셜로 가입한 사람이어도)의 닉네임이면 그 사람으로 로그인한다")
    void sameNicknameSameUser() throws Exception
    {
        String nickname = newNickname();
        Long first = userIdFrom(devLogin(json("nickname", nickname)).andExpect(status().isOk()).andReturn());
        Long second = userIdFrom(devLogin(json("nickname", nickname)).andExpect(status().isOk()).andReturn());

        assertThat(second).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("select count(*) from users where nickname = ?", Integer.class, nickname))
                .isEqualTo(1);

        String existing = newNickname();
        Long existingId = insertUser(existing);
        assertThat(userIdFrom(devLogin(json("nickname", existing)).andExpect(status().isOk()).andReturn()))
                .isEqualTo(existingId);
    }

    @Test
    @DisplayName("같은 새 닉네임으로 동시에 여럿이 불러도(8 스레드) 사용자는 하나이고 전부 그 번호로 200 이다")
    void concurrentSameNickname() throws Exception
    {
        String nickname = newNickname();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        try
        {
            for(int i = 0; i < 8; i++)
            {
                results.add(pool.submit(() -> {
                    go.await();
                    return devLogin(json("nickname", nickname)).andReturn();
                }));
            }
            go.countDown();
            Set<Long> userIds = new HashSet<>();
            for(Future<MvcResult> result : results)
            {
                MvcResult done = result.get(30, TimeUnit.SECONDS);
                assertThat(done.getResponse().getStatus()).isEqualTo(200);
                userIds.add(userIdFrom(done));
            }
            assertThat(userIds).containsExactly(userIdOf(nickname));
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from users where nickname = ?", Integer.class, nickname))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("본문이 없거나 nickname 이 없거나 null 이면 dev-tester 다")
    void defaultNickname() throws Exception
    {
        // dev-tester 는 손으로 해 본 사람이 이미 만들어 뒀을 수 있다(테스트와 bootRun 이 같은 DB 다) — 있던 사용자는 지우지 않는다
        boolean existed = jdbcTemplate.queryForObject(
                "select count(*) from users where nickname = ?", Integer.class, DEFAULT_NICKNAME) > 0;
        try
        {
            MvcResult noBody = register(mockMvc.perform(post(PATH)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value(DEFAULT_NICKNAME))
                    .andReturn();
            MvcResult emptyObject = devLogin("{}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value(DEFAULT_NICKNAME))
                    .andReturn();
            MvcResult explicitNull = devLogin(json("nickname", null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value(DEFAULT_NICKNAME))
                    .andReturn();

            assertThat(userIdFrom(emptyObject)).isEqualTo(userIdFrom(noBody));
            assertThat(userIdFrom(explicitNull)).isEqualTo(userIdFrom(noBody));
            assertThat(noBody.getResponse().getCookie("qm_access")).isNotNull();
        }
        finally
        {
            if(!existed)
            {
                jdbcTemplate.update("delete from users where nickname = ?", DEFAULT_NICKNAME);
            }
        }
    }

    @Test
    @DisplayName("닉네임이 있으면 소셜 가입과 같은 규칙이다 — 2~16자 · 앞뒤 공백 없음 · 빈 문자열도 위반. 어기면 400 VALIDATION_FAILED 이고 쿠키가 없다")
    void nicknameRules() throws Exception
    {
        for(String invalid : new String[]{"x", "a".repeat(17), " padded ", ""})
        {
            MvcResult result = devLogin(json("nickname", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(detailFor("nickname"))
                    .andReturn();
            assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where nickname = ?", Integer.class, " padded ")).isZero();

        devLogin("not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST 라서 Origin 검사를 받는다 — 다른 Origin 이면 403 ORIGIN_NOT_ALLOWED 이고 사용자를 만들지 않는다. 허용된 Origin 이면 200")
    void checksOrigin() throws Exception
    {
        String nickname = newNickname();
        mockMvc.perform(post(PATH).header(HttpHeaders.ORIGIN, "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content(json("nickname", nickname)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
        assertThat(userIdOf(nickname)).isNull();

        register(mockMvc.perform(post(PATH).header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content(json("nickname", nickname))))
                .andExpect(status().isOk());
        assertThat(userIdOf(nickname)).isNotNull();
    }

    /** 받은 refresh 를 끝에 지우도록 적어 둔다 */
    private ResultActions devLogin(String body) throws Exception
    {
        MockHttpServletRequestBuilder request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
        return register(mockMvc.perform(request));
    }

    private ResultActions register(ResultActions actions)
    {
        refreshCookieOf(actions.andReturn());
        return actions;
    }

    private Long userIdFrom(MvcResult result) throws Exception
    {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("userId").asLong();
    }
}
