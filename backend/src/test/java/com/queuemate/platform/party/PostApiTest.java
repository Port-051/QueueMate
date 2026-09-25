package com.queuemate.platform.party;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모집 글 쓰기 · 고치기 · 지우기 — {@code contracts/platform-api.md} "모집 글 · 목록" 의 앞 세 요청. 방의 상태가 걸리는 것은 {@link PostBoardTest},
 * 글과 방이 맞물리는 것(글 쓰기 = 방 만들기 · 입장의 검사 · 확정 한 길)은 {@link PostRoomFlowTest} 다.
 */
class PostApiTest extends PostTestSupport {

    @Test
    @DisplayName("글을 쓰면 201 과 글 한 줄이 온다 — 방이 같이 생겨 방장이 members 에 있고(memberCount 1) host 도 채워져 있다")
    void create() throws Exception
    {
        String host = newLoginId();
        Cookie cookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        putGameAccount(cookie, "LOL", json("gameNickname", "달콤한 인생#KR7", "tier", "EMERALD_4", "mainPosition", "MID"));

        createPost(cookie, lolPostBody("에메 듀오 구해요", "SUPPORT", "MID", "SUPPORT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").isNumber())
                .andExpect(jsonPath("$.hostId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.mode").value(LOL_MODE))
                .andExpect(jsonPath("$.title").value("에메 듀오 구해요"))
                .andExpect(jsonPath("$.description").value("즐겁게"))
                .andExpect(jsonPath("$.voice").value("REQUIRED"))
                .andExpect(jsonPath("$.purpose").value("RANK_UP"))
                .andExpect(jsonPath("$.conditions").isMap())
                .andExpect(jsonPath("$.conditions").isEmpty())
                // 겹친 값은 하나로 치고, 그 게임의 포지션 순서로 온다
                .andExpect(jsonPath("$.wantedPositions.length()").value(2))
                .andExpect(jsonPath("$.wantedPositions[0]").value("MID"))
                .andExpect(jsonPath("$.wantedPositions[1]").value("SUPPORT"))
                // 찾는 포지션 가운데 채워진 것의 강조(filledPositions)는 없앴다 — 주 포지션은 그 방에서 할 포지션이 아니다(2026-09-24 소유자 결정)
                .andExpect(jsonPath("$.filledPositions").doesNotExist())
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.capacity").value(5))
                .andExpect(jsonPath("$.full").value(false))
                .andExpect(jsonPath("$.host.userId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.host.nickname").value(nicknameOf(host)))
                .andExpect(jsonPath("$.host.host").value(true))
                .andExpect(jsonPath("$.host.profile.gameNickname").value("달콤한 인생#KR7"))
                .andExpect(jsonPath("$.host.profile.mainPosition").value("MID"))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.members[0].host").value(true));
    }

