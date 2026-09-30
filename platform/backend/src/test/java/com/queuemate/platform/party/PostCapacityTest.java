package com.queuemate.platform.party;

import com.queuemate.platform.account.domain.Game;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>게시판 방의 정원 = 그 모드의 인원</b>(2026-09-30 소유자 결정 — {@code contracts/platform-api.md} P-41. 소유자의 말 — "솔랭은 2인밖에 안 되는데 왜 5인이 있는 거야?").
 * 글을 쓸 때 gameconfig 모드 HASH 의 {@code targetPartySize} 를 읽어 글에 적고({@code recruit_posts.capacity} — V8), 입장 스크립트 · 응답의 {@code capacity} · {@code full} ·
 * 게시판 방 먼저 합류가 그 값 하나를 본다. 모드를 고치면({@code PATCH}) 다시 정한다. V8 전에 쓴 글(칸이 비었다)과 gameconfig 를 못 읽은 채 쓴 글은 5 다.
 *
 * <p>입장은 <b>진짜로</b> 거친다(HTTP → {@code PostEntryGate} → {@code enter-room.lua}). gameconfig 는 {@code ApiTestSupport} 가 심은 것({@code RANKED_SOLO} 2 ·
 * {@code RANKED_FLEX_5} 5)에 {@code NORMAL_3} 을 보탠다 — 있던 키는 건드리지 않고 없어서 심은 것만 끝나고 지운다.
 */
class PostCapacityTest extends PostTestSupport {

    /** 3인 일반 — seed 의 모양이다(포지션이 있고 티어를 안 본다) */
    private static final String LOL_NORMAL_3 = "NORMAL_3";

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

    // ---- 도우미 ----

    private org.springframework.test.web.servlet.ResultActions enter(Cookie cookie, Long postId) throws Exception
    {
        return mockMvc.perform(post("/api/v1/rooms/" + postId + "/members").cookie(cookie));
    }

