package com.queuemate.platform.party;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모집 글 쓰기 · 지우기 — {@code contracts/platform-api.md} "모집 글 · 목록" 의 앞 요청들. 글 고치기({@code PATCH})는 2026-10-01 소유자 결정으로 없어졌다 —
 * 그 경로가 405 인 것을 여기서 본다. 방의 상태가 걸리는 것은 {@link PostBoardTest},
 * 글과 방이 맞물리는 것(글 쓰기 = 방 만들기 · 입장의 검사 · 확정 한 길)은 {@link PostRoomFlowTest} 다.
 */
class PostApiTest extends PostTestSupport {

    @Test
    @DisplayName("글을 쓰면 201 과 글 한 줄이 온다 — 방이 같이 생겨 방장이 members 에 있고(memberCount 1) host 도 채워져 있다")
    void create() throws Exception
    {
        String host = newNickname();
        Cookie cookie = login(host);
        Long hostId = userIdOf(host);
        insertGameAccount(hostId, "LOL", "달콤한 인생#KR7", "EMERALD_4");

        createPost(cookie, lolPostBody("에메 듀오 구해요", "SUPPORT", "MID", "SUPPORT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").isNumber())
                .andExpect(jsonPath("$.hostId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.mode").value(LOL_MODE))
                .andExpect(jsonPath("$.title").value("에메 듀오 구해요"))
                .andExpect(jsonPath("$.description").value("즐겁게"))
                .andExpect(jsonPath("$.voice").value("REQUIRED"))
                // purpose 칸은 없다(2026-09-27 소유자 결정 — P-29)
                .andExpect(jsonPath("$.purpose").doesNotExist())
                .andExpect(jsonPath("$.conditions").isMap())
                .andExpect(jsonPath("$.conditions").isEmpty())
                // 겹친 값은 하나로 치고, 그 게임의 포지션 순서로 온다
                .andExpect(jsonPath("$.wantedPositions.length()").value(2))
                .andExpect(jsonPath("$.wantedPositions[0]").value("MID"))
                .andExpect(jsonPath("$.wantedPositions[1]").value("SUPPORT"))
                // 방장 자신의 포지션(2026-09-30 — P-38). 도우미가 찾는 포지션과 겹치지 않게 골라 보냈다. 규칙은 PostHostPositionTest 가 본다
                .andExpect(jsonPath("$.hostPosition").value("JUNGLE"))
                // 찾는 포지션 가운데 채워진 것의 강조(filledPositions)는 없앴다 — 주 포지션은 그 방에서 할 포지션이 아니다(2026-09-24 소유자 결정)
                .andExpect(jsonPath("$.filledPositions").doesNotExist())
                .andExpect(jsonPath("$.status").value("RECRUITING"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.memberCount").value(1))
                // 정원은 그 모드의 인원이다 — RANKED_SOLO 는 2(2026-09-30 소유자 결정 — P-41. 그 전에는 늘 5). 규칙은 PostCapacityTest 가 본다
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.full").value(false))
                .andExpect(jsonPath("$.host.userId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.host.nickname").value(host))
                .andExpect(jsonPath("$.host.host").value(true))
                .andExpect(jsonPath("$.host.profile.gameNickname").value("달콤한 인생#KR7"))
                // 사람별 포지션은 카드에 없다 — 게임 계정의 주 포지션을 없앴다(2026-09-29 소유자 결정 — P-35)
                .andExpect(jsonPath("$.host.profile.mainPosition").doesNotExist())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].userId").value(equalTo(hostId), Long.class))
                .andExpect(jsonPath("$.members[0].host").value(true));
    }

    @Test
    @DisplayName("PUBG 글은 conditions.perspective 가 필수이고 포지션이 없다. 게임 계정이 없는 방장의 profile 은 null 이다")
    void createPubg() throws Exception
    {
        Cookie cookie = login(newNickname());

        createPost(cookie, postBody("PUBG", "치킨 먹자", "{\"perspective\":\"FPP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.game").value("PUBG"))
                .andExpect(jsonPath("$.conditions.perspective").value("FPP"))
                .andExpect(jsonPath("$.wantedPositions").isEmpty())
                .andExpect(jsonPath("$.hostPosition").isEmpty())
                .andExpect(jsonPath("$.host.profile").isEmpty());
    }

    @Test
    @DisplayName("검증 실패는 400 VALIDATION_FAILED 이고 어느 필드인지 details 에 온다")
    void validation() throws Exception
    {
        Cookie cookie = login(newNickname());

        createPost(cookie, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("game")).andExpect(detailFor("title")).andExpect(detailFor("mode"))
                .andExpect(detailFor("voice"));
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
        String host = newNickname();
        Cookie cookie = login(host);
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

        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ? and status = 'RECRUITING'",
                Integer.class, hostId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ?",
                Integer.class, hostId)).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 사람의 글 쓰기를 여러 스레드가 동시에 보내면 하나만 201 이고 나머지는 409 다 — DB 의 부분 UNIQUE 인덱스가 지킨다")
    void concurrentCreates() throws Exception
    {
        String host = newNickname();
        Cookie cookie = login(host);
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
        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ?",
                Integer.class, hostId)).isEqualTo(1);
    }

    @Test
    @DisplayName("글은 고칠 수 없다(2026-10-01 소유자 결정) — PATCH 는 방장이 보내도 405 METHOD_NOT_ALLOWED 이고 글 · 방이 그대로다")
    void editIsGone() throws Exception
    {
        String host = newNickname();
        Cookie cookie = login(host);
        Long hostId = userIdOf(host);
        Long postId = createLolPost(cookie, "MID", "SUPPORT");

        // 고치기가 있던 때 200 이던 본문 그대로다 — 그 경로에는 GET · DELETE 만 남았다
        mockMvc.perform(patch("/api/v1/posts/" + postId).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목을 바꾼다\",\"voice\":\"NO_VOICE\",\"hostPosition\":\"TOP\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("같이 하실 분"))
                .andExpect(jsonPath("$.voice").value("REQUIRED"))
                .andExpect(jsonPath("$.hostPosition").value("JUNGLE"))
                .andExpect(jsonPath("$.wantedPositions.length()").value(2));
        // 방의 방장 값 · 찾는 포지션도 글을 쓸 때 그대로다
        assertThat(positionOf(postId, hostId)).isEqualTo("JUNGLE");
        assertThat(redisTemplate.opsForSet().members(needsKey(postId))).containsExactlyInAnyOrder("MID", "SUPPORT");
    }

    @Test
    @DisplayName("빠른매치 입장 허용 / 금지(allowAutoJoin)는 글을 쓸 때 필수다 — 없거나 null 이면 400 \"allowAutoJoin: 필요합니다\" 이고 글도 방도 안 생긴다. "
            + "고른 값이 쓰기 · 단건 · 목록의 글 한 줄에 실린다(2026-10-02 — P-50)")
    void allowAutoJoinIsRequiredAndEchoed() throws Exception
    {
        String host = newNickname();
        Cookie cookie = login(host);
        Long hostId = userIdOf(host);
        String body = lolPostBody("빠른매치 고르기");

        createPost(cookie, body.replace(",\"allowAutoJoin\":true", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value("allowAutoJoin: 필요합니다"))
                .andExpect(jsonPath("$.details.length()").value(1));
        createPost(cookie, body.replace("\"allowAutoJoin\":true", "\"allowAutoJoin\":null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("allowAutoJoin: 필요합니다"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from recruit_posts where host_id = ?", Integer.class, hostId)).isZero();
        assertThat(redisTemplate.opsForValue().get("qm:user:active-room:" + hostId)).isNull();

        Long forbidden = createdId(createPost(cookie, forbidAutoJoin(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allowAutoJoin").value(false)));
        mockMvc.perform(get("/api/v1/posts/" + forbidden).cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowAutoJoin").value(false));
        assertThat(find(list(cookie, "LOL"), forbidden).get("allowAutoJoin").asBoolean()).isFalse();
        assertThat(jdbcTemplate.queryForObject("select allow_auto_join from recruit_posts where id = ?", Boolean.class, forbidden)).isFalse();

        // 허용한 글 — 지우고 다시 쓴다(모집 중인 글은 한 사람에 하나다)
        mockMvc.perform(delete("/api/v1/posts/" + forbidden).cookie(cookie)).andExpect(status().isNoContent());
        Long allowed = createdId(createPost(cookie, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allowAutoJoin").value(true)));
        assertThat(find(list(cookie, "LOL"), allowed).get("allowAutoJoin").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("남의 글은 지우지 못한다(403 NOT_POST_HOST). 없는 글은 404 POST_NOT_FOUND 다")
    void onlyHostCanDelete() throws Exception
    {
        Cookie hostCookie = login(newNickname());
        Cookie otherCookie = login(newNickname());
        Long postId = createLolPost(hostCookie);

        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(otherCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_POST_HOST"));
        assertThat(statusOf(postId)).isEqualTo("RECRUITING");

        long nowhere = NO_SUCH_POST;
        mockMvc.perform(delete("/api/v1/posts/" + nowhere).cookie(hostCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/posts/" + nowhere).cookie(hostCookie))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("지우면 줄은 남고 만료가 된다. 두 번 지워도 204 다")
    void deleteExpires() throws Exception
    {
        Cookie cookie = login(newNickname());
        Long postId = createLolPost(cookie);

        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/posts/" + postId).cookie(cookie)).andExpect(status().isNoContent());

        assertThat(statusOf(postId)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("select expired_at is not null from recruit_posts where id = ?",
                Boolean.class, postId)).isTrue();
        mockMvc.perform(get("/api/v1/posts/" + postId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.host.host").value(true));
    }

    @Test
    @DisplayName("글은 로그인해야 하고, 상태를 바꾸는 요청은 Origin 검사를 거친다")
    void requiresLoginAndOrigin() throws Exception
    {
        Cookie cookie = login(newNickname());

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
