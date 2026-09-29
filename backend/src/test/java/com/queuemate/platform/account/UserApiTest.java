package com.queuemate.platform.account;

import com.queuemate.platform.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 프로필과 게임 계정 — {@code contracts/platform-api.md} "계정" 의 {@code /users/me} 아래 네 요청.
 *
 * <p>"나"는 경로가 아니라 토큰에서 온다 — 경로에 사용자 번호를 받지 않는다. 응답의 {@code userId} 는 DB 가 매긴 번호라
 * {@code value(equalTo(…), Long.class)} 로 본다.
 */
class UserApiTest extends ApiTestSupport {

    @Test
    @DisplayName("닉네임을 바꾸면 GET 과 같은 모양이 온다. 남이 쓰는 닉네임이면 409 NICKNAME_TAKEN 이고 바뀌지 않는다")
    void changeNickname() throws Exception
    {
        String me = newNickname();
        String other = newNickname();
        Cookie cookie = login(me);
        insertUser(other);
        String renamed = "r" + me.substring(1);

        patchNickname(cookie, renamed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userIdOf(me)), Long.class))
                .andExpect(jsonPath("$.nickname").value(renamed))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.gameAccounts").isArray());

        patchNickname(cookie, other)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));
        // 지금 내 닉네임으로 다시 바꾸는 것은 충돌이 아니다
        patchNickname(cookie, renamed).andExpect(status().isOk());
        patchNickname(cookie, " x ").andExpect(status().isBadRequest())
                .andExpect(detailFor("nickname"));

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.nickname").value(renamed));
    }

    @Test
    @DisplayName("게임 계정 PUT 은 없으면 만들고 있으면 바꾼다 — 두 번 불러도 한 줄이다 (자기신고 — VALORANT · PUBG)")
    void putGameAccountTwice() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);

        putGameAccount(cookie, "VALORANT", json("gameNickname", "val#KR1", "tier", "DIAMOND_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("VALORANT"))
                .andExpect(jsonPath("$.gameNickname").value("val#KR1"))
                .andExpect(jsonPath("$.tier").value("DIAMOND_2"));

        // 바꾸기 — 티어를 비운다(null 이 그대로 들어가는지도 같이 본다)
        putGameAccount(cookie, "VALORANT", json("gameNickname", "jett#KR2", "tier", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameNickname").value("jett#KR2"))
                .andExpect(jsonPath("$.tier").isEmpty());

        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "tier", null))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from game_accounts where user_id = ? and game = 'VALORANT'",
                Integer.class, userIdOf(nickname))).isEqualTo(1);
        // 게임 이름순으로 온다
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameAccounts.length()").value(2))
                .andExpect(jsonPath("$.gameAccounts[0].game").value("PUBG"))
                .andExpect(jsonPath("$.gameAccounts[1].game").value("VALORANT"))
                .andExpect(jsonPath("$.gameAccounts[1].gameNickname").value("jett#KR2"))
                .andExpect(jsonPath("$.gameAccounts[1].tier").isEmpty());
    }

    @Test
    @DisplayName("LOL 의 티어는 Riot 에서 채운다 — tier · server 를 보내면 400 이고, 이름#태그가 아니면 400 이다. Riot 을 부르기 전에 거른다")
    void lolRejectsSelfReportedFields() throws Exception
    {
        Cookie cookie = login(newNickname());

        putGameAccount(cookie, "LOL", json("gameNickname", "Faker#KR1", "tier", "GOLD_1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("tier"));
        putGameAccount(cookie, "LOL", json("gameNickname", "Faker#KR1", "server", "STEAM"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("server"));
        // 형식 — 태그가 없다 · 한쪽이 비었다
        for(String bad : new String[]{"Hide on bush", "Faker#", "#KR1"})
        {
            putGameAccount(cookie, "LOL", json("gameNickname", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(detailFor("gameNickname"));
        }

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("그 게임의 티어가 아니면 400 이다 (자기신고 — VALORANT · PUBG)")
    void invalidGameAccount() throws Exception
    {
        Cookie cookie = login(newNickname());

        // 소문자 티어 · 빈 게임 닉네임 · 모르는 게임
        putGameAccount(cookie, "VALORANT", json("gameNickname", "x", "tier", "gold"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("tier"));
        // 사다리에 없는 이름 · 다른 게임의 티어(2026-09-24 — 값의 목록은 gameconfig 가 원본이다)
        putGameAccount(cookie, "VALORANT", json("gameNickname", "x", "tier", UNKNOWN_TIER))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("tier"));
        putGameAccount(cookie, "PUBG", json("gameNickname", "x", "tier", "ASCENDANT_1"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("tier"));
        putGameAccount(cookie, "VALORANT", json("gameNickname", " ", "tier", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("gameNickname"));
        putGameAccount(cookie, "OVERWATCH", json("gameNickname", "x", "tier", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("game"));
        // 경로의 게임은 대문자 enum 이다 — 소문자도 같은 400 (게시판 목록의 game 과 같다)
        putGameAccount(cookie, "lol", json("gameNickname", "x", "tier", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("game"));

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("주 포지션은 어느 게임도 받지 않는다 — mainPosition 에 값이 있으면 400 이고 아무것도 바꾸지 않는다. null 은 안 보낸 것과 같다 (2026-09-29 — P-35)")
    void mainPositionIsRejected() throws Exception
    {
        Cookie cookie = login(newNickname());
        putGameAccount(cookie, "VALORANT", json("gameNickname", "before#KR1", "tier", "DIAMOND_2"))
                .andExpect(status().isOk());

        // 그 게임의 옳은 포지션 이름이어도 거절한다 — 조용히 버리면 옛 클라이언트가 저장됐다고 착각한다
        for(String game : new String[]{"LOL", "VALORANT", "PUBG"})
        {
            putGameAccount(cookie, game, json("gameNickname", "after#KR1", "mainPosition", "DUELIST"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(detailFor("mainPosition"));
        }
        // 문자열이 아닌 값 · 빈 문자열도 같은 한 줄이다
        putGameAccount(cookie, "VALORANT", "{\"gameNickname\":\"after#KR1\",\"mainPosition\":3}")
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("mainPosition"));
        putGameAccount(cookie, "VALORANT", "{\"gameNickname\":\"after#KR1\",\"mainPosition\":[\"SENTINEL\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("mainPosition"));
        putGameAccount(cookie, "VALORANT", json("gameNickname", "after#KR1", "mainPosition", ""))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("mainPosition"));

        // 거절된 요청은 아무것도 바꾸지 않았다 — VALORANT 한 줄이 그대로다
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts.length()").value(1))
                .andExpect(jsonPath("$.gameAccounts[0].gameNickname").value("before#KR1"))
                .andExpect(jsonPath("$.gameAccounts[0].mainPosition").doesNotExist());

        // null 은 안 보낸 것과 같다 — 통과한다
        putGameAccount(cookie, "VALORANT", json("gameNickname", "after#KR1", "tier", null, "mainPosition", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameNickname").value("after#KR1"))
                .andExpect(jsonPath("$.mainPosition").doesNotExist());
    }

    @Test
    @DisplayName("게임 계정 DELETE 는 두 번 다 204 다 — 없어도 성공이다")
    void deleteGameAccountTwice() throws Exception
    {
        String nickname = newNickname();
        Cookie cookie = login(nickname);
        insertGameAccount(userIdOf(nickname), "LOL", "x#KR1", "GOLD_1");

        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL").cookie(cookie)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("남의 게임 계정은 건드려지지 않는다 — '나'는 토큰에서 온다")
    void gameAccountsAreScopedToTheCaller() throws Exception
    {
        String mine = newNickname();
        String theirs = newNickname();
        Cookie myCookie = login(mine);
        Cookie theirCookie = login(theirs);
        insertGameAccount(userIdOf(theirs), "LOL", "theirs", null);

        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL").cookie(myCookie)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(theirCookie))
                .andExpect(jsonPath("$.gameAccounts[0].gameNickname").value("theirs"));
    }

    private ResultActions patchNickname(Cookie cookie, String nickname) throws Exception
    {
        return mockMvc.perform(patch("/api/v1/users/me").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(json("nickname", nickname)));
    }

    private ResultActions putGameAccount(Cookie cookie, String game, String body) throws Exception
    {
        return mockMvc.perform(put("/api/v1/users/me/game-accounts/" + game).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