    /** 새 사람 하나를 만들어 그 방에 들여보낸다 — 201 을 기대한다. 끝나면 입장 표시 키를 지우게 적어 둔다 */
    private void enterNewcomer(Long postId) throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        track(postId, userIdOf(nickname));
        enter(cookie, postId).andExpect(status().isCreated());
    }

    /** 새 사람 하나가 들어가려다 409 {@code ROOM_FULL} 로 거절당한다 — 멤버 SET 에도 입장 표시 키에도 남지 않는다 */
    private void expectFullFor(Long postId) throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        Long userId = userIdOf(nickname);
        track(postId, userId);
        enter(cookie, postId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_FULL"));
        assertThat(redisTemplate.opsForSet().members(membersKey(postId))).doesNotContain(String.valueOf(userId));
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + userId)).isNull();
    }

    private Integer storedCapacity(Long postId)
    {
        return jdbcTemplate.queryForObject("select capacity from recruit_posts where id = ?", Integer.class, postId);
    }

    // ---- 모드의 인원 ----

    @Test
    @DisplayName("솔로 랭크(targetPartySize 2) 글의 정원은 2 다 — 두 번째 사람이 들어오면 full 이고 세 번째는 409 ROOM_FULL 이다")
    void soloRankIsTwo() throws Exception
    {
        Cookie hostCookie = login(newNickname());
        Cookie viewer = login(newNickname());
        JsonNode created = body(createPost(hostCookie, lolPostBody("솔랭 듀오"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value(LOL_MODE))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.full").value(false)));
        Long postId = created.get("postId").asLong();
        assertThat(storedCapacity(postId)).isEqualTo(2);

        enterNewcomer(postId);
        JsonNode line = find(list(viewer, "LOL"), postId);
        assertThat(line.get("memberCount").asInt()).isEqualTo(2);
        assertThat(line.get("capacity").asInt()).isEqualTo(2);
        assertThat(line.get("full").asBoolean()).isTrue();

        // 전에는 정원이 늘 5 라 여기서 들어와 "3/5" 가 됐다
        expectFullFor(postId);
        assertThat(redisTemplate.opsForSet().size(membersKey(postId))).isEqualTo(2);
    }

    @Test
    @DisplayName("3인 모드(NORMAL_3)의 정원은 3 이다 — 두 사람이 더 들어오고 네 번째는 409 ROOM_FULL")
    void normalThreeIsThree() throws Exception
    {
        seedIfAbsent("qm:gameconfig:LOL:" + LOL_NORMAL_3, key -> redisTemplate.opsForHash()
                .putAll(key, Map.of("targetPartySize", "3", "positionUniqueness", "true", "tierRule", "NONE")));
        Cookie hostCookie = login(newNickname());
        Long postId = createdId(createPost(hostCookie, postBodyWithHostPosition("LOL", LOL_NORMAL_3, "3인 일반", "{}", "JUNGLE", "MID"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capacity").value(3)));
        assertThat(storedCapacity(postId)).isEqualTo(3);

        enterNewcomer(postId);
        enterNewcomer(postId);
        expectFullFor(postId);
        assertThat(redisTemplate.opsForSet().size(membersKey(postId))).isEqualTo(3);
    }

    // ---- 모드를 고치면 정원도 ----

    @Test
    @DisplayName("PATCH 로 모드를 바꾸면 정원을 다시 정한다(2 → 5 → 2) — 모드를 주지 않은 고치기는 정원을 건드리지 않는다. 바뀐 정원으로 입장을 가른다")
    void patchModeRecomputesCapacity() throws Exception
    {
        Cookie hostCookie = login(newNickname());
        Long postId = createLolPost(hostCookie);
        assertThat(storedCapacity(postId)).isEqualTo(2);

        editPost(hostCookie, postId, json("mode", LOL_MODE_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value(LOL_MODE_2))
                .andExpect(jsonPath("$.capacity").value(5));
        assertThat(storedCapacity(postId)).isEqualTo(5);

        editPost(hostCookie, postId, json("title", "제목만 고친다"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(5));
        assertThat(storedCapacity(postId)).isEqualTo(5);

        editPost(hostCookie, postId, json("mode", LOL_MODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(2));
        assertThat(storedCapacity(postId)).isEqualTo(2);

        // 다시 5 인 모드로 바꾼 뒤에는 세 번째 사람도 들어온다 — 입장 스크립트가 글의 새 정원을 받는다
        editPost(hostCookie, postId, json("mode", LOL_MODE_2)).andExpect(status().isOk());
        enterNewcomer(postId);
        enterNewcomer(postId);
        assertThat(redisTemplate.opsForSet().size(membersKey(postId))).isEqualTo(3);
    }

    // ---- 모를 때는 5 ----

    @Test
    @DisplayName("V8 전에 쓴 글(capacity 가 비었다)은 정원 5 다 — 솔로 랭크 글이어도 세 번째 사람이 들어온다(그날까지의 동작)")
    void legacyPostWithoutCapacityIsFive() throws Exception
    {
        String host = newNickname();
        login(host);
        Cookie viewer = login(newNickname());
        // SQL 로 넣은 글은 capacity 칸을 채우지 않는다 — V8 전에 쓴 글의 모양이다. 모드는 RANKED_SOLO(인원 2)다
        Long postId = insertRecruitPost(userIdOf(host), "LOL", "옛 글", java.time.Instant.now());
        assertThat(storedCapacity(postId)).isNull();

        JsonNode line = find(list(viewer, "LOL"), postId);
        assertThat(line.get("mode").asString()).isEqualTo(LOL_MODE);
        assertThat(line.get("capacity").asInt()).isEqualTo(5);
        assertThat(line.get("full").asBoolean()).isFalse();

        enterNewcomer(postId);
        enterNewcomer(postId);
        assertThat(redisTemplate.opsForSet().size(membersKey(postId))).isEqualTo(3);
        // 입장은 글에 쓰지 않는다 — 칸은 비어 있는 그대로다(모드를 주는 고치기만 채운다)
        assertThat(storedCapacity(postId)).isNull();
    }

    @Test
    @DisplayName("gameconfig 를 못 읽으면(Redis 장애) · 안 심겼으면 정원은 5 다 — 글 쓰기는 막히지 않는다(fail-open, P-16 과 같은 쪽)")
    void unknownPartySizeFallsBackToFive() throws Exception
    {
        for(StringRedisTemplate gameConfigRedis : List.of(deadRedis(), emptyRedis()))
        {
            String host = newNickname();
            login(host);
            Long hostId = userIdOf(host);
            PostService service = new PostService(postStore, matchPartyStore, roomService, gameProfileReader, blockReader, boardProperties,
                    new GameConfigReader(gameConfigRedis));

            PostResponse created = service.create(hostId, new PostCreateRequest("LOL", LOL_MODE, "모르는 인원", null, "REQUIRED",
                    Map.of(), List.of("SUPPORT"), "JUNGLE"));
            track(created.postId(), hostId);

            assertThat(created.capacity()).isEqualTo(5);
            assertThat(storedCapacity(created.postId())).isEqualTo(5);
        }
    }

    /** gameconfig 를 읽는 명령마다 연결 실패를 던지는 Redis — 죽은 Redis 다. 방 키는 진짜 Redis 로 쓴다(이 템플릿은 GameConfigReader 에만 준다) */
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

    /** 모든 명령이 "그런 키 없다" 로 답하는 Redis — gameconfig 를 심지 않은 Redis 다 */
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
}
