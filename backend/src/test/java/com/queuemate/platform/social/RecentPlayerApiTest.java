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
 * 최근 함께한 사람 — <b>읽는 쪽만 있다.</b> 채우는 코드가 아직 없어서({@code PartyClosed.fifo} — SQS 배선이 미정이다) 줄은 SQL 로 직접 넣는다.
 *
 * <p>줄의 사람도 파티도 <b>번호</b>(bigint)다 — 응답의 {@code userId} · {@code lastPartyId} 는 JSON 숫자라
 * {@code jsonPath(…, equalTo(번호), Long.class)} 로 본다.
 */
class RecentPlayerApiTest extends ApiTestSupport {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("아무도 채우지 않으므로 지금은 빈 목록이다. 로그인해야 한다")
    void emptyForNow() throws Exception
    {
        Cookie myCookie = signupAndLogin(newLoginId());

        mockMvc.perform(get("/api/v1/recent-players").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players").isArray())
                .andExpect(jsonPath("$.players").isEmpty());
        mockMvc.perform(get("/api/v1/recent-players"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("최근순이고 내 줄만 나온다 — 닉네임 · 마지막 파티 · 마지막 시각이 붙는다. 없어진 사용자의 줄은 빠진다")
    void recentFirstAndMineOnly() throws Exception
    {
        String me = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        Long myId = userIdOf(me);
        String olderLogin = newLoginId();
        String newerLogin = newLoginId();
        Long older = insertUser(olderLogin);
        Long newer = insertUser(newerLogin);
        Long someoneElse = insertUser();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        long newerParty = 900_001L;
        insertRecent(myId, older, 900_000L, base.minusSeconds(3600));
        insertRecent(myId, newer, newerParty, base.minusSeconds(60));
        // 남의 목록의 줄 — 나를 만난 사람의 줄이지 내 줄이 아니다
        insertRecent(someoneElse, myId, 900_002L, base);
        insertRecent(someoneElse, older, 900_003L, base);
        // 가입한 적 없는(없어진) 사용자 — 닉네임을 붙일 수 없어 뺀다
        insertRecent(myId, unknownUserId(), 900_004L, base);

        mockMvc.perform(get("/api/v1/recent-players").cookie(myCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2))
                .andExpect(jsonPath("$.players[0].userId", equalTo(newer), Long.class))
                .andExpect(jsonPath("$.players[0].nickname").value(nicknameOf(newerLogin)))
                .andExpect(jsonPath("$.players[0].lastPartyId", equalTo(newerParty), Long.class))
                .andExpect(jsonPath("$.players[0].lastPlayedAt").value(base.minusSeconds(60).toString()))
                .andExpect(jsonPath("$.players[1].userId", equalTo(older), Long.class));
    }

    @Test
    @DisplayName("50명까지다. 차단 관계(어느 방향이든)인 사람은 빠지고, 빠진 자리는 그다음 사람이 채운다")
    void limitAndBlocks() throws Exception
    {
        String me = newLoginId();
        Cookie myCookie = signupAndLogin(me);
        Long myId = userIdOf(me);
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        // others.get(0) 이 가장 최근이다
        List<Long> others = new ArrayList<>();
        for(int i = 0; i < 55; i++)
        {
            Long other = insertUser();
            others.add(other);
            insertRecent(myId, other, 910_000L + i, base.minusSeconds(i));
        }

        List<Long> all = playerIds(myCookie);
        assertThat(all).containsExactlyElementsOf(others.subList(0, 50));

        // 가장 최근의 한 명은 내가 차단했고, 그다음 한 명은 나를 차단했다
        jdbcTemplate.update("insert into social.blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", myId, others.get(0));
        jdbcTemplate.update("insert into social.blocks (blocker_id, blocked_id, created_at) values (?, ?, now())", others.get(1), myId);

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

    /** 가입 API 를 거치지 않고 사용자를 넣는다 — 55명을 가입시키면 비밀번호 해시 때문에 느리다. 끝나면 {@link ApiTestSupport} 가 지운다 */
    private Long insertUser()
    {
        return insertUser(newLoginId());
    }

    /** 사용자 번호는 identity 라 DB 가 매긴다 — 넣고 그 번호를 받아 온다 */
    private Long insertUser(String loginId)
    {
        return jdbcTemplate.queryForObject("insert into account.users (login_id, nickname, created_at, updated_at) "
                + "values (?, ?, now(), now()) returning id", Long.class, loginId, nicknameOf(loginId));
    }

    private void insertRecent(Long userId, Long otherUserId, long partyId, Instant playedAt)
    {
        jdbcTemplate.update("insert into social.recent_players (user_id, other_user_id, last_party_id, last_played_at) values (?, ?, ?, ?)",
                userId, otherUserId, partyId, Timestamp.from(playedAt));
    }
}
