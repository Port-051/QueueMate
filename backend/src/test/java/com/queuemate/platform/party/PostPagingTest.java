package com.queuemate.platform.party;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시판 목록의 <b>페이지 나누기(커서 방식)</b> — 2026-09-23 소유자 결정({@code contracts/platform-api.md} "모집 글 · 목록 · 입장권").
 *
 * <p>보는 것 — {@code limit} 의 기본값 · 상한, {@code cursor} 로 다음 페이지를 받는 것, <b>1페이지를 본 뒤 새 글이 올라와도 2페이지에 중복 · 누락이 없는 것</b>
 * (커서의 핵심이다 — {@code offset} 이면 깨진다), <b>차단으로 숨겨진 글 때문에 모자라면 그 뒤를 더 읽어 채우는 것</b>과 그 상한,
 * 정렬이 그대로인 것, SQL 문장 수가 페이지 크기에 비례해 늘지 않는 것.
 *
 * <p><b>글은 SQL 로 직접 넣는다</b>({@link #insertRecruitPost}) — "모집 중인 글은 한 사람에 하나"라 글마다 방장이 달라야 하고 20~25명을 가입시키면 느리다.
 * 그 방장들은 가입하지 않은 사용자 번호라 카드가 {@code null} 로 나가지만 페이지 나누기는 그것과 무관하다.
 */
class PostPagingTest extends PostTestSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    @DisplayName("limit 이 없으면 20개다. nextCursor 로 나머지가 오고 그 다음은 null 이다 — 두 페이지에 같은 글이 없고 빠진 글도 없다")
    void defaultLimitThenCursor() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        List<Long> all = insertPosts(25, "LOL", "paging");

        JsonNode first = listPage(viewer, "LOL", null, null);
        assertThat(longs(first.get("posts"), "postId")).hasSize(20).containsExactlyElementsOf(all.subList(0, 20));
        String cursor = first.get("nextCursor").asString();
        assertThat(cursor).isNotBlank();

        JsonNode second = listPage(viewer, "LOL", null, cursor);
        assertThat(longs(second.get("posts"), "postId")).containsExactlyElementsOf(all.subList(20, 25));
        assertThat(second.get("nextCursor").isNull()).isTrue();

        // 두 페이지를 합치면 25개가 한 번씩이다
        List<Long> walked = walk(viewer, "LOL", null);
        assertThat(walked).containsExactlyElementsOf(all).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("1페이지를 받은 뒤 새 글이 올라와도 2페이지에 중복 · 누락이 없다 — offset 이면 깨지는 자리다")
    void cursorIsNotOffset() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        // 뒤에 올라올 글이 확실히 더 새것이도록 시각을 벌려서 넣는다
        List<Long> before = insertPosts(15, "LOL", "before", Instant.now().minusSeconds(60));

        JsonNode first = listPage(viewer, "LOL", 5, null);
        List<Long> page1 = longs(first.get("posts"), "postId");
        assertThat(page1).containsExactlyElementsOf(before.subList(0, 5));

        // 1페이지를 보는 동안 새 글 다섯이 맨 위에 올라왔다 — 커서는 값을 기준으로 자르므로 뒤의 페이지가 밀리지 않는다
        List<Long> added = insertPosts(5, "LOL", "after", Instant.now());

        List<Long> rest = new ArrayList<>();
        String cursor = first.get("nextCursor").asString();
        while(cursor != null)
        {
            JsonNode page = listPage(viewer, "LOL", 5, cursor);
            rest.addAll(longs(page.get("posts"), "postId"));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        }
        assertThat(rest).containsExactlyElementsOf(before.subList(5, 15))
                .doesNotHaveDuplicates()
                .doesNotContainAnyElementsOf(page1)
                .doesNotContainAnyElementsOf(added);
        // 새 글은 맨 위부터 다시 받을 때 보인다 — 게시판 신호를 받은 프런트가 하는 일이다
        assertThat(longs(listPage(viewer, "LOL", 5, null).get("posts"), "postId")).containsExactlyElementsOf(added);
    }

    @Test
    @DisplayName("차단으로 숨겨진 글이 섞여 있어도 limit 만큼 채워 준다 — nextCursor 는 '마지막으로 읽은 줄'이라 숨겨진 글을 다시 읽지 않는다")
    void refillsToTheLimit() throws Exception
    {
        String viewerId = newLoginId();
        Cookie viewer = signupAndLogin(viewerId);
        List<Long> posts = insertPosts(12, "LOL", "refill");
        // 맨 위 둘과 가운데 둘이 숨겨진 글이다 — 한 페이지(limit 4)를 채우려면 그 뒤를 더 읽어야 한다
        List<Long> hidden = List.of(posts.get(0), posts.get(1), posts.get(5), posts.get(6));
        for(Long postId : hidden)
        {
            blockHostOf(userIdOf(viewerId), postId);
        }
        List<Long> visible = new ArrayList<>(posts);
        visible.removeAll(hidden);
        assertThat(visible).hasSize(8);

        JsonNode page = listPage(viewer, "LOL", 4, null);
        // 숨겨진 글을 건너뛰고 보이는 글 넷을 채워 준다
        assertThat(longs(page.get("posts"), "postId")).containsExactlyElementsOf(visible.subList(0, 4));
        // nextCursor 가 '마지막으로 읽은 줄'이라 다음 페이지가 숨겨진 글을 다시 읽지 않는다 — 나머지가 한 번씩 온다
        JsonNode next = listPage(viewer, "LOL", 4, page.get("nextCursor").asString());
        assertThat(longs(next.get("posts"), "postId")).containsExactlyElementsOf(visible.subList(4, 8));

        // 페이지를 잘게 나눠 끝까지 걸어도 보이는 글이 한 번씩만 온다
        assertThat(walk(viewer, "LOL", 1)).containsExactlyElementsOf(visible).doesNotHaveDuplicates();
        assertThat(walk(viewer, "LOL", 2)).containsExactlyElementsOf(visible);
    }

    @Test
    @DisplayName("채우기의 상한(max-refills=3)을 다 써도 모자라면 있는 만큼(빈 목록도) 준다 — nextCursor 로 이어 받으면 나머지가 온다")
    void refillStopsAtTheLimitButTheCursorGoesOn() throws Exception
    {
        String viewerId = newLoginId();
        Cookie viewer = signupAndLogin(viewerId);
        List<Long> posts = insertPosts(22, "LOL", "wall");
        // 앞의 스물이 전부 숨겨진 글이다 — limit 4 라면 한 번의 조회로는(첫 읽기 + 채우기 3번) 벽을 넘지 못한다
        for(int i = 0; i < 20; i++)
        {
            blockHostOf(userIdOf(viewerId), posts.get(i));
        }
        List<Long> visible = posts.subList(20, 22);

        JsonNode first = listPage(viewer, "LOL", 4, null);
        assertThat(longs(first.get("posts"), "postId")).isEmpty();
        // 벽에 부딪혀도 "여기까지 읽었다"를 알려 준다 — 같은 곳을 다시 읽지 않는다
        assertThat(first.get("nextCursor").isNull()).isFalse();

        JsonNode second = listPage(viewer, "LOL", 4, first.get("nextCursor").asString());
        assertThat(longs(second.get("posts"), "postId")).containsExactlyElementsOf(visible);
        assertThat(second.get("nextCursor").isNull()).isTrue();
        assertThat(walk(viewer, "LOL", 4)).containsExactlyElementsOf(visible);
    }

    @Test
    @DisplayName("limit 의 경계 — 1 · 100 은 되고 0 · 101 · 숫자가 아닌 값은 400 VALIDATION_FAILED(details 에 limit)")
    void limitBounds() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        List<Long> all = insertPosts(3, "LOL", "bounds");

        JsonNode one = listPage(viewer, "LOL", 1, null);
        assertThat(longs(one.get("posts"), "postId")).containsExactly(all.get(0));
        assertThat(one.get("nextCursor").isNull()).isFalse();

        JsonNode hundred = listPage(viewer, "LOL", 100, null);
        assertThat(longs(hundred.get("posts"), "postId")).containsExactlyElementsOf(all);
        assertThat(hundred.get("nextCursor").isNull()).isTrue();

        for(String bad : List.of("0", "101", "-1", "abc"))
        {
            mockMvc.perform(get("/api/v1/posts").param("game", "LOL").param("limit", bad).cookie(viewer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(detailFor("limit"));
        }
    }

    @Test
    @DisplayName("읽을 수 없는 커서는 400 VALIDATION_FAILED(details 에 cursor)다 — 500 이 아니다. 빈 값은 맨 위부터다")
    void brokenCursorIsRejected() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        List<Long> all = insertPosts(2, "LOL", "cursor");

        List<String> broken = new ArrayList<>(List.of(
                "not base64 !!",
                encode("0|1|"),            // 칸이 모자라다
                encode("0|1|2|3"),         // 칸이 남는다
                encode("7|1790000000000000|5"), // 있을 수 없는 statusOrder
                encode("0|1790000000000000|0"),  // 있을 수 없는 글 번호
                encode("0|not-a-number|5")));
        for(String bad : broken)
        {
            mockMvc.perform(get("/api/v1/posts").param("game", "LOL").param("cursor", bad).cookie(viewer))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(detailFor("cursor"));
        }
        // 값이 없는 cursor 는 안 준 것과 같다
        assertThat(longs(listPage(viewer, "LOL", null, "").get("posts"), "postId")).containsExactlyElementsOf(all);
    }

    @Test
    @DisplayName("정렬은 그대로다 — 모집 중인 글이 먼저, 그 안에서는 새 글이 먼저. 페이지 경계를 넘어도 같고 game 필터와 같이 쓴다")
    void orderAcrossPagesWithGameFilter() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        List<Long> recruiting = insertPosts(4, "LOL", "recruiting");
        List<Long> closed = insertPosts(3, "LOL", "closed");
        List<Long> other = insertPosts(2, "VALORANT", "valorant");
        // 만료된 글 — 그렇게 된 지 10분이 안 됐으니 목록에 남지만 모집 중인 글 뒤다
        for(Long postId : closed)
        {
            jdbcTemplate.update("update party.recruit_posts set status = 'EXPIRED', expired_at = now() where id = ?", postId);
        }

        List<Long> expected = new ArrayList<>(recruiting);
        expected.addAll(closed);
        assertThat(walk(viewer, "LOL", 2)).containsExactlyElementsOf(expected).doesNotContainAnyElementsOf(other);
        assertThat(walk(viewer, "LOL", 3)).containsExactlyElementsOf(expected);
        assertThat(walk(viewer, "VALORANT", 1)).containsExactlyElementsOf(other);
        // game 을 주지 않으면 세 게임 전부다 — 다섯 게임이 섞여도 페이지가 이어진다
        assertThat(walk(viewer, null, 3)).containsAll(expected).containsAll(other);
    }

    @Test
    @DisplayName("목록의 SQL 문장 수는 페이지 크기에 비례해 늘지 않는다 — 글 · 찾는 포지션 · 프로필 · 차단으로 같다")
    void statementCountDoesNotGrowWithPageSize() throws Exception
    {
        Cookie viewer = signupAndLogin(newLoginId());
        insertPosts(25, "LOL", "statements");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try
        {
            statistics.clear();
            assertThat(longs(listPage(viewer, "LOL", 5, null).get("posts"), "postId")).hasSize(5);
            long small = statistics.getPrepareStatementCount();

            statistics.clear();
            assertThat(longs(listPage(viewer, "LOL", 25, null).get("posts"), "postId")).hasSize(25);
            long large = statistics.getPrepareStatementCount();

            // 글 1 + 찾는 포지션 1 + LOL 프로필 1 + 차단 1
            assertThat(small).isLessThanOrEqualTo(5);
            assertThat(large).isEqualTo(small);
        }
        finally
        {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ---- 도우미 ----

    /**
     * 글 {@code count} 개를 <b>새 글부터</b> 넣는다 — {@code created_at} 을 1초씩 벌려서 정렬이 흔들리지 않게 한다.
     * 돌려주는 순서가 곧 목록에 보일 순서다(새 글이 먼저). 방장은 글마다 다른, 가입하지 않은 사용자 번호다.
     */
    private List<Long> insertPosts(int count, String game, String title)
    {
        return insertPosts(count, game, title, Instant.now());
    }

    private List<Long> insertPosts(int count, String game, String title, Instant newest)
    {
        List<Long> ids = new ArrayList<>();
        for(int i = 0; i < count; i++)
        {
            ids.add(insertRecruitPost(unknownUserId(), game, title + "-" + i, newest.minusSeconds(i)));
        }
        return ids;
    }

    /** 그 글의 방장을 차단한다 — 방이 없는 글이라 목록에서 숨겨지는 기준은 방장과의 사이다 */
    private void blockHostOf(Long blockerId, Long postId)
    {
        Long hostId = jdbcTemplate.queryForObject("select host_id from party.recruit_posts where id = ?", Long.class, postId);
        jdbcTemplate.update("insert into social.blocks (blocker_id, blocked_id, created_at) values (?, ?, now())",
                blockerId, hostId);
    }

    /** 커서를 따라 끝까지 걸어 본 글의 번호. 페이지가 끝나지 않으면 실패한다 */
    private List<Long> walk(Cookie cookie, String game, Integer limit) throws Exception
    {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for(int page = 0; page < 100; page++)
        {
            JsonNode body = listPage(cookie, game, limit, cursor);
            ids.addAll(longs(body.get("posts"), "postId"));
            if(body.get("nextCursor").isNull())
            {
                return ids;
            }
            cursor = body.get("nextCursor").asString();
        }
        throw new AssertionError("페이지가 끝나지 않는다 — nextCursor 가 제자리를 돈다");
    }

    private static String encode(String plain)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }
}
