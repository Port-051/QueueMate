package com.queuemate.platform.party;

import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.error.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>모집 글의 방장 포지션</b>(2026-09-30 소유자 결정 — {@code contracts/platform-api.md} "모집 글 · 목록" 의 {@code hostPosition} · P-38).
 * <ul>
 *   <li>포지션이 있는 모드(gameconfig 모드 HASH 의 {@code positionUniqueness = true} — LoL {@code RANKED_*} · {@code NORMAL_*} · VALORANT)면 <b>필수</b>다</li>
 *   <li>포지션이 없는 모드(LoL {@code ARAM_*})와 PUBG 는 <b>보내면 400</b> 이다 — 조용히 버리지 않는다</li>
 *   <li>그 게임의 포지션 이름이어야 하고 <b>찾는 포지션({@code wantedPositions})과 겹치면 안 된다</b></li>
 *   <li><b>찾는 포지션도 모드를 따른다</b>(같은 날 소유자 결정 — 포지션이 있는 모드면 하나 이상 필수 · 없는 모드는 빈 배열만. "누구든" 글을 없앴다)</li>
 *   <li>gameconfig 를 못 읽거나 안 심겼으면 요구하지도 거절하지도 않는다(fail-open — P-16 과 같다). 이름 · 겹침은 그래도 본다</li>
 * </ul>
 * 글 한 줄의 칸이다 — 카드({@code {userId, nickname, host, profile}})의 모양은 그대로다.
 *
 * <p>모드는 seed 의 것이다. {@code NORMAL_5} · {@code ARAM_5} 는 없으면 seed 의 값으로 심고 끝나면 지운다(있던 키는 건드리지 않는다 — {@link #seedModes}).
 */
class PostHostPositionTest extends PostTestSupport {

    /** 티어를 안 보고 포지션이 있는 LoL 모드 */
    private static final String LOL_NORMAL = "NORMAL_5";
    /** 포지션이 없는 LoL 모드(무작위 총력전) — seed 의 {@code positionUniqueness false} */
    private static final String LOL_ARAM = "ARAM_5";

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
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_NORMAL, key -> redisTemplate.opsForHash()
                .putAll(key, Map.of("targetPartySize", "5", "positionUniqueness", "true", "tierRule", "NONE")));
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_ARAM, key -> redisTemplate.opsForHash()
                .putAll(key, Map.of("targetPartySize", "5", "positionUniqueness", "false", "tierRule", "NONE")));
    }

    // ---- 도우미 ----

    private ResultActions create(Cookie cookie, String game, String mode, String hostPosition, String... wanted) throws Exception
    {
        String conditions = "PUBG".equals(game) ? "{\"perspective\":\"TPP\"}" : "{}";
        return createPost(cookie, postBodyWithHostPosition(game, mode, "방장 포지션", conditions, hostPosition, wanted));
    }

    /** {@code details} 에 이 한 줄이 글자 그대로 있다 */
    private static ResultMatcher detail(String line)
    {
        return jsonPath("$.details", hasItem(line));
    }

    private String storedHostPosition(Long postId)
    {
        return jdbcTemplate.queryForObject("select host_position from recruit_posts where id = ?", String.class, postId);
    }

    // ---- 글 쓰기 ----

    @Test
    @DisplayName("방장 포지션은 글에 저장되고 쓰기 · 단건 · 목록의 글 한 줄에 hostPosition 으로 나간다 — 카드의 모양은 그대로다")
    void storedAndReturned() throws Exception
    {
        String host = newNickname();
        Cookie cookie = login(host);
        insertGameAccount(userIdOf(host), "LOL", "달콤한 인생#KR7", "EMERALD_4");

        Long postId = createdId(create(cookie, "LOL", LOL_MODE, "MID", "TOP", "SUPPORT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostPosition").value("MID"))
                .andExpect(jsonPath("$.wantedPositions[0]").value("TOP"))
                .andExpect(jsonPath("$.wantedPositions[1]").value("SUPPORT"))
                // 카드에 붙지 않는다 — 카드는 {userId, nickname, host, profile} 그대로이고 게임 프로필에도 포지션이 없다(P-35)
                .andExpect(jsonPath("$.host.hostPosition").doesNotExist())
                .andExpect(jsonPath("$.host.profile.mainPosition").doesNotExist())
                .andExpect(jsonPath("$.members[0].hostPosition").doesNotExist()));

        assertThat(storedHostPosition(postId)).isEqualTo("MID");
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").value("MID"));
        JsonNode line = find(list(login(newNickname()), "LOL"), postId);
        assertThat(line).isNotNull();
        assertThat(line.get("hostPosition").asString()).isEqualTo("MID");
        assertThat(line.get("host").has("hostPosition")).isFalse();
    }

    @Test
    @DisplayName("포지션이 있는 모드(LoL 랭크 · 일반 · VALORANT)는 필수다 — 없으면 400 'hostPosition: 필요합니다' 이고 글이 생기지 않는다")
    void requiredWhenTheModeHasPositions() throws Exception
    {
        String first = newNickname();
        Cookie cookie = login(first);

        create(cookie, "LOL", LOL_MODE, null, "MID").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detail("hostPosition: 필요합니다"));
        create(cookie, "LOL", LOL_NORMAL, null, "TOP").andExpect(status().isBadRequest()).andExpect(detail("hostPosition: 필요합니다"));
        create(cookie, "VALORANT", VALORANT_MODE, null, "SENTINEL").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 필요합니다"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ?", Integer.class, userIdOf(first)))
                .isZero();

        create(cookie, "LOL", LOL_NORMAL, "ADC", "SUPPORT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value(LOL_NORMAL))
                .andExpect(jsonPath("$.hostPosition").value("ADC"));
        create(login(newNickname()), "VALORANT", VALORANT_MODE, "DUELIST", "SENTINEL").andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostPosition").value("DUELIST"));
    }

    @Test
    @DisplayName("포지션이 없는 모드(ARAM)와 PUBG 는 보내면 400 이다 — 조용히 버리지 않는다. 안 보내면 hostPosition 은 null 이다")
    void rejectedWhenTheModeHasNoPositions() throws Exception
    {
        Cookie cookie = login(newNickname());

        create(cookie, "LOL", LOL_ARAM, "MID").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 포지션이 없는 모드입니다"));
        create(cookie, "PUBG", PUBG_MODE, "MID").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: PUBG 에는 포지션이 없습니다"));

        Long aram = createdId(create(cookie, "LOL", LOL_ARAM, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostPosition").isEmpty()));
        assertThat(storedHostPosition(aram)).isNull();
        create(login(newNickname()), "PUBG", PUBG_MODE, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.hostPosition").isEmpty());
    }

    @Test
    @DisplayName("그 게임의 포지션 이름이어야 하고(대소문자 · 빈 문자열 · 다른 게임의 이름은 400) 찾는 포지션과 겹치면 400 이다")
    void mustBeAPositionOfTheGameAndNotWanted() throws Exception
    {
        Cookie cookie = login(newNickname());

        create(cookie, "LOL", LOL_MODE, "DUELIST", "TOP").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: LOL 의 포지션이 아닙니다"));
        create(cookie, "LOL", LOL_MODE, "mid", "TOP").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: LOL 의 포지션이 아닙니다"));
        create(cookie, "LOL", LOL_MODE, "", "TOP").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: LOL 의 포지션이 아닙니다"));
        create(cookie, "VALORANT", VALORANT_MODE, "MID", "SENTINEL").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: VALORANT 의 포지션이 아닙니다"));
        create(cookie, "LOL", LOL_MODE, "MID", "MID", "TOP").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 찾는 포지션(wantedPositions)과 겹칠 수 없습니다"));

        // 다른 칸의 거절이 먼저다 — 모드(서비스 앞머리) · 찾는 포지션(방장 포지션은 그것과 겹치는지를 봐야 해서 그 뒤에 본다)
        create(cookie, "LOL", UNKNOWN_MODE, null).andExpect(status().isBadRequest())
                .andExpect(detailFor("mode")).andExpect(jsonPath("$.details.length()").value(1));
        create(cookie, "LOL", LOL_MODE, null, "SENTINEL").andExpect(status().isBadRequest())
                .andExpect(detailFor("wantedPositions")).andExpect(jsonPath("$.details.length()").value(1));

        create(cookie, "LOL", LOL_MODE, "MID", "TOP").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("찾는 포지션도 모드를 따른다 — 포지션이 있는 모드는 하나 이상 필수(빈 배열 · 칸 없음은 400), 없는 모드(ARAM · PUBG)는 빈 배열만")
    void wantedPositionsFollowTheMode() throws Exception
    {
        String first = newNickname();
        Cookie cookie = login(first);

        create(cookie, "LOL", LOL_MODE, "MID").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다")).andExpect(jsonPath("$.details.length()").value(1));
        create(cookie, "LOL", LOL_NORMAL, "MID").andExpect(status().isBadRequest()).andExpect(detail("wantedPositions: 하나 이상 필요합니다"));
        create(cookie, "VALORANT", VALORANT_MODE, "DUELIST").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다"));
        // 칸을 아예 안 보내도 같다(null 은 빈 배열이다)
        createPost(cookie, "{\"game\":\"LOL\",\"mode\":\"" + LOL_MODE + "\",\"title\":\"x\",\"voice\":\"REQUIRED\",\"conditions\":{},"
                + "\"hostPosition\":\"MID\"}").andExpect(status().isBadRequest()).andExpect(detail("wantedPositions: 하나 이상 필요합니다"));
        // 둘 다 없으면 찾는 포지션이 먼저다(방장 포지션은 찾는 포지션과 겹치는지를 봐야 해서 그 뒤다)
        create(cookie, "LOL", LOL_MODE, null).andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다")).andExpect(jsonPath("$.details.length()").value(1));

        create(cookie, "LOL", LOL_ARAM, null, "TOP").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 포지션이 없는 모드입니다"));
        create(cookie, "PUBG", PUBG_MODE, null, "MID").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: PUBG 에는 포지션이 없습니다"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ?", Integer.class,
                userIdOf(first))).isZero();

        create(cookie, "LOL", LOL_MODE, "MID", "TOP").andExpect(status().isCreated())
                .andExpect(jsonPath("$.wantedPositions[0]").value("TOP"));
        create(login(newNickname()), "LOL", LOL_ARAM, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.wantedPositions").isEmpty());
    }

    // ---- 고치기 ----

    @Test
    @DisplayName("고치기 — null 은 그대로 · 준 값은 고친 뒤의 모양으로 본다. 포지션이 없는 모드로 바꾸면 방장 · 찾는 포지션이 비워지고, 있는 모드로 돌아가면 다시 필수다")
    void edit() throws Exception
    {
        Cookie cookie = login(newNickname());
        Long postId = createdId(create(cookie, "LOL", LOL_MODE, "MID", "TOP").andExpect(status().isCreated()));

        editPost(cookie, postId, "{\"title\":\"제목만 바꾼다\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").value("MID"));
        editPost(cookie, postId, "{\"hostPosition\":\"ADC\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").value("ADC"))
                .andExpect(jsonPath("$.wantedPositions[0]").value("TOP"));

        // 거절 — 겹침(찾는 포지션만 줘도 고친 뒤의 모양을 본다) · 이름 · 빈 문자열(비우는 길이 아니다) · 포지션이 없는 모드에 값 · 포지션이 있는 모드의 빈 찾는 포지션
        editPost(cookie, postId, "{\"wantedPositions\":[\"ADC\",\"TOP\"]}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 찾는 포지션(wantedPositions)과 겹칠 수 없습니다"));
        editPost(cookie, postId, "{\"hostPosition\":\"DUELIST\"}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: LOL 의 포지션이 아닙니다"));
        editPost(cookie, postId, "{\"hostPosition\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: LOL 의 포지션이 아닙니다"));
        editPost(cookie, postId, "{\"mode\":\"" + LOL_ARAM + "\",\"hostPosition\":\"MID\"}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 포지션이 없는 모드입니다"));
        editPost(cookie, postId, "{\"mode\":\"" + LOL_ARAM + "\",\"wantedPositions\":[\"TOP\"]}").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 포지션이 없는 모드입니다"));
        editPost(cookie, postId, "{\"wantedPositions\":[]}").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다"));
        // 거절된 고치기는 아무것도 바꾸지 않았다
        assertThat(storedHostPosition(postId)).isEqualTo("ADC");
        assertThat(jdbcTemplate.queryForObject("select mode from recruit_posts where id = ?", String.class, postId)).isEqualTo(LOL_MODE);
        assertThat(jdbcTemplate.queryForList("select position from recruit_post_positions where post_id = ?", String.class, postId))
                .containsExactly("TOP");

        // 포지션이 없는 모드로 바꾸면 적혀 있던 방장 포지션과 찾는 포지션이 같이 비워진다
        editPost(cookie, postId, "{\"mode\":\"" + LOL_ARAM + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value(LOL_ARAM))
                .andExpect(jsonPath("$.hostPosition").isEmpty())
                .andExpect(jsonPath("$.wantedPositions").isEmpty());
        assertThat(storedHostPosition(postId)).isNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_post_positions where post_id = ?", Integer.class, postId))
                .isZero();
        editPost(cookie, postId, "{\"hostPosition\":\"MID\"}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 포지션이 없는 모드입니다"));
        editPost(cookie, postId, "{\"wantedPositions\":[\"TOP\"]}").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 포지션이 없는 모드입니다"));
        editPost(cookie, postId, "{\"wantedPositions\":[]}").andExpect(status().isOk());

        // 포지션이 있는 모드로 돌아가려면 찾는 포지션 · 방장 포지션을 같이 줘야 한다
        editPost(cookie, postId, "{\"mode\":\"" + LOL_MODE + "\"}").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다"));
        editPost(cookie, postId, "{\"mode\":\"" + LOL_MODE + "\",\"wantedPositions\":[\"TOP\"]}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 필요합니다"));
        editPost(cookie, postId, "{\"mode\":\"" + LOL_MODE + "\",\"hostPosition\":\"SUPPORT\",\"wantedPositions\":[\"TOP\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value(LOL_MODE))
                .andExpect(jsonPath("$.hostPosition").value("SUPPORT"))
                .andExpect(jsonPath("$.wantedPositions[0]").value("TOP"));
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").value("SUPPORT"));
    }

    @Test
    @DisplayName("그 전에 쓴 글(방장 포지션도 찾는 포지션도 없다)은 hostPosition 이 null 이고, 포지션에 닿지 않는 고치기는 된다 — 포지션을 고치면 그때 둘 다 필수다")
    void postsWrittenBefore() throws Exception
    {
        String host = newNickname();
        Cookie cookie = login(host);
        // SQL 로 넣는 글에는 host_position 도 찾는 포지션도 없다 — 2026-09-30 전에 쓴 글과 같은 모양이다(모드는 포지션이 있는 LOL_MODE)
        Long postId = insertRecruitPost(userIdOf(host), "LOL", "옛 글", Instant.now());

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").isEmpty());
        editPost(cookie, postId, "{\"title\":\"제목만\",\"voice\":\"NO_VOICE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").isEmpty());
        editPost(cookie, postId, "{\"wantedPositions\":[\"TOP\"]}").andExpect(status().isBadRequest())
                .andExpect(detail("hostPosition: 필요합니다"));
        editPost(cookie, postId, "{\"hostPosition\":\"MID\"}").andExpect(status().isBadRequest())
                .andExpect(detail("wantedPositions: 하나 이상 필요합니다"));
        editPost(cookie, postId, "{\"hostPosition\":\"MID\",\"wantedPositions\":[\"TOP\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.hostPosition").value("MID"))
                .andExpect(jsonPath("$.wantedPositions[0]").value("TOP"));
    }

    // ---- fail-open ----

    @Test
    @DisplayName("gameconfig 가 안 심겼거나 Redis 를 못 읽으면 방장 · 찾는 포지션을 요구하지도 거절하지도 않는다(fail-open) — 이름 · 겹침은 그래도 본다")
    void failsOpenWhenGameConfigIsUnreadable() throws Exception
    {
        for(StringRedisTemplate gameConfigRedis : List.of(emptyRedis(), deadRedis()))
        {
            PostService service = new PostService(postStore, matchPartyStore, roomService, gameProfileReader, blockReader, boardProperties,
                    new GameConfigReader(gameConfigRedis));

            // 포지션이 있는 모드인데 없어도 된다
            PostResponse without = createThrough(service, LOL_MODE, null, "TOP");
            assertThat(without.hostPosition()).isNull();
            // 포지션이 없는 모드인데 있어도 된다 — 모드에 포지션이 있는지 모른다(찾는 포지션도 같다)
            PostResponse aram = createThrough(service, LOL_ARAM, "MID", "TOP");
            assertThat(aram.hostPosition()).isEqualTo("MID");
            assertThat(aram.wantedPositions()).containsExactly("TOP");
            // 포지션이 있는 모드인데 찾는 포지션이 비어도 된다
            PostResponse open = createThrough(service, LOL_MODE, "MID");
            assertThat(open.wantedPositions()).isEmpty();

            String nickname = newNickname();
            login(nickname);
            Long me = userIdOf(nickname);
            assertValidationFailed(() -> service.create(me, request(LOL_MODE, "DUELIST")), "hostPosition: LOL 의 포지션이 아닙니다");
            assertValidationFailed(() -> service.create(me, request(LOL_MODE, "MID", "MID")),
                    "hostPosition: 찾는 포지션(wantedPositions)과 겹칠 수 없습니다");
            // PUBG 는 gameconfig 없이도 포지션이 없다는 것을 안다
            assertValidationFailed(() -> service.create(me, new PostCreateRequest("PUBG", PUBG_MODE, "치킨", null, "REQUIRED",
                    Map.of("perspective", "TPP"), List.of(), "MID")), "hostPosition: PUBG 에는 포지션이 없습니다");
        }
    }

    /** 새 사용자로 서비스를 거쳐 글을 쓴다. 같이 생긴 방은 끝나면 지워지게 적어 둔다 */
    private PostResponse createThrough(PostService service, String mode, String hostPosition, String... wanted)
    {
        String nickname = newNickname();
        login(nickname);
        Long me = userIdOf(nickname);
        PostResponse created = service.create(me, request(mode, hostPosition, wanted));
        track(created.postId(), me);
        return created;
    }

    private static PostCreateRequest request(String mode, String hostPosition, String... wanted)
    {
        return new PostCreateRequest("LOL", mode, "방장 포지션", null, "REQUIRED", Map.of(), List.of(wanted), hostPosition);
    }

    private static void assertValidationFailed(Runnable call, String detail)
    {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getDetails()).containsExactly(detail);
        });
    }

    /** 모든 명령이 "그런 키 없다" 로 답하는 Redis — gameconfig 를 붓지 않은 상태다({@code GameConfigReaderTest} 와 같은 모양) */
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
