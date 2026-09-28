package com.queuemate.platform;

import com.queuemate.platform.common.security.AccessTokenIssuer;
import com.queuemate.platform.common.security.RefreshTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
import java.util.function.Consumer;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * API 통합 테스트의 공통 바탕 — 앱 전체를 띄우고(MockMvc · 보안 필터 · 진짜 PostgreSQL) 요청을 보낸다. <b>H2 를 쓰지 않는다.</b>
 *
 * <p><b>돌리는 법</b>과 <b>5432 · 6379 를 피하는 이유</b>는 {@link PlatformApplicationTests} 와 같다 — 그 포트면 클래스를 통째로 건너뛴다.
 * 건너뛴 것을 통과로 읽지 마라.
 *
 * <p><b>사용자의 식별자는 사용자 번호 하나다</b>(2026-09-26 소유자 결정 — 로그인은 소셜뿐이고 로그인 아이디 · 비밀번호가 없다). 테스트는 사람을
 * <b>닉네임</b>({@link #newNickname()})으로 부르고 그 번호는 {@link #userIdOf(String)} 로 꺼낸다. 로그인은 {@link #login(String)} 이 한다 —
 * 소셜 흐름을 HTTP 로 매번 타지 않고 {@code users} 에 줄을 넣은 뒤 앱의 빈({@link AccessTokenIssuer} · {@link RefreshTokens})으로 쿠키를 직접 찍는다
 * (소셜 흐름 자체는 {@code SocialLoginApiTest} 가 본다). 아무도 아닌 번호가 필요하면 {@link #unknownUserId()} 다 — <b>그 번호로는 DB 에 줄을 넣을 수 없다</b>
 * (사용자 번호의 칸에 전부 {@code users(id)} 로 FK 가 있다). SQL 로 줄을 넣을 사용자가 필요하면 {@link #insertUser()} 다.
 *
 * <p><b>DB 는 테스트 사이에 남는다.</b> 그래서 닉네임은 매번 새로 짓고, 만든 계정은 테스트가 끝나면 지운다
 * (사용자 번호를 담는 칸이 전부 {@code users(id)} 로 FK({@code ON DELETE CASCADE})라 <b>{@code users} 의 줄만 지우면</b> 게임 계정 · 전적 ·
 * 소셜 연결 · 차단 · 친구 요청 · 친구 · 신고 · 최근 함께한 사람 · 글 · 파티 · 파티원이 딸려 지워진다 — 2026-09-26). 받은 refresh 토큰(Redis)도 지운다 —
 * <b>Redis 는 자기 키만 지운다</b>({@code FLUSHDB} 금지 — 같은 Redis 를 {@code room} 이 쓸 수 있다). 트랜잭션 롤백에 기대지 않는다 — MockMvc 의 요청은
 * 서비스의 트랜잭션에서 실제로 커밋되고, 동시성 테스트는 여러 스레드(여러 커넥션)를 쓴다.
 *
 * <p><b>gameconfig 는 테스트가 스스로 심는다</b>({@link #seedGameConfig()}) — {@code mode} · {@code tier} 검증이 그 키를 읽기 때문이다(2026-09-24).
 * <b>있던 키는 건드리지 않고</b>(소유자가 seed 를 심어 뒀을 수 있다) 없어서 심은 것만 끝나고 지운다.
 *
 * <p>하위 클래스들이 설정을 바꾸지 않으므로 스프링 컨텍스트 하나를 같이 쓴다(/mnt/c 아래라 컨텍스트를 띄우는 것이 느리다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisabledIf(value = "com.queuemate.platform.ApiTestSupport#pointsAtForeignPorts",
        disabledReason = "DB_PORT=5432 또는 REDIS_PORT=6379 다 — 다른 프로젝트의 것이다. 테스트용을 5433 · 6380 으로 띄워라")
public abstract class ApiTestSupport {

    /** 테스트가 쓰는 모드 · 티어 — 이름은 전부 {@code matching/seed/gameconfig.redis} 의 것이다. 지어내면 검증이 400 으로 거절한다 */
    protected static final String LOL_MODE = "RANKED_SOLO";
    /** 모드를 <b>바꾸는</b> 테스트가 쓰는 두 번째 LoL 모드 */
    protected static final String LOL_MODE_2 = "RANKED_FLEX_5";
    protected static final String VALORANT_MODE = "COMPETITIVE_DUO";
    protected static final String PUBG_MODE = "RANKED_DUO_TPP";
    /** gameconfig 에 <b>없는</b> 모드 — 검증이 거절해야 하는 값이다. 심지 않는다 */
    protected static final String UNKNOWN_MODE = "NO_SUCH_MODE";
    /** 롤 사다리에는 단까지 적힌 이름만 있다({@code GOLD_4} … {@code GOLD_1}) — 단이 없는 이 이름은 없는 티어다. 심지 않는다 */
    protected static final String UNKNOWN_TIER = "GOLD";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private RefreshTokens refreshTokens;

    /** 이 테스트가 지은 닉네임. 끝나면 그 사용자를 지운다. 동시성 테스트가 여러 스레드에서 넣는다 */
    private final List<String> createdNicknames = new CopyOnWriteArrayList<>();

    /** 닉네임(처음 지은 것) → 사용자 번호. 만들 때 · 소셜 가입 응답에서 받아 둔다. 닉네임을 바꿔도 번호로 지울 수 있게 한다 */
    private final Map<String, Long> userIdsByNickname = new ConcurrentHashMap<>();

    /** 이 테스트가 <b>없어서 심은</b> gameconfig 키. 있던 키는 여기 들어오지 않아 지워지지 않는다 */
    private final List<String> seededGameConfigKeys = new CopyOnWriteArrayList<>();

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

    /**
     * {@code mode} · {@code tier} 검증이 읽는 gameconfig 를 심는다 — <b>없는 키만</b>이다. 심지 않으면 검증이 fail-open 으로 통째로 꺼져
     * "없는 모드는 400" 같은 테스트가 조용히 통과한다(무엇을 보는 테스트인지 사라진다).
     *
     * <p>모드는 <b>키가 있는지만</b> 보므로 필드가 하나 있으면 되고, 티어는 <b>이름이 사다리에 있는지만</b> 보므로 사다리의 일부만 심는다 —
     * {@code score} 의 뜻(몇 단계 차이인가)은 매칭의 것이라 이 앱이 읽지 않는다. 값은 그래도 seed 의 것을 적었다.
     */
    @BeforeEach
    void seedGameConfig()
    {
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_MODE, key -> redisTemplate.opsForHash().put(key, "targetPartySize", "2"));
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_MODE_2, key -> redisTemplate.opsForHash().put(key, "targetPartySize", "5"));
        seedIfAbsent("qm:gameconfig:VALORANT:" + VALORANT_MODE,
                key -> redisTemplate.opsForHash().put(key, "targetPartySize", "2"));
        seedIfAbsent("qm:gameconfig:PUBG:" + PUBG_MODE, key -> redisTemplate.opsForHash().put(key, "targetPartySize", "2"));
        seedIfAbsent("qm:gameconfig:LOL:tier", key -> {
            redisTemplate.opsForZSet().add(key, "UNRANKED", 0);
            redisTemplate.opsForZSet().add(key, "GOLD_4", 13);
            redisTemplate.opsForZSet().add(key, "GOLD_2", 15);
            redisTemplate.opsForZSet().add(key, "GOLD_1", 16);
            redisTemplate.opsForZSet().add(key, "EMERALD_4", 21);
        });
        seedIfAbsent("qm:gameconfig:VALORANT:tier", key -> {
            redisTemplate.opsForZSet().add(key, "DIAMOND_2", 17);
            redisTemplate.opsForZSet().add(key, "ASCENDANT_1", 19);
        });
        seedIfAbsent("qm:gameconfig:PUBG:tier", key -> {
            redisTemplate.opsForZSet().add(key, "GOLD_1", 12);
            redisTemplate.opsForZSet().add(key, "SURVIVOR", 26);
        });
    }

    protected void seedIfAbsent(String key, Consumer<String> seed)
    {
        if(Boolean.TRUE.equals(redisTemplate.hasKey(key)))
        {
            // 소유자가 심어 둔 진짜 seed 다 — 건드리지도, 지우지도 않는다
            return;
        }
        seed.accept(key);
        seededGameConfigKeys.add(key);
    }

    @AfterEach
    void deleteSeededGameConfig()
    {
        redisTemplate.delete(seededGameConfigKeys);
        seededGameConfigKeys.clear();
    }

    @AfterEach
    void deleteCreatedUsers()
    {
        for(String nickname : createdNicknames)
        {
            lookupUserId(nickname);
        }
        for(Long userId : userIdsByNickname.values())
        {
            // 딸린 줄(차단 · 친구 · 신고 · 글 · 파티 …)은 FK 의 ON DELETE CASCADE 가 같이 지운다
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
        for(String refreshToken : receivedRefreshTokens)
        {
            // 이름은 계약(contracts/platform-api.md "refresh 토큰")의 것이다. 받은 값만 지운다
            redisTemplate.delete("qm:auth:refresh:" + refreshToken);
        }
        createdNicknames.clear();
        userIdsByNickname.clear();
        receivedRefreshTokens.clear();
    }

    /** 겹치지 않는 닉네임(14자 — 2~16자 제한 안이고 앞뒤 공백이 없다). 끝나면 그 사용자가 지워지게 적어 둔다 */
    protected String newNickname()
    {
        String nickname = "n_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createdNicknames.add(nickname);
        return nickname;
    }

    /**
     * 그 닉네임(처음 지은 것)의 <b>사용자 번호</b>. 만들 때 받아 둔 값이고, 없으면 DB 에서 닉네임으로 읽는다.
     * 그런 사용자가 없으면 {@code null} 이다.
     */
    protected Long userIdOf(String nickname)
    {
        Long known = userIdsByNickname.get(nickname);
        return known != null ? known : lookupUserId(nickname);
    }

    private Long lookupUserId(String nickname)
    {
        List<Long> ids = jdbcTemplate.queryForList("select id from users where nickname = ?", Long.class, nickname);
        if(ids.isEmpty())
        {
            return null;
        }
        userIdsByNickname.putIfAbsent(nickname, ids.get(0));
        return ids.get(0);
    }

    /**
     * 아무도 아닌 사용자 번호 — identity 가 닿지 않을 만큼 큰 무작위 값이다. <b>DB 에 그 번호로 줄을 넣을 수 없다</b>(FK) —
     * "없는 사용자"(404) · Redis 에만 있는 방 멤버를 볼 때 쓴다. 줄을 넣을 사용자가 필요하면 {@link #insertUser()} 다.
     */
    protected Long unknownUserId()
    {
        return ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 2_000_000_000_000_000L);
    }

    /**
     * API 를 거치지 않고 {@code users} 에 한 줄을 바로 넣고 그 사용자 번호를 돌려준다 — SQL 로 글 · 차단 줄을 여럿 넣는 느린 테스트가
     * FK 를 채우려고 쓴다. 닉네임은 새로 짓고 끝나면 지워진다.
     */
    protected Long insertUser()
    {
        return insertUser(newNickname());
    }

    /** 그 닉네임으로 {@code users} 에 한 줄을 넣는다. {@link #newNickname()} 으로 지은 이름이어야 끝나고 지워진다 */
    protected Long insertUser(String nickname)
    {
        Long userId = jdbcTemplate.queryForObject(
                "insert into users (nickname, created_at, updated_at) values (?, now(), now()) returning id",
                Long.class, nickname);
        userIdsByNickname.put(nickname, userId);
        return userId;
    }

    /**
     * API 를 거치지 않고 게임 계정 한 줄을 바로 넣고 그 번호를 돌려준다(있으면 바꾼다). <b>LoL 은 연결이 Riot 을 긁어야 저장되므로</b>
     * (2026-09-27 소유자 결정 — {@code PUT …/game-accounts/LOL}) 게시판 · 프로필 테스트가 Riot 흐름을 매번 타지 않고 계정을 갖추려고 쓴다.
     * 사용자를 지우면 FK 의 {@code ON DELETE CASCADE} 가 같이 지운다.
     */
    protected Long insertGameAccount(Long userId, String game, String gameNickname, String tier, String mainPosition)
    {
        return jdbcTemplate.queryForObject("insert into game_accounts "
                        + "(user_id, game, game_nickname, tier, main_position, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, now(), now()) "
                        + "on conflict on constraint game_accounts_user_id_game_key do update "
                        + "set game_nickname = excluded.game_nickname, tier = excluded.tier, "
                        + "main_position = excluded.main_position, updated_at = excluded.updated_at returning id",
                Long.class, userId, game, gameNickname, tier, mainPosition);
    }

    /**
     * 그 닉네임의 사용자로 로그인해서 <b>access 쿠키</b>를 돌려준다 — 없으면 {@code users} 에 먼저 만든다. 사용자 번호는 {@link #userIdOf} 로 꺼낸다.
     * 소셜 흐름을 HTTP 로 타지 않고 앱의 {@link AccessTokenIssuer} 로 직접 찍는다(소셜 가입이 곧바로 주는 쿠키와 같은 것이다).
     */
    protected Cookie login(String nickname)
    {
        Long userId = userIdOf(nickname);
        if(userId == null)
        {
            userId = insertUser(nickname);
        }
        return new Cookie("qm_access", accessTokenIssuer.issue(userId));
    }

    /**
     * 그 닉네임의 사용자에게 <b>refresh 쿠키</b>({@code qm_refresh})를 찍어 준다 — 없으면 먼저 만든다. 값은 Redis 에 저장되고 끝나면 지워진다.
     * 소셜 로그인 · 소셜 가입이 주는 refresh 와 같은 것이다({@link RefreshTokens#issue}).
     */
    protected Cookie refreshCookieFor(String nickname)
    {
        Long userId = userIdOf(nickname);
        if(userId == null)
        {
            userId = insertUser(nickname);
        }
        String token = refreshTokens.issue(userId).orElseThrow(() -> new IllegalStateException("refresh 를 저장하지 못했다"));
        receivedRefreshTokens.add(token);
        return new Cookie(RefreshTokens.COOKIE, token);
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

    /** 소셜 가입의 201 응답에서 사용자 번호를 받아 둔다 — 그 닉네임으로 {@link #userIdOf} 가 꺼내고 끝나면 지운다 */
    protected void rememberUserId(String nickname, MvcResult result) throws Exception
    {
        if(result.getResponse().getStatus() == 201)
        {
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
            if(body.has("userId") && body.get("userId").isNumber())
            {
                userIdsByNickname.put(nickname, body.get("userId").asLong());
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
