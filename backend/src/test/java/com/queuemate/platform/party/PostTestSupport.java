package com.queuemate.platform.party;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모집 글 테스트의 공통 바탕 — 글을 쓰는 법과 <b>방의 상태를 손으로 만드는 법</b>.
 *
 * <p><b>글을 쓰면 방이 같이 생긴다</b>(2026-09-25 2단계 — {@code POST /api/v1/posts} 가 방 만들기 스크립트를 부른다). 방장이 혼자 들어 있는 방이다.
 * 그 밖의 상태(남이 들어와 있다 · 방이 사라졌다 · 확정 표시 키만 있다)는 <b>테스트가 Redis 에 직접 써서 만든다</b>
 * ({@code SET qm:room:{id}:host} · {@code SADD …:members} · {@code SET …:confirmed}) — 방의 스크립트가 쓰는 것과 같은 키 · 같은 자료형이다.
 * 입장 · 확정을 진짜로 거치는 테스트는 HTTP 로 부른다({@code PostRoomFlowTest}). 손으로 쓰는 쪽은 "스크립트가 만들 수 없는 모양"(가입하지 않은 번호 ·
 * 숫자가 아닌 값 · 커밋이 실패해 확정 표시 키만 남은 방)을 만들 때 쓴다.
 * 키 이름을 main 의 상수에서 가져오지 않고 <b>글자로 적었다</b> — 상수에 오타가 나면 이 테스트들이 깨져야 한다({@code room.redisKeys.RoomKeys} 가 원본이다).
 *
 * <p>끝나면 자기가 쓴 키만 지운다 — 방 키 셋과, 그 방에 들어 있던 사람들의 입장 표시 키. <b>{@code FLUSHDB} 금지</b> — 같은 Redis 를 다른 테스트 · 앱이 쓴다.
 */
abstract class PostTestSupport extends ApiTestSupport {

    /** 어느 글의 번호도 아닌 값 — identity 가 닿지 않을 만큼 크다. "없는 글" 을 부를 때 쓴다 */
    protected static final long NO_SUCH_POST = 9_999_999_999L;

    /** 이 테스트가 방 키를 쓴 방. 끝나면 그 방의 키 셋을 지운다 */
    private final List<Long> touchedRooms = new CopyOnWriteArrayList<>();

    /** 이 테스트에서 방에 들어갔던 사람 — 끝나면 입장 표시 키를 지운다(방이 먼저 사라지면 멤버 SET 으로는 알 수 없다) */
    private final List<Long> touchedUsers = new CopyOnWriteArrayList<>();

    @AfterEach
    void deleteRoomKeys()
    {
        for(Long roomId : touchedRooms)
        {
            Set<String> members = redisTemplate.opsForSet().members(membersKey(roomId));
            if(members != null)
            {
                members.forEach(member -> redisTemplate.delete("qm:user:active-room:" + member));
            }
            redisTemplate.delete(List.of(hostKey(roomId), membersKey(roomId), confirmedKey(roomId)));
        }
        touchedUsers.forEach(userId -> redisTemplate.delete("qm:user:active-room:" + userId));
        touchedRooms.clear();
        touchedUsers.clear();
    }

    /** 방의 키 · 그 방 사람들의 입장 표시 키를 끝나면 지우게 적어 둔다 — HTTP 로 방에 들어가거나 방을 만든 테스트가 부른다 */
    protected void track(Long roomId, Long... users)
    {
        touchedRooms.add(roomId);
        touchedUsers.addAll(List.of(users));
    }

    // ---- room 인 척 ----

    protected static String hostKey(Long roomId)
    {
        return "qm:room:" + roomId + ":host";
    }

    protected static String membersKey(Long roomId)
    {
        return "qm:room:" + roomId + ":members";
    }

    protected static String confirmedKey(Long roomId)
    {
        return "qm:room:" + roomId + ":confirmed";
    }

    /**
     * 방장 키를 (다시) 쓰고 멤버 SET 에 방장과 나머지를 넣는다 — 손으로 만드는 입장이다(입장 표시 키 · 차단 검사를 거치지 않는다). 수명은 방과 같은 600초다.
     * {@code others} 는 보통 사용자 번호({@code Long})지만, 누가 Redis 에 손으로 넣었을 아무 문자열도 그대로 넣을 수 있다
     */
    protected void openRoom(Long roomId, Long hostId, Object... others)
    {
        touchedRooms.add(roomId);
        redisTemplate.opsForValue().set(hostKey(roomId), String.valueOf(hostId), java.time.Duration.ofSeconds(600));
        redisTemplate.opsForSet().add(membersKey(roomId), String.valueOf(hostId));
        for(Object other : others)
        {
            redisTemplate.opsForSet().add(membersKey(roomId), String.valueOf(other));
        }
        redisTemplate.expire(membersKey(roomId), java.time.Duration.ofSeconds(600));
    }

