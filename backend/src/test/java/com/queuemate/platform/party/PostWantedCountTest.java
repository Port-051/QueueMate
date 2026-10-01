package com.queuemate.platform.party;

import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.party.dto.PostCreateRequest;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.service.BoardProperties;
import com.queuemate.platform.party.service.MatchPartyStore;
import com.queuemate.platform.party.service.PostService;
import com.queuemate.platform.party.service.PostStore;
import com.queuemate.platform.room.service.RoomService;
import com.queuemate.platform.social.service.BlockReader;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>찾는 포지션 수 ≥ 정원 − 1</b>(2026-09-30 소유자 결정 — 방장 본인을 뺀 자리 수. {@code PostValidation#enoughWantedPositions}).
 * 참가할 때 남은 찾는 포지션 가운데 하나를 골라 들어오므로, 자리가 남아 있는 한 고를 포지션이 하나는 남게 한다 — 2인이면 1개 · 3인이면 2개 · 5인이면 4개 이상.
 * <ul>
 *   <li>포지션이 있는 모드(gameconfig {@code positionUniqueness = true})에만 걸린다 — 포지션이 없는 모드(ARAM)는 그대로다</li>
 *   <li>정원은 gameconfig 모드 HASH 의 {@code targetPartySize} 이고 <b>실제로 읽었을 때만</b> 본다 — 모르면(안 심겼다 · Redis 장애 · 그 필드가 없다) 보지 않는다.
 *       모를 때 글에 적는 정원 5 로 거절하면 솔랭 듀오에도 넷을 요구하게 된다</li>
 *   <li>비어 있으면 이 규칙이 아니라 {@code "하나 이상 필요합니다"} 가 먼저다. 자리보다 많이 적는 것은 된다</li>
 *   <li>글을 쓸 때만 본다 — 글은 고칠 수 없다(2026-10-01 소유자 결정)</li>
 * </ul>
 * 거절은 400 {@code VALIDATION_FAILED} 이고 {@code details} 는 {@code "wantedPositions: 정원이 N명이면 M개 이상 필요합니다"} 한 줄이다.
 *
 * <p>모드는 seed 의 것이다({@code RANKED_FLEX_3} · {@code COMPETITIVE_TRIO} · {@code ARAM_3} — 없으면 seed 의 값으로 심고 끝나면 지운다, 있던 키는 건드리지 않는다).
 * {@link #NO_PARTY_SIZE_MODE} 만 seed 에 없는 이름이다 — 포지션은 있는데 인원이 없는 모드를 이 테스트가 만들어 "정원을 모를 때" 를 본다.
 */
class PostWantedCountTest extends PostTestSupport {

    /** 3인 자유랭크 — seed 의 {@code targetPartySize 3 · positionUniqueness true} */
    private static final String LOL_FLEX_3 = "RANKED_FLEX_3";
    /** 3인 VALORANT 경쟁 — seed 의 {@code targetPartySize 3 · positionUniqueness true} */
    private static final String VALORANT_TRIO = "COMPETITIVE_TRIO";
    /** 3인 무작위 총력전 — 포지션이 없는 모드(seed 의 {@code positionUniqueness false}) */
    private static final String LOL_ARAM_3 = "ARAM_3";
    /** seed 에 없는 이름 — 포지션은 있고 {@code targetPartySize} 가 없다. 이 테스트만 심고 지운다 */
    private static final String NO_PARTY_SIZE_MODE = "TEST_NO_PARTY_SIZE";

    @Autowired
    PostStore postStore;

    @Autowired
    MatchPartyStore matchPartyStore;

    @Autowired
    RoomService roomService;

    @Autowired
    GameProfileReader gameProfileReader;

    @Autowired
    BlockReader blockReader;

    @Autowired
    BoardProperties boardProperties;

    @BeforeEach
    void seedModes()
    {
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_FLEX_3, key -> redisTemplate.opsForHash().putAll(key, Map.of(
                "targetPartySize", "3", "positionUniqueness", "true", "tierRule", "EXIST", "tierLadder", "FLEX")));
        seedIfAbsent("qm:gameconfig:VALORANT:" + VALORANT_TRIO, key -> redisTemplate.opsForHash().putAll(key, Map.of(
                "targetPartySize", "3", "positionUniqueness", "true", "tierRule", "EXIST", "tierLadder", "COMPETITIVE")));
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_ARAM_3, key -> redisTemplate.opsForHash().putAll(key, Map.of(
                "targetPartySize", "3", "positionUniqueness", "false", "tierRule", "NONE")));
        seedIfAbsent("qm:gameconfig:LOL:" + NO_PARTY_SIZE_MODE, key -> redisTemplate.opsForHash().putAll(key, Map.of(
                "positionUniqueness", "true", "tierRule", "NONE")));
    }

    // ---- 도우미 ----

    private ResultActions create(Cookie cookie, String game, String mode, String hostPosition, String... wanted) throws Exception
    {
        return createPost(cookie, postBodyWithHostPosition(game, mode, "찾는 포지션 수", "{}", hostPosition, wanted));
    }

    /** {@code details} 에 이 한 줄이 글자 그대로 있다 */
    private static ResultMatcher detail(String line)
    {
        return jsonPath("$.details", hasItem(line));
    }

    /** 정원이 {@code capacity} 인 모드에서 찾는 포지션이 모자랄 때의 한 줄 */
    private static ResultMatcher notEnoughFor(int capacity)
    {
        return detail("wantedPositions: 정원이 " + capacity + "명이면 " + (capacity - 1) + "개 이상 필요합니다");
    }

    private int postsOf(String nickname)
    {
        return jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ?", Integer.class, userIdOf(nickname));
    }

    private List<String> storedWanted(Long postId)
    {
        return jdbcTemplate.queryForList("select position from recruit_post_positions where post_id = ? order by position",
                String.class, postId);
    }

    // ---- 글 쓰기 ----

    @Test
    @DisplayName("3인 모드는 찾는 포지션이 둘 이상이다 — 하나면 400 '정원이 3명이면 2개 이상 필요합니다' 이고 글이 생기지 않는다. 둘이면 201 (LoL 자유랭크 · VALORANT 경쟁)")
    void threePersonNeedsTwo() throws Exception
    {
        String first = newNickname();
        Cookie cookie = login(first);

        create(cookie, "LOL", LOL_FLEX_3, "JUNGLE", "MID").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(notEnoughFor(3))
                .andExpect(jsonPath("$.details.length()").value(1));
        create(cookie, "VALORANT", VALORANT_TRIO, "CONTROLLER", "DUELIST").andExpect(status().isBadRequest())
                .andExpect(notEnoughFor(3)).andExpect(jsonPath("$.details.length()").value(1));
        assertThat(postsOf(first)).isZero();

        Long lol = createdId(create(cookie, "LOL", LOL_FLEX_3, "JUNGLE", "MID", "SUPPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value(LOL_FLEX_3))
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.wantedPositions.length()").value(2)));
        assertThat(storedWanted(lol)).containsExactly("MID", "SUPPORT");
        create(login(newNickname()), "VALORANT", VALORANT_TRIO, "CONTROLLER", "DUELIST", "SENTINEL").andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.wantedPositions.length()").value(2));
    }

    @Test
    @DisplayName("2인 모드는 하나면 된다(LoL 솔랭 · VALORANT 듀오) — 자리보다 많이 적는 것도 된다(솔랭 듀오에 '정글 또는 서포터')")
    void twoPersonNeedsOne() throws Exception
    {
        create(login(newNickname()), "LOL", LOL_MODE, "JUNGLE", "SUPPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.wantedPositions.length()").value(1));
        create(login(newNickname()), "VALORANT", VALORANT_MODE, "CONTROLLER", "DUELIST").andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(2));
        create(login(newNickname()), "LOL", LOL_MODE, "JUNGLE", "TOP", "MID", "ADC", "SUPPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.wantedPositions.length()").value(4));
    }

    @Test
    @DisplayName("5인 모드는 넷이다 — 셋이면 400 '정원이 5명이면 4개 이상 필요합니다', 넷(방장을 뺀 전부)이면 201")
    void fivePersonNeedsFour() throws Exception
    {
        String first = newNickname();
        Cookie cookie = login(first);

        create(cookie, "LOL", LOL_MODE_2, "JUNGLE", "TOP", "MID", "SUPPORT").andExpect(status().isBadRequest())
                .andExpect(notEnoughFor(5)).andExpect(jsonPath("$.details.length()").value(1));
        assertThat(postsOf(first)).isZero();

        create(cookie, "LOL", LOL_MODE_2, "JUNGLE", "TOP", "MID", "ADC", "SUPPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.hostPosition").value("JUNGLE"))
                .andExpect(jsonPath("$.wantedPositions.length()").value(4));
    }

    @Test
    @DisplayName("다른 거절과의 순서 — 비어 있으면 '하나 이상 필요합니다' 가 먼저 · 이름이 틀리면 그 거절이 먼저 · 모자라면 방장 포지션보다 먼저다. 겹친 값은 하나로 센다")
    void orderAgainstTheOtherRules() throws Exception
    {
        String first = newNickname();
        Cookie cookie = login(first);

        // 비어 있으면 이 규칙이 아니다 — 원래 거절 한 줄만 나간다
        create(cookie, "LOL", LOL_FLEX_3, "JUNGLE").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다")).andExpect(jsonPath("$.details.length()").value(1));
        // 그 게임의 포지션이 아니면 그 거절이다(수를 세기 전에 이름을 본다)
        create(cookie, "LOL", LOL_FLEX_3, "JUNGLE", "SENTINEL").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: LOL 의 포지션이 아닙니다")).andExpect(jsonPath("$.details.length()").value(1));
        // 방장 포지션이 없어도 수가 모자란 것이 먼저 나간다
        create(cookie, "LOL", LOL_FLEX_3, null, "MID").andExpect(status().isBadRequest())
                .andExpect(notEnoughFor(3)).andExpect(jsonPath("$.details.length()").value(1));
        // 같은 포지션을 두 번 적어도 하나다
        create(cookie, "LOL", LOL_FLEX_3, "JUNGLE", "MID", "MID").andExpect(status().isBadRequest())
                .andExpect(notEnoughFor(3));
        // 수가 넉넉하면 그다음 규칙(방장 포지션)이 나온다
        create(cookie, "LOL", LOL_FLEX_3, null, "MID", "SUPPORT").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 필요합니다")).andExpect(jsonPath("$.details.length()").value(1));
        assertThat(postsOf(first)).isZero();
    }

    @Test
    @DisplayName("포지션이 없는 모드(ARAM)에는 걸리지 않는다 — 찾는 포지션 · 방장 포지션 없이 3인 글이 된다")
    void noPositionModeIsUnaffected() throws Exception
    {
        create(login(newNickname()), "LOL", LOL_ARAM_3, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(3))
                .andExpect(jsonPath("$.wantedPositions").isEmpty())
                .andExpect(jsonPath("$.hostPosition").isEmpty());
    }

    // ---- 정원을 모를 때 ----

    @Test
    @DisplayName("gameconfig 에 그 모드의 인원(targetPartySize)이 없으면 보지 않는다 — 포지션이 있는 모드여도 하나로 201 · 정원은 5 로 적힌다(5 로 거절하지 않는다)")
    void skippedWhenThePartySizeIsUnknown() throws Exception
    {
        create(login(newNickname()), "LOL", NO_PARTY_SIZE_MODE, "JUNGLE", "MID").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value(NO_PARTY_SIZE_MODE))
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.wantedPositions.length()").value(1));
        // 방장 포지션은 그대로 필수다 — 모드에 포지션이 있는지는 안다(인원만 모른다)
        create(login(newNickname()), "LOL", NO_PARTY_SIZE_MODE, null, "MID").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 필요합니다"));
    }

    @Test
    @DisplayName("gameconfig 가 안 심겼거나 Redis 를 못 읽으면 보지 않는다(fail-open) — 3인 · 5인 모드에 찾는 포지션 하나로도 글이 된다")
    void skippedWhenGameConfigIsUnreadable()
    {
        for(StringRedisTemplate gameConfigRedis : List.of(emptyRedis(), deadRedis()))
        {
            PostService service = new PostService(postStore, matchPartyStore, roomService, gameProfileReader, blockReader, boardProperties,
                    new GameConfigReader(gameConfigRedis));

            for(String mode : List.of(LOL_FLEX_3, LOL_MODE_2))
            {
                String nickname = newNickname();
                login(nickname);
                Long me = userIdOf(nickname);
                PostResponse created = service.create(me, new PostCreateRequest("LOL", mode, "모르는 정원", null, "REQUIRED",
                        Map.of(), List.of("MID"), "JUNGLE"));
                track(created.postId(), me);

                assertThat(created.wantedPositions()).containsExactly("MID");
                assertThat(created.capacity()).isEqualTo(5);
            }
        }
    }

    /** 모든 명령이 "그런 키 없다" 로 답하는 Redis — gameconfig 를 심지 않은 Redis 다({@code PostCapacityTest} 와 같은 모양) */
    private static StringRedisTemplate emptyRedis()
    {
        return new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline)
            {
                return null;
            }
        };
    }

    /** 모든 명령에 연결 실패를 던지는 Redis — gameconfig 를 읽는 쪽만 죽인다(방 키는 앱의 진짜 Redis 다) */
    private static StringRedisTemplate deadRedis()
    {
        return new StringRedisTemplate() {
            @Override
            public <T> T execute(RedisCallback<T> action, boolean exposeConnection, boolean pipeline)
            {
                throw new RedisConnectionFailureException("테스트 — Redis 가 죽었다");
            }
        };
    }
}
