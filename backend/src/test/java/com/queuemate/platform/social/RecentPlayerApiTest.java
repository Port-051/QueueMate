package com.queuemate.platform.social;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 최근 함께한 사람의 <b>읽기</b>. 줄은 SQL 로 직접 넣는다 — 채우는 쪽(게시판 파티가 닫힐 때 — 2026-09-26)은 {@code party.PartyCloseTest} 가 본다.
 *
 * <p>줄의 사람도 파티도 <b>번호</b>(bigint)다 — 응답의 {@code userId} · {@code lastPartyId} 는 JSON 숫자라
 * {@code jsonPath(…, equalTo(번호), Long.class)} 로 본다. 두 칸 다 FK 가 있어(사람은 {@code users}, 파티는 {@code parties} — 2026-09-26)
 * 진짜 사용자와 진짜 파티를 먼저 넣는다({@link #insertParty()}).
 */
class RecentPlayerApiTest extends ApiTestSupport {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("같이 한 파티가 없으면 빈 목록이다. 로그인해야 한다")
    void emptyForNow() throws Exception
    {
        Cookie myCookie = login(newNickname());

        mockMvc.perform(get("/api/v1/recent-players").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players").isArray())
                .andExpect(jsonPath("$.players").isEmpty());
        mockMvc.perform(get("/api/v1/recent-players"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("최근순이고 내 줄만 나온다 — 닉네임 · 마지막 파티 · 마지막 시각이 붙는다. 없어진 사용자의 줄은 딸려 지워진다")
    void recentFirstAndMineOnly() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        String olderNickname = newNickname();
        String newerNickname = newNickname();
        Long older = insertUser(olderNickname);
        Long newer = insertUser(newerNickname);
        Long someoneElse = insertUser();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Long olderParty = insertParty();
        Long newerParty = insertParty();
        insertRecent(myId, older, olderParty, base.minusSeconds(3600));
        insertRecent(myId, newer, newerParty, base.minusSeconds(60));
        // 남의 목록의 줄 — 나를 만난 사람의 줄이지 내 줄이 아니다
        insertRecent(someoneElse, myId, newerParty, base);
        insertRecent(someoneElse, older, olderParty, base);
        // 없어진 사용자 — 그 사람을 지우면 내 목록의 줄도 FK 의 ON DELETE CASCADE 로 딸려 지워진다
        Long gone = insertUser();
        insertRecent(myId, gone, newerParty, base);
        jdbcTemplate.update("delete from users where id = ?", gone);

        mockMvc.perform(get("/api/v1/recent-players").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2))
                .andExpect(jsonPath("$.players[0].userId", equalTo(newer), Long.class))
                .andExpect(jsonPath("$.players[0].nickname").value(newerNickname))
                .andExpect(jsonPath("$.players[0].lastPartyId", equalTo(newerParty), Long.class))
                .andExpect(jsonPath("$.players[0].lastPlayedAt").value(base.minusSeconds(60).toString()))
                .andExpect(jsonPath("$.players[1].userId", equalTo(older), Long.class));
    }

    @Test
    @DisplayName("50명까지다. 차단 관계(어느 방향이든)인 사람은 빠지고, 빠진 자리는 그다음 사람이 채운다")
    void limitAndBlocks() throws Exception
    {
        String me = newNickname();
        Cookie myCookie = login(me);
        Long myId = userIdOf(me);
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // others.get(0) 이 가장 최근이다. 마지막 파티는 모두 같은 파티다 — 순서와 무관하다
        Long party = insertParty();
        List<Long> others = new ArrayList<>();
        for(int i = 0; i < 55; i++)
        {
            Long other = insertUser();
            others.add(other);
            insertRecent(myId, other, party, base.minusSeconds(i));
        }

        List<Long> all = playerIds(myCookie);
        assertThat(all).containsExactlyElementsOf(others.subList(0, 50));

        // 가장 최근의 한 명은 내가 차단했고, 그다음 한 명은 나를 차단했다
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", myId, others.get(0));
        jdbcTemplate.update("insert into blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", others.get(1), myId);

        List<Long> filtered = playerIds(myCookie);
        assertThat(filtered).hasSize(50).doesNotContain(others.get(0), others.get(1));
        assertThat(filtered).containsExactlyElementsOf(others.subList(2, 52));
    }

    private List<Long> playerIds(Cookie cookie) throws Exception
    {
        JsonNode players = objectMapper.readTree(mockMvc.perform(get("/api/v1/recent-players").cookie(cookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("players");
        List<Long> userIds = new ArrayList<>();
        players.forEach(player -> userIds.add(player.get("userId").asLong()));
        return userIds;
    }

    /**
     * 확정된 게시판 파티 하나 — 방장은 새로 넣은 사용자이고 그 사람의 글에 딸린다. 끝나면 그 방장을 지울 때 글 · 파티가 딸려 지워지고,
     * 이 파티를 가리키던 줄의 {@code last_party_id} 는 {@code NULL} 이 된다
     */
    private Long insertParty()
    {
        Long postId = jdbcTemplate.queryForObject("insert into recruit_posts "
                + "(host_id, game, mode, title, voice, status, created_at, updated_at, confirmed_at) "
                + "values (?, 'LOL', 'RANKED_SOLO', 't', 'REQUIRED', 'CONFIRMED', now(), now(), now()) returning id",
                Long.class, insertUser());
        return jdbcTemplate.queryForObject("insert into parties (source, post_id, game, status, created_at) "
                + "values ('BOARD', ?, 'LOL', 'ACTIVE', now()) returning id", Long.class, postId);
    }

    private void insertRecent(Long userId, Long otherUserId, Long partyId, Instant playedAt)
    {
        jdbcTemplate.update("insert into recent_players (user_id, other_user_id, last_party_id, last_played_at) values (?, ?, ?, ?)",
                userId, otherUserId, partyId, Timestamp.from(playedAt));
    }
}