    /**
     * 확정 표시 키만 쓴다. 값은 {@code roomId} 다 — <b>확정 스크립트는 성공했는데 글의 기록이 커밋되지 않은 상태</b>를 만든다(자가 치유를 보는 테스트).
     * 진짜 확정은 {@code POST /api/v1/rooms/{roomId}/confirm} 이다
     */
    protected void confirmRoom(Long roomId)
    {
        touchedRooms.add(roomId);
        redisTemplate.opsForValue().set(confirmedKey(roomId), String.valueOf(roomId), java.time.Duration.ofSeconds(600));
    }

    /**
     * 방이 사라졌다 — 방장 키 · 멤버 SET · 확정 표시 키와 <b>그 방 사람들의 입장 표시 키</b>가 함께 사라진다(방장이 나가면 나가기 스크립트가 그렇게 한다).
     * 입장 표시 키까지 지우는 것은 그 사람들이 다음 글을 쓸 수 있게 하려는 것이다 — 남아 있으면 409 {@code IN_OTHER_ROOM} 이다
     */
    protected void closeRoom(Long roomId)
    {
        Set<String> members = redisTemplate.opsForSet().members(membersKey(roomId));
        if(members != null)
        {
            members.forEach(member -> redisTemplate.delete("qm:user:active-room:" + member));
        }
        redisTemplate.delete(List.of(hostKey(roomId), membersKey(roomId), confirmedKey(roomId)));
    }

    /** 지금 Redis 에 있는 {@code qm:room:*} · {@code qm:user:*} · {@code qm:party:*} 키 — 읽기만 하는 요청이 키를 만들거나 지우지 않았는지 볼 때 쓴다 */
    protected Set<String> foreignKeys()
    {
        Set<String> keys = new TreeSet<>();
        keys.addAll(redisTemplate.keys("qm:room:*"));
        keys.addAll(redisTemplate.keys("qm:user:*"));
        keys.addAll(redisTemplate.keys("qm:party:*"));
        return keys;
    }

    // ---- 글 ----

