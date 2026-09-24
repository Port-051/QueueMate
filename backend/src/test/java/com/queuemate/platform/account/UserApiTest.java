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
        String me = newLoginId();
        String other = newLoginId();
        Cookie cookie = signupAndLogin(me);
        signup(other, PASSWORD, nicknameOf(other)).andExpect(status().isCreated());
        String renamed = "r" + me.substring(1);

        patchNickname(cookie, renamed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(equalTo(userIdOf(me)), Long.class))
                .andExpect(jsonPath("$.loginId").value(me))
                .andExpect(jsonPath("$.nickname").value(renamed))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.gameAccounts").isArray());

        patchNickname(cookie, nicknameOf(other))
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
    @DisplayName("게임 계정 PUT 은 없으면 만들고 있으면 바꾼다 — 두 번 불러도 한 줄이다")
    void putGameAccountTwice() throws Exception
    {
        String loginId = newLoginId();
        Cookie cookie = signupAndLogin(loginId);

        putGameAccount(cookie, "LOL", json("gameNickname", "Hide on bush", "tier", "GOLD_1", "mainPosition", "MID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game").value("LOL"))
                .andExpect(jsonPath("$.gameNickname").value("Hide on bush"))
                .andExpect(jsonPath("$.tier").value("GOLD_1"))
                .andExpect(jsonPath("$.mainPosition").value("MID"));

        // 바꾸기 — 티어와 포지션을 비운다(null 이 그대로 들어가는지도 같이 본다)
        putGameAccount(cookie, "LOL", json("gameNickname", "Faker", "tier", null, "mainPosition", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameNickname").value("Faker"))
                .andExpect(jsonPath("$.tier").isEmpty())
                .andExpect(jsonPath("$.mainPosition").isEmpty());

        putGameAccount(cookie, "VALORANT", json("gameNickname", "val#KR1", "tier", "DIAMOND_2", "mainPosition", "SENTINEL"))
                .andExpect(status().isOk());
        putGameAccount(cookie, "PUBG", json("gameNickname", "chicken", "tier", null, "mainPosition", null))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from account.game_accounts where user_id = ? and game = 'LOL'",
                Integer.class, userIdOf(loginId))).isEqualTo(1);
        // 게임 이름순으로 온다
        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameAccounts.length()").value(3))
                .andExpect(jsonPath("$.gameAccounts[0].game").value("LOL"))
                .andExpect(jsonPath("$.gameAccounts[0].gameNickname").value("Faker"))
                .andExpect(jsonPath("$.gameAccounts[0].tier").isEmpty())
                .andExpect(jsonPath("$.gameAccounts[1].game").value("PUBG"))
                .andExpect(jsonPath("$.gameAccounts[2].game").value("VALORANT"))
                .andExpect(jsonPath("$.gameAccounts[2].mainPosition").value("SENTINEL"));
    }

    @Test
    @DisplayName("그 게임의 포지션이 아니면 400 이다. PUBG 는 포지션을 받지 않는다")
    void invalidGameAccount() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());

        // VALORANT 의 역할을 LOL 에
        putGameAccount(cookie, "LOL", json("gameNickname", "x", "tier", "GOLD_1", "mainPosition", "DUELIST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(detailFor("mainPosition"));
        putGameAccount(cookie, "PUBG", json("gameNickname", "x", "tier", null, "mainPosition", "TOP"))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("mainPosition"));
        // 소문자 티어 · 빈 게임 닉네임 · 모르는 게임
        putGameAccount(cookie, "LOL", json("gameNickname", "x", "tier", "gold", "mainPosition", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("tier"));
        // 사다리에 없는 이름 · 다른 게임의 티어(2026-09-24 — 값의 목록은 gameconfig 가 원본이다)
        putGameAccount(cookie, "LOL", json("gameNickname", "x", "tier", UNKNOWN_TIER, "mainPosition", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("tier"));
        putGameAccount(cookie, "LOL", json("gameNickname", "x", "tier", "ASCENDANT_1", "mainPosition", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("tier"));
        putGameAccount(cookie, "LOL", json("gameNickname", " ", "tier", null, "mainPosition", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("gameNickname"));
        putGameAccount(cookie, "OVERWATCH", json("gameNickname", "x", "tier", null, "mainPosition", null))
                .andExpect(status().isBadRequest())
                .andExpect(detailFor("game"));

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("게임 계정 DELETE 는 두 번 다 204 다 — 없어도 성공이다")
    void deleteGameAccountTwice() throws Exception
    {
        Cookie cookie = signupAndLogin(newLoginId());
        putGameAccount(cookie, "LOL", json("gameNickname", "x", "tier", "GOLD_1", "mainPosition", "TOP"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL").cookie(cookie)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/users/me/game-accounts/LOL").cookie(cookie)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me").cookie(cookie))
                .andExpect(jsonPath("$.gameAccounts").isEmpty());
    }

    @Test
    @DisplayName("남의 게임 계정은 건드려지지 않는다 — '나'는 토큰에서 온다")
    void gameAccountsAreScopedToTheCaller() throws Exception
    {
        String mine = newLoginId();
        String theirs = newLoginId();
        Cookie myCookie = signupAndLogin(mine);
        Cookie theirCookie = signupAndLogin(theirs);
        putGameAccount(theirCookie, "LOL", json("gameNickname", "theirs", "tier", null, "mainPosition", null))
                .andExpect(status().isOk());

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
