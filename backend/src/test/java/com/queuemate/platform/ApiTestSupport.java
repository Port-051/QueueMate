package com.queuemate.platform;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 통합 테스트의 공통 바탕 — 앱 전체를 띄우고(MockMvc · 보안 필터 · 진짜 PostgreSQL) 요청을 보낸다. <b>H2 를 쓰지 않는다.</b>
 *
 * <p><b>돌리는 법</b>과 <b>5432 · 6379 를 피하는 이유</b>는 {@link PlatformApplicationTests} 와 같다 — 그 포트면 클래스를 통째로 건너뛴다.
 * 건너뛴 것을 통과로 읽지 마라.
 *
 * <p><b>사용자의 식별자가 둘이다</b>(2026-09-22 소유자 결정) — 가입할 때 정하는 <b>로그인 아이디</b>({@link #newLoginId()})와 DB 가 매기는 <b>사용자 번호</b>
 * ({@link #userIdOf(String)} — 가입 응답의 {@code userId}). 로그인 아이디는 가입 · 로그인 본문에만 쓰고, 그 밖의 모든 곳(URL · 본문의 {@code userId} · 방 키 ·
 * 다른 스키마의 컬럼)은 사용자 번호를 쓴다. 아무도 아닌 번호가 필요하면 {@link #unknownUserId()} 다.
 *
 * <p><b>DB 는 테스트 사이에 남는다.</b> 그래서 아이디 · 닉네임은 매번 새로 짓고, 만든 계정은 테스트가 끝나면 지운다
 * (게임 계정 · 전적 · 비밀번호 · 소셜 연결은 {@code ON DELETE CASCADE} 로 같이 지워진다. <b>차단 · 친구 요청 · 친구 · 신고 · 최근 함께한 사람 · 글 · 파티는 따로 지운다</b> —
 * {@code social.*} · {@code party.*} 는 {@code account.users} 로 FK 가 없다). 그 사람의 로그인 실패 횟수(Redis)도 지운다 —
 * <b>Redis 는 자기 키만 지운다</b>({@code FLUSHDB} 금지 — 같은 Redis 를 {@code room} 이 쓸 수 있다). 트랜잭션 롤백에 기대지 않는다 — MockMvc 의 요청은
 * 서비스의 트랜잭션에서 실제로 커밋되고, 동시성 테스트는 여러 스레드(여러 커넥션)를 쓴다.
 *
 * <p>하위 클래스들이 설정을 바꾸지 않으므로 스프링 컨텍스트 하나를 같이 쓴다(/mnt/c 아래라 컨텍스트를 띄우는 것이 느리다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisabledIf(value = "com.queuemate.platform.ApiTestSupport#pointsAtForeignPorts",
        disabledReason = "DB_PORT=5432 또는 REDIS_PORT=6379 다 — 다른 프로젝트의 것이다. 테스트용을 5433 · 6380 으로 띄워라")
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 이 테스트가 만든(만들려고 한) 로그인 아이디. 끝나면 지운다. 동시성 테스트가 여러 스레드에서 넣는다 */
    private final List<String> createdLoginIds = new CopyOnWriteArrayList<>();

    /** 로그인 아이디 → 사용자 번호. 가입 응답에서 받아 둔다 */
    private final Map<String, Long> userIdsByLoginId = new ConcurrentHashMap<>();

    /** 아무도 아닌 번호({@link #unknownUserId()}) — 그 번호로 적힌 social · party 의 줄을 끝나면 지운다 */
    private final List<Long> unknownUserIds = new CopyOnWriteArrayList<>();

    /**
     * 이 테스트가 받은 refresh 토큰. 끝나면 그 키만 지운다 — <b>{@code KEYS} · {@code FLUSHDB} 를 쓰지 않는다</b>
     * (같은 Redis 를 {@code room} 이 쓸 수 있다). {@link #refreshCookieOf} 가 적어 둔다
     */
    private final List<String> receivedRefreshTokens = new CopyOnWriteArrayList<>();

    /** application.yaml 의 기본값과 같아야 한다 — DB_PORT 5433, REDIS_PORT 6380. */
    public static boolean pointsAtForeignPorts()
    {
        return "5432".equals(envOrDefault("DB_PORT", "5433"))
                || "6379".equals(envOrDefault("REDIS_PORT", "6380"));
    }

    private static String envOrDefault(String name, String defaultValue)
    {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    @AfterEach
    void deleteCreatedUsers()
    {
        for(String loginId : createdLoginIds)
        {
            Long userId = lookupUserId(loginId);
            if(userId != null)
            {
                deleteRowsOf(userId);
                jdbcTemplate.update("delete from account.users where id = ?", userId);
            }
            // 로그인을 틀려 본 테스트가 남긴 횟수 · 잠금. 이름은 계약(contracts/platform-api.md "로그인 실패 제한")의 것이다 — 로그인 아이디 기준이다
            redisTemplate.delete(List.of("qm:auth:login-fail:" + loginId, "qm:auth:login-lock:" + loginId));
        }
        for(Long userId : unknownUserIds)
        {
            deleteRowsOf(userId);
        }
        for(String refreshToken : receivedRefreshTokens)
        {
            // 이름은 계약(contracts/platform-api.md "refresh 토큰")의 것이다. 받은 값만 지운다
            redisTemplate.delete("qm:auth:refresh:" + refreshToken);
        }
        createdLoginIds.clear();
        userIdsByLoginId.clear();
        unknownUserIds.clear();
        receivedRefreshTokens.clear();
    }

    /** {@code account.users} 밖에 그 사용자 번호로 적힌 줄 전부 — FK 가 없어 CASCADE 로 지워지지 않는다 */
    private void deleteRowsOf(Long userId)
    {
        jdbcTemplate.update("delete from social.blocks where blocker_id = ? or blocked_id = ?", userId, userId);
        jdbcTemplate.update("delete from social.friend_requests where requester_id = ? or receiver_id = ?", userId, userId);
        jdbcTemplate.update("delete from social.friendships where user_low_id = ? or user_high_id = ?", userId, userId);
        jdbcTemplate.update("delete from social.reports where reporter_id = ? or target_user_id = ?", userId, userId);
        jdbcTemplate.update("delete from social.recent_players where user_id = ? or other_user_id = ?", userId, userId);
        // 파티가 글을 FK 로 잡고 있어(ON DELETE CASCADE 가 아니다) 파티를 먼저 지운다. 파티원 · 찾는 포지션은 같이 지워진다
        jdbcTemplate.update("delete from party.parties where post_id in "
                + "(select id from party.recruit_posts where host_id = ?)", userId);
        jdbcTemplate.update("delete from party.recruit_posts where host_id = ?", userId);
    }

    /** 겹치지 않는 로그인 아이디(14자). 형식({@code ^[a-z0-9_]{4,20}$})에 맞는다. 끝나면 지워지게 적어 둔다 */
    protected String newLoginId()
    {
        String loginId = "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createdLoginIds.add(loginId);
        return loginId;
    }

    /**
     * 그 로그인 아이디의 <b>사용자 번호</b>. 가입 응답에서 받아 둔 값이고, 없으면(SQL 로 직접 넣은 사용자) DB 에서 읽는다.
     * 가입하지 않은 아이디면 {@code null} 이다.
     */
    protected Long userIdOf(String loginId)
    {
        Long known = userIdsByLoginId.get(loginId);
        return known != null ? known : lookupUserId(loginId);
    }

    private Long lookupUserId(String loginId)
    {
        List<Long> ids = jdbcTemplate.queryForList("select id from account.users where login_id = ?", Long.class, loginId);
        if(ids.isEmpty())
        {
            return null;
        }
        userIdsByLoginId.put(loginId, ids.get(0));
        return ids.get(0);
    }

    /** 아무도 아닌 사용자 번호 — identity 가 닿지 않을 만큼 큰 무작위 값이다. 그 번호로 적힌 social · party 의 줄은 끝나면 지운다 */
    protected Long unknownUserId()
    {
        long userId = ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 2_000_000_000_000_000L);
        unknownUserIds.add(userId);
        return userId;
    }

    /** 로그인 아이디에서 닉네임을 짓는다(14자 — 16자 제한 안). 아이디가 겹치지 않으니 닉네임도 겹치지 않는다 */
    protected static String nicknameOf(String loginId)
    {
        return "n" + loginId.substring(1);
    }

    /** 가입 요청. 201 이면 응답의 {@code userId} 를 받아 둔다({@link #userIdOf}) */
    protected ResultActions signup(String loginId, String password, String nickname) throws Exception
    {
        ResultActions actions = mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("loginId", loginId, "password", password, "nickname", nickname)));
        rememberUserId(loginId, actions.andReturn());
        return actions;
    }

    protected ResultActions login(String loginId, String password) throws Exception
    {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("loginId", loginId, "password", password)));
    }

    /** 새 계정을 만들고 로그인해서 <b>access 쿠키</b>를 돌려준다. 사용자 번호는 {@link #userIdOf} 로 꺼낸다 */
    protected Cookie signupAndLogin(String loginId) throws Exception
    {
        signup(loginId, PASSWORD, nicknameOf(loginId)).andExpect(status().isCreated());
        MvcResult result = login(loginId, PASSWORD).andExpect(status().isOk()).andReturn();
        // 같이 온 refresh 는 끝나면 Redis 에서 지운다
        refreshCookieOf(result);
        return result.getResponse().getCookie("qm_access");
    }

    /**
     * 응답의 <b>refresh 쿠키</b>({@code qm_refresh}). 값이 있으면 끝에 지우도록 적어 둔다 — <b>refresh 를 받는 테스트는 이것으로 받는다.</b>
     * 쿠키가 없거나(Redis 가 죽었다) 지우는 쿠키({@code Max-Age=0})면 적지 않는다.
     */
    protected Cookie refreshCookieOf(MvcResult result)
    {
        Cookie cookie = result.getResponse().getCookie("qm_refresh");
        if(cookie != null && !cookie.getValue().isBlank() && cookie.getMaxAge() > 0)
        {
            receivedRefreshTokens.add(cookie.getValue());
        }
        return cookie;
    }

    /** 그 refresh 토큰이 Redis 에 있는가 — 로그아웃이 정말 지웠는지를 본다 */
    protected boolean refreshTokenStored(String refreshToken)
    {
        return Boolean.TRUE.equals(redisTemplate.hasKey("qm:auth:refresh:" + refreshToken));
    }

    /** 가입 · 소셜 가입의 201 응답에서 사용자 번호를 받아 둔다 */
    protected void rememberUserId(String loginId, MvcResult result) throws Exception
    {
        if(result.getResponse().getStatus() == 201)
        {
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            if(body.has("userId") && body.get("userId").isNumber())
            {
                userIdsByLoginId.put(loginId, body.get("userId").asLong());
            }
        }
    }

    /**
     * 에러 본문의 {@code details}(문자열의 배열)에 그 필드의 줄({@code "필드: 사유"})이 있는지 본다
     * ({@code contracts/platform-api.md} "공통" — {@code matching} · {@code room} 과 같은 모양이다).
     */
    protected static ResultMatcher detailFor(String field)
    {
        return jsonPath("$.details", hasItem(startsWith(field + ": ")));
    }

    /**
     * 키 · 값을 번갈아 받아 JSON 객체를 만든다. 값이 {@code null} 이면 JSON 의 null, {@link Number} 면 JSON 숫자, 나머지는 문자열이다.
     * 테스트의 문자열 값에는 따옴표 · 역슬래시가 없다.
     */
    protected static String json(Object... keysAndValues)
    {
        StringBuilder builder = new StringBuilder("{");
        for(int i = 0; i < keysAndValues.length; i += 2)
        {
            if(i > 0)
            {
                builder.append(',');
            }
            Object value = keysAndValues[i + 1];
            builder.append('"').append(keysAndValues[i]).append("\":");
            if(value == null)
            {
                builder.append("null");
            }
            else if(value instanceof Number)
            {
                builder.append(value);
            }
            else
            {
                builder.append('"').append(value).append('"');
            }
        }
        return builder.append('}').toString();
    }
}