    /** 글을 쓴다. 201 이면 <b>같이 생긴 방</b>과 방장의 입장 표시 키를 끝나면 지우게 적어 둔다({@link #track}) */
    protected ResultActions createPost(Cookie cookie, String body) throws Exception
    {
        ResultActions created = mockMvc.perform(post("/api/v1/posts").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
        if(created.andReturn().getResponse().getStatus() == 201)
        {
            JsonNode json = body(created);
            track(json.get("postId").asLong(), json.get("hostId").asLong());
        }
        return created;
    }

    protected ResultActions editPost(Cookie cookie, long postId, String body) throws Exception
    {
        return mockMvc.perform(patch("/api/v1/posts/" + postId).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** LOL 글을 하나 쓰고 그 id 를 돌려준다 */
    protected Long createLolPost(Cookie cookie, String... wantedPositions) throws Exception
    {
        return createdId(createPost(cookie, lolPostBody("같이 하실 분", wantedPositions)).andExpect(status().isCreated()));
    }

    protected static String lolPostBody(String title, String... wantedPositions)
    {
        return postBody("LOL", title, "{}", wantedPositions);
    }

    protected static String postBody(String game, String title, String conditionsJson, String... wantedPositions)
    {
        List<String> quoted = new ArrayList<>();
        for(String position : wantedPositions)
        {
            quoted.add('"' + position + '"');
        }
        return "{\"game\":\"" + game + "\",\"mode\":\"" + modeOf(game) + "\",\"title\":\"" + title + "\",\"description\":\"즐겁게\","
                + "\"voice\":\"REQUIRED\",\"conditions\":" + conditionsJson
                + ",\"wantedPositions\":[" + String.join(",", quoted) + "]}";
    }

    /**
     * 그 게임의 <b>gameconfig 에 있는</b> 모드(2026-09-24 — 없는 모드는 400 이다). 모르는 게임({@code OVERWATCH} 등)에는 LoL 의 것을 붙인다 —
     * 그런 본문은 {@code game} 검증이 먼저 거절하므로 모드가 무엇이든 결과가 같다.
     */
    protected static String modeOf(String game)
    {
        return switch(game)
        {
            case "VALORANT" -> VALORANT_MODE;
            case "PUBG" -> PUBG_MODE;
            default -> LOL_MODE;
        };
    }

    protected Long createdId(ResultActions created) throws Exception
    {
        return body(created).get("postId").asLong();
    }

    protected JsonNode body(ResultActions actions) throws Exception
    {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 기본 페이지({@code limit} 을 주지 않는다 — 20개)의 {@code posts} 만. 글이 몇 개 안 되는 테스트가 쓴다 */
    protected JsonNode list(Cookie cookie, String game) throws Exception
    {
        return body(mockMvc.perform(get("/api/v1/posts").param("game", game).cookie(cookie)).andExpect(status().isOk()))
                .get("posts");
    }

    /**
     * 목록 응답 <b>전체</b>({@code posts} · {@code nextCursor}) — 페이지 나누기를 보는 테스트가 쓴다.
     * {@code null} 인 파라미터는 보내지 않는다(그때의 기본값을 보려는 것이다).
     * {@code cursor} 는 <b>글 번호 그대로다</b>(2026-09-25 소유자 결정으로 base64url 한 겹이 없어졌다).
     */
    protected JsonNode listPage(Cookie cookie, String game, Integer limit, Long cursor) throws Exception
    {
        MockHttpServletRequestBuilder request = get("/api/v1/posts").cookie(cookie);
        if(game != null)
        {
            request.param("game", game);
        }
        if(limit != null)
        {
            request.param("limit", String.valueOf(limit));
        }
        if(cursor != null)
        {
            request.param("cursor", String.valueOf(cursor));
        }
        return body(mockMvc.perform(request).andExpect(status().isOk()));
    }

    /**
     * 모집 글을 <b>SQL 로 직접</b> 넣는다 — 여러 사람의 글이 여러 개 필요할 때다("모집 중인 글은 한 사람에 하나"라 방장이 저마다 달라야 하고,
     * 가입 · 로그인을 그만큼 되풀이하면 느리다). 방장은 보통 SQL 로 바로 넣은 사용자({@code insertUser()} — {@code host_id} 에 FK 가 있어 가입하지 않은 번호는 못 쓴다)라
     * 카드의 닉네임은 있고 프로필(게임 계정)은 {@code null} 이다.
     * 글 쓰기 경로(검증 · 신호 · 전적 긁기 · 방 만들기)를 보는 테스트는 이것을 쓰지 말고 {@link #createPost} 를 쓴다.
     *
     * <p><b>방도 손으로 연다</b>(방장 혼자 — {@link #openRoom}). 2026-09-25 2단계부터 글이 있으면 방이 있다 — 방이 없는 모집 중인 글은 "사라진 방"이라
     * 목록이 그 자리에서 만료로 옮겨 적는다. 그러면 옮겨 적기가 끼어 목록의 SQL 문장 수를 재는 테스트가 흔들린다
     */
    protected Long insertRecruitPost(Long hostId, String game, String title, java.time.Instant createdAt)
    {
        java.sql.Timestamp at = java.sql.Timestamp.from(createdAt);
        Long postId = jdbcTemplate.queryForObject("insert into recruit_posts "
                + "(host_id, game, mode, title, voice, conditions, status, created_at, updated_at) "
                + "values (?, ?, ?, ?, 'REQUIRED', '{}'::jsonb, 'RECRUITING', ?, ?) returning id",
                Long.class, hostId, game, modeOf(game), title, at, at);
        openRoom(postId, hostId);
        return postId;
    }

    /** 목록에서 그 글의 줄. 없으면 {@code null} 이다 — DB 가 테스트 사이에 남아 남의 글이 섞여 있으므로 늘 id 로 찾는다 */
    protected static JsonNode find(JsonNode posts, Long postId)
    {
        for(JsonNode one : posts)
        {
            if(one.get("postId").asLong() == postId)
            {
                return one;
            }
        }
        return null;
    }

    protected static List<String> texts(JsonNode array, String field)
    {
        List<String> values = new ArrayList<>();
        for(JsonNode one : array)
        {
            JsonNode value = (field == null) ? one : one.get(field);
            values.add(value == null || value.isNull() ? null : value.asString());
        }
        return values;
    }

    /** 배열의 각 원소(또는 그 원소의 {@code field})를 숫자로 — {@code postId} · {@code userId} 처럼 숫자인 칸에 쓴다 */
    protected static List<Long> longs(JsonNode array, String field)
    {
        List<Long> values = new ArrayList<>();
        for(JsonNode one : array)
        {
            JsonNode value = (field == null) ? one : one.get(field);
            values.add(value == null || value.isNull() ? null : value.asLong());
        }
        return values;
    }

    /** VALORANT · PUBG 용이다 — LoL 은 연결이 Riot 을 긁으므로(2026-09-27) {@link #insertGameAccount} 로 넣는다 */
    protected void putGameAccount(Cookie cookie, String game, String body) throws Exception
    {
        mockMvc.perform(put("/api/v1/users/me/game-accounts/" + game).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    protected void block(Cookie cookie, Long targetUserId) throws Exception
    {
        mockMvc.perform(post("/api/v1/blocks").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json("userId", targetUserId))).andExpect(status().isCreated());
    }

    protected String statusOf(Long postId)
    {
        return jdbcTemplate.queryForObject("select status from recruit_posts where id = ?", String.class, postId);
    }
}