    @Test
    @DisplayName("PUBG 글은 conditions.perspective 가 필수이고 포지션이 없다. 게임 계정이 없는 방장의 profile 은 null 이다")
    void createPubg() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());

        createPost(cookie, postBody("PUBG", "치킨 먹자", "{\"perspective\":\"FPP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.game").value("PUBG"))
                .andExpect(jsonPath("$.conditions.perspective").value("FPP"))
                .andExpect(jsonPath("$.wantedPositions").isEmpty())
                .andExpect(jsonPath("$.host.profile").isEmpty());
    }

    @Test
    @DisplayName("검증 실패는 400 VALIDATION_FAILED 이고 어느 필드인지 details 에 온다")
    void validation() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());

        createPost(cookie, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("game")).andExpect(detailFor("title")).andExpect(detailFor("mode"))
                .andExpect(detailFor("voice")).andExpect(detailFor("purpose"));
        createPost(cookie, postBody("OVERWATCH", "x", "{}")).andExpect(status().isBadRequest()).andExpect(detailFor("game"));
        createPost(cookie, lolPostBody("가".repeat(61))).andExpect(status().isBadRequest()).andExpect(detailFor("title"));
        createPost(cookie, lolPostBody("   ")).andExpect(status().isBadRequest()).andExpect(detailFor("title"));
        createPost(cookie, lolPostBody("x").replace("즐겁게", "가".repeat(301)))
                .andExpect(status().isBadRequest()).andExpect(detailFor("description"));
        createPost(cookie, lolPostBody("x").replace(LOL_MODE, "M".repeat(31)))
                .andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        createPost(cookie, lolPostBody("x").replace("REQUIRED", "LOUD")).andExpect(status().isBadRequest()).andExpect(detailFor("voice"));
        // gameconfig 에 없는 모드 · 다른 게임의 모드(2026-09-24) — 같은 gameconfig 를 matching 이 읽는다
        createPost(cookie, lolPostBody("x").replace(LOL_MODE, UNKNOWN_MODE))
                .andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        createPost(cookie, lolPostBody("x").replace(LOL_MODE, PUBG_MODE))
                .andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        createPost(cookie, lolPostBody("x").replace(LOL_MODE, ""))
                .andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        createPost(cookie, lolPostBody("x").replace("RANK_UP", "WIN")).andExpect(status().isBadRequest()).andExpect(detailFor("purpose"));
        // 다른 게임의 포지션 · PUBG 의 포지션
        createPost(cookie, lolPostBody("x", "DUELIST")).andExpect(status().isBadRequest()).andExpect(detailFor("wantedPositions"));
        createPost(cookie, postBody("PUBG", "x", "{\"perspective\":\"TPP\"}", "MID"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("wantedPositions"));
        // conditions — PUBG 는 perspective 필수 · 모르는 값 · 모르는 키, 다른 게임은 {} 뿐
        createPost(cookie, postBody("PUBG", "x", "{}")).andExpect(status().isBadRequest()).andExpect(detailFor("conditions"));
        createPost(cookie, postBody("PUBG", "x", "null")).andExpect(status().isBadRequest()).andExpect(detailFor("conditions"));
        createPost(cookie, postBody("PUBG", "x", "{\"perspective\":\"VR\"}"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("conditions"));
        createPost(cookie, postBody("PUBG", "x", "{\"perspective\":\"TPP\",\"map\":\"ERANGEL\"}"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("conditions"));
        createPost(cookie, postBody("LOL", "x", "{\"perspective\":\"TPP\"}"))
                .andExpect(status().isBadRequest()).andExpect(detailFor("conditions"));
        mockMvc.perform(get("/api/v1/posts/not-a-number").cookie(cookie)).andExpect(status().isBadRequest());
        // 목록의 game 검증은 {@link PostPagingTest} 다 — 이제 쿼리 파라미터의 필수 · 형 변환이고 글의 본문 검증이 아니다

        // 이 앱은 gameconfig 를 읽기만 한다 — 없는 모드를 물어도 그 키가 생기지 않는다 (CLAUDE.md §11)
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey("qm:gameconfig:LOL:" + UNKNOWN_MODE))).isFalse();

        // 하나도 만들어지지 않았다 — 검증에 걸린 글이 "모집 중인 글 하나"의 자리를 차지하지 않는다
        createPost(cookie, lolPostBody("이제 된다")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("모집 중인 글이 있으면 또 쓸 수 없다(409 ALREADY_RECRUITING). 지우면 방도 같이 닫혀 곧바로 새 글을 쓸 수 있다 — IN_OTHER_ROOM 이 나지 않는다")
    void oneRecruitingPostPerHost() throws Exception
    {
        String host = newLoginId();
        Cookie cookie = signupAndLogin(host);
        Long hostId = userIdOf(host);
        Long first = createLolPost(cookie);

        createPost(cookie, postBody("VALORANT", "다른 게임이어도 안 된다", "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RECRUITING"));

        mockMvc.perform(delete("/api/v1/posts/" + first).cookie(cookie)).andExpect(status().isNoContent());
        // 확정 전에는 방과 글이 같이 끝난다(2026-09-25 소유자 결정) — 지우면 방도 닫혀 방장의 입장 표시 키가 없다. 방에서 따로 나올 필요가 없다
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + hostId)).isNull();
        Long second = createLolPost(cookie);
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + hostId)).isEqualTo(Long.toString(second));

        assertThat(jdbcTemplate.queryForObject("select count(*) from party.recruit_posts where host_id = ? and status = 'RECRUITING'",
                Integer.class, hostId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from party.recruit_posts where host_id = ?",
                Integer.class, hostId)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 사람의 글 쓰기를 여러 스레드가 동시에 보내면 하나만 201 이고 나머지는 409 다 — DB 의 부분 UNIQUE 인덱스가 지킨다")
    void concurrentCreates() throws Exception
    {
        String host = newLoginId();
        Cookie cookie = signupAndLogin(host);
        Long hostId = userIdOf(host);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try
        {
            for(int i = 0; i < threads; i++)
            {
                String body = lolPostBody("동시에 " + i);
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return createPost(cookie, body).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Integer> statuses = new ArrayList<>();
            for(Future<Integer> future : futures)
            {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
            assertThat(statuses).filteredOn(status -> status == 409).hasSize(threads - 1);
        }
        finally
        {
            pool.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from party.recruit_posts where host_id = ?",
                Integer.class, hostId)).isEqualTo(1);
    }

    @Test
    @DisplayName("고치기는 준 것만 바꾼다. 빈 문자열은 description 을 비우고, 빈 배열은 찾는 포지션을 비운다. mode 는 gameconfig 에 있는 다른 모드로만 바꾼다")
    void edit() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());
        Long postId = createLolPost(cookie, "MID", "SUPPORT");

        editPost(cookie, postId, "{\"title\":\"제목만 바꾼다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만 바꾼다"))
                .andExpect(jsonPath("$.mode").value(LOL_MODE))
                .andExpect(jsonPath("$.description").value("즐겁게"))
                .andExpect(jsonPath("$.voice").value("REQUIRED"))
                .andExpect(jsonPath("$.wantedPositions.length()").value(2));

        editPost(cookie, postId, "{\"mode\":\"" + LOL_MODE_2 + "\",\"description\":\"\",\"voice\":\"NO_VOICE\",\"purpose\":\"FUN\","
                + "\"wantedPositions\":[\"TOP\",\"MID\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만 바꾼다"))
                .andExpect(jsonPath("$.mode").value(LOL_MODE_2))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.voice").value("NO_VOICE"))
                .andExpect(jsonPath("$.purpose").value("FUN"))
                .andExpect(jsonPath("$.wantedPositions[0]").value("TOP"))
                .andExpect(jsonPath("$.wantedPositions[1]").value("MID"));
        editPost(cookie, postId, "{\"wantedPositions\":[]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wantedPositions").isEmpty());

        // 다시 읽어도 같다 — 응답을 요청에서 되짚어 만든 것이 아니다
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만 바꾼다"))
                .andExpect(jsonPath("$.voice").value("NO_VOICE"))
                .andExpect(jsonPath("$.wantedPositions").isEmpty());

        editPost(cookie, postId, "{\"title\":\"\"}").andExpect(status().isBadRequest()).andExpect(detailFor("title"));
        // mode 는 비울 수 없고(빈 문자열은 400) gameconfig 에 없는 이름도 400 이다 (2026-09-24). 안 주면 그대로다
        editPost(cookie, postId, "{\"mode\":\"\"}").andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        editPost(cookie, postId, "{\"mode\":\"" + UNKNOWN_MODE + "\"}")
                .andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        editPost(cookie, postId, "{\"mode\":\"" + PUBG_MODE + "\"}")
                .andExpect(status().isBadRequest()).andExpect(detailFor("mode"));
        editPost(cookie, postId, "{\"title\":\"모드는 안 준다\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value(LOL_MODE_2));
        editPost(cookie, postId, "{\"wantedPositions\":[\"SENTINEL\"]}")
                .andExpect(status().isBadRequest()).andExpect(detailFor("wantedPositions"));
        editPost(cookie, postId, "{\"conditions\":{\"perspective\":\"TPP\"}}")
                .andExpect(status().isBadRequest()).andExpect(detailFor("conditions"));
    }

    @Test
    @DisplayName("남의 글은 고치지도 지우지도 못한다(403 NOT_POST_HOST). 없는 글은 404 POST_NOT_FOUND 다")
    void onlyHostCanEditOrDelete() throws Exception
    {
        Cookie hostCookie = signupAndLogin(newLoginId());
        Cookie otherCookie = signupAndLogin(newLoginId());
        Long postId = createLolPost(hostCookie);

        editPost(otherCookie, postId, "{\"title\":\"내 것처럼\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_POST_HOST"));
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(otherCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_POST_HOST"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");

        long nowhere = NO_SUCH_POST;
        editPost(hostCookie, nowhere, "{\"title\":\"x\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/posts/" + nowhere).cookie(hostCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/posts/" + nowhere).cookie(hostCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("지우면 줄은 남고 만료가 된다. 두 번 지워도 204 다. 만료된 글은 고칠 수 없다(409 POST_NOT_RECRUITING)")
    void deleteExpires() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());
        Long postId = createLolPost(cookie);

        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isNoContent());

        assertThat(statusOf(postId)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("select expired_at is not null from party.recruit_posts where id = ?",
                Boolean.class, postId)).isTrue();
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.host.host").value(true));
        editPost(cookie, postId, "{\"title\":\"늦었다\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_RECRUITING"));
    }

    @Test
    @DisplayName("글은 로그인해야 하고, 상태를 바꾸는 요청은 Origin 검사를 거친다")
    void requiresLoginAndOrigin() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());

        mockMvc.perform(get("/api/v1/posts")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/posts").contentType(MediaType.APPLICATION_JSON).content(lolPostBody("x")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/posts").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(lolPostBody("x"))
                        .header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }
}
